package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.api.client.ui.UiPageSpec;
import xin.vanilla.banira.api.client.ui.UiPointer;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.sakura.data.collection.StringList;
import xin.vanilla.sakura.screen.StringInputScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RewardEditorUiParityTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }


    static final class Port implements RewardEditorUiSession.Port {
        int rule = 1, scans, commands, entries, closes, suspended;
        long revision;
        boolean redacted, sidebar = true;
        RewardEditorUiSession.Action lastCommand;
        RewardEditorUiSession.EntryAction lastEntry;
        final List<RewardEditorUiSession.Entry> rows = new ArrayList<>();

        Port(int size) {
            for (int i = 0; i < size; i++)
                rows.add(new RewardEditorUiSession.Entry("base," + i, "Reward " + i, "type 1%", "Full reward " + i, null, false, false, false, true));
        }

        public long revision() {
            return revision;
        }

        public void refresh() {
        }

        public List<RewardEditorUiSession.Entry> entries() {
            scans++;
            return rows;
        }

        public int rule() {
            return rule;
        }

        public void rule(int value) {
            rule = value;
            revision++;
        }

        public boolean redacted() {
            return redacted;
        }

        public boolean enabled(RewardEditorUiSession.Action action) {
            return !redacted || action == RewardEditorUiSession.Action.HELP;
        }

        public boolean sidebarOpen() {
            return sidebar;
        }

        public void command(RewardEditorUiSession.Action action) {
            commands++;
            lastCommand = action;
        }

        public void entry(String id, RewardEditorUiSession.EntryAction action) {
            assertTrue(id.startsWith("base,"));
            entries++;
            lastEntry = action;
        }

        public void suspend() {
            suspended++;
        }

        public void closed(UiCloseReason reason) {
            closes++;
        }

        public boolean key(int key, int scan, int modifiers, boolean pressed) {
            return true;
        }
    }

    @Test
    public void everyToolbarOperationReachesOriginalControllerOnlyOnExplicitAction() {
        Port port = new Port(3);
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals(0, port.commands);
        for (RewardEditorUiSession.Action action : RewardEditorUiSession.Action.values()) {
            view.actions.get(action.id).run();
            assertEquals(action, port.lastCommand);
        }
        assertEquals(RewardEditorUiSession.Action.values().length, port.commands);
        for (int rule = 1; rule <= 12; rule++) {
            view.actions.get("rule" + rule).run();
            assertEquals(rule, port.rule);
        }
    }

    @Test
    public void hugeListsRemainReachableWithBoundedOwnersAndNoStableFrameScanning() {
        Port port = new Port(10000);
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals(10000, view.rows.size());
        assertEquals(120, view.limit);
        for (int i = 0; i < 250; i++) view.rows.bind(new RecordingUiView(), i);
        assertEquals(120, session.boundRows());
        assertEquals("1:base,9999", view.rows.key(9999));
        int scans = port.scans;
        for (int i = 0; i < 100; i++) session.tick();
        assertEquals(scans, port.scans);
        session.detach(UiDetachReason.SWITCH);
        assertEquals(0, session.boundRows());
        assertEquals(0, port.commands);
        assertEquals(0, port.closes);
    }

    @Test
    public void rowAndToolbarCallbacksCannotSurviveReuseDetachOrDisconnect() {
        Port port = new Port(4);
        boolean[] connected = {true};
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> connected[0]);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        RecordingUiView row = new RecordingUiView();
        view.rows.bind(row, 0);
        Runnable staleRow = row.actions.get("apply");
        view.rows.bind(row, 1);
        staleRow.run();
        assertEquals(0, port.entries);
        row.actions.get("apply").run();
        assertEquals(1, port.entries);
        assertEquals(RewardEditorUiSession.EntryAction.EDIT, port.lastEntry);
        Runnable stale = view.actions.get("upload");
        session.detach(UiDetachReason.RELOAD);
        stale.run();
        assertEquals(0, port.commands);
        RecordingUiView next = new RecordingUiView();
        session.bind(next);
        connected[0] = false;
        next.actions.get("upload").run();
        assertEquals(0, port.commands);
        session.close(UiCloseReason.DISCONNECT);
        session.close(UiCloseReason.DISCONNECT);
        assertEquals(1, port.closes);
    }

    @Test
    public void searchAndNativeHandoffKeepDraftWithoutDispatchingBusinessActions() {
        Port port = new Port(5);
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        view.drafts.get("search").accept("Reward 4");
        assertEquals(1, view.rows.size());
        session.detach(UiDetachReason.SWITCH);
        RecordingUiView nativeView = new RecordingUiView();
        nativeView.nativeView = true;
        session.bind(nativeView);
        assertNull(nativeView.rows);
        assertTrue(nativeView.actions.isEmpty());
        session.detach(UiDetachReason.SWITCH);
        RecordingUiView next = new RecordingUiView();
        session.bind(next);
        assertEquals("Reward 4", next.draftValues.get("search"));
        assertEquals(1, next.rows.size());
        assertEquals(0, port.commands);
        assertEquals(0, port.closes);
    }

    @Test
    public void redactedRulesNeverExposeOrMutateRewardData() {
        Port port = new Port(5);
        port.redacted = true;
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals(0, port.scans);
        assertEquals(0, view.rows.size());
        assertFalse(view.enabled.get("add"));
        view.actions.get("add").run();
        assertEquals(0, port.commands);
    }

    @Test
    public void primarySpritesFollowEnabledStateAndKeepOwnershipAcrossRefresh() {
        Port port = new Port(2);
        lombok.var session = new RewardEditorUiSession(port, () -> true);
        lombok.var view = new RecordingUiView();
        session.bind(view);
        assertTrue(view.icons.get("add_icon").option().texture()[0].location().getPath().endsWith("reward-add-primary.png"));
        assertTrue(view.icons.get("upload_icon").option().texture()[0].location().getPath().endsWith("reward-upload-primary.png"));
        port.redacted = true;
        port.revision++;
        session.tick();
        assertFalse(view.icons.get("upload_icon").option().texture()[0].location().getPath().contains("-primary"));
        port.redacted = false;
        port.revision++;
        session.tick();
        assertTrue(view.icons.get("upload_icon").option().texture()[0].location().getPath().contains("-primary"));
    }

    @Test
    public void primaryPngsAreWhiteAndRetainOriginalAlpha() throws Exception {
        String root = "src/main/resources/assets/apricityui/apricity/sakura_sign_in/icons/reward-";
        for (String name : com.google.common.collect.ImmutableList.of("add", "upload")) {
            assertTrue(Files.exists(java.nio.file.Paths.get(root + name + "-primary.png")));
            lombok.var old = javax.imageio.ImageIO.read(java.nio.file.Paths.get(root + name + ".png").toFile());
            lombok.var white = javax.imageio.ImageIO.read(java.nio.file.Paths.get(root + name + "-primary.png").toFile());
            assertEquals(old.getWidth(), white.getWidth());
            assertEquals(old.getHeight(), white.getHeight());
            for (int y = 0; y < old.getHeight(); y++)
                for (int x = 0; x < old.getWidth(); x++) {
                    assertEquals(old.getRGB(x, y) >>> 24, white.getRGB(x, y) >>> 24);
                    if ((white.getRGB(x, y) >>> 24) != 0) assertEquals(0xffffff, white.getRGB(x, y) & 0xffffff);
                }
        }
    }

    @Test
    public void everyActionHasBothTranslations() throws Exception {
        for (String language : new String[]{"en_us", "zh_cn"}) {
            com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(xin.vanilla.sakura.client.ui.UiBoundaryChecks.text(java.nio.file.Paths.get("src/main/resources/assets/sakura_sign_in/lang/" + language + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (RewardEditorUiSession.Action action : RewardEditorUiSession.Action.values())
                assertTrue(language + " missing " + action.label, json.has("word.sakura_sign_in." + action.label));
        }
    }

    @Test
    public void explicitButtonClearsSearchFocusBeforeKeyboardNavigation() {
        Port port = new Port(2);
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        view.state.selection("search", 1, 1);
        assertFalse(session.key(262, 0, 0, true));
        RecordingUiView row = new RecordingUiView();
        view.rows.bind(row, 0);
        row.actions.get("open").run();
        assertEquals("", view.state.focusedField());
        assertTrue(session.key(262, 0, 0, true));
    }

    @Test
    public void rightClickContextRequiresTargetPressAndMatchingInsideRelease() {
        Port port = new Port(2);
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        RecordingUiView first = new RecordingUiView(), second = new RecordingUiView();
        view.rows.bind(first, 0);
        view.rows.bind(second, 1);
        assertNotNull("Reward context menu must use shared input", first.pointers.get("open"));
        first.pointers.get("open").accept(new UiPointer(UiPointer.Kind.UP, 10, 10, 1));
        assertEquals(0, port.entries);
        first.pointers.get("open").accept(new UiPointer(UiPointer.Kind.DOWN, 10, 10, 1));
        second.pointers.get("open").accept(new UiPointer(UiPointer.Kind.UP, 10, 10, 1));
        assertEquals(0, port.entries);
        first.pointers.get("open").accept(new UiPointer(UiPointer.Kind.UP, 500, 500, 1));
        assertEquals(0, port.entries);
        first.pointers.get("open").accept(new UiPointer(UiPointer.Kind.DOWN, 10, 10, 1));
        first.pointers.get("open").accept(new UiPointer(UiPointer.Kind.UP, 10, 10, 1));
        assertEquals(1, port.entries);
        assertEquals("CONTEXT", port.lastEntry.name());
    }

    @Test
    public void allOrdinaryRuleFormsKeepNormalizationAndOriginalValidation() throws Exception {
        lombok.var owner = new xin.vanilla.sakura.screen.RewardOptionScreen();
        lombok.var method = owner.getClass().getDeclaredMethod("getRuleKeyInputScreen", net.minecraft.client.gui.screens.Screen.class, xin.vanilla.sakura.enums.ERewardRule.class, String[].class);
        method.setAccessible(true);
        net.minecraft.client.gui.screens.Screen parent = new net.minecraft.client.gui.screens.Screen(new net.minecraft.network.chat.TextComponent("")) {
        };
        for (lombok.var rule : xin.vanilla.sakura.enums.ERewardRule.values()) {
            if (rule.getCode() == 1 || rule.getCode() >= 10) continue;
            String[] key = {""};
            String value = rule.getCode() == 7 ? "2026-10-09" : rule.getCode() == 9 ? "12.34567" : "1";
            lombok.var form = (StringInputScreen) method.invoke(owner, parent, rule, key);
            lombok.var session = form.uiPage().session();
            RecordingUiView view = new RecordingUiView();
            session.bind(view);
            RecordingUiView row = new RecordingUiView();
            view.rows.bind(row, 0);
            assertEquals("", row.draftValues.get("input"));
            row.drafts.get("input").accept(value);
            view.actions.get("submit").run();
            assertEquals(rule.name(), rule.getCode() == 9 ? "0.1234567" : value, key[0]);
        }
    }

    @Test
    public void cdkThreeFieldFormKeepsDefaultsAndValidation() throws Exception {
        lombok.var owner = new xin.vanilla.sakura.screen.RewardOptionScreen();
        lombok.var method = owner.getClass().getDeclaredMethod("getCdkRuleKeyInputScreen", net.minecraft.client.gui.screens.Screen.class, xin.vanilla.sakura.enums.ERewardRule.class, String[].class);
        method.setAccessible(true);
        net.minecraft.client.gui.screens.Screen parent = new net.minecraft.client.gui.screens.Screen(new net.minecraft.network.chat.TextComponent("")) {
        };
        String[] key = {""};
        lombok.var form = (StringInputScreen) method.invoke(owner, parent, xin.vanilla.sakura.enums.ERewardRule.CDK_REWARD, key);
        lombok.var session = form.uiPage().session();
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals(3, view.rows.size());
        RecordingUiView code = new RecordingUiView(), until = new RecordingUiView(), count = new RecordingUiView();
        view.rows.bind(code, 0);
        view.rows.bind(until, 1);
        view.rows.bind(count, 2);
        assertEquals("1", count.draftValues.get("input"));
        assertFalse(until.draftValues.get("input").isEmpty());
        code.drafts.get("input").accept("TEST");
        count.drafts.get("input").accept("0");
        view.actions.get("submit").run();
        assertEquals("", key[0]);
        count.drafts.get("input").accept("2");
        view.actions.get("submit").run();
        assertTrue(key[0].startsWith("TEST|"));
        assertTrue(key[0].endsWith("|-1|2"));
    }

    @Test
    public void baseGroupControlsStayProtectedAndSidebarCanClose() {
        Port port = new Port(0);
        port.sidebar = false;
        port.rows.add(new RewardEditorUiSession.Entry("base,0", "Base", "", "", null, true, false, false, false));
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertFalse(view.enabled.get("rules"));
        RecordingUiView row = new RecordingUiView();
        view.rows.bind(row, 0);
        assertFalse(row.enabled.get("apply"));
        assertFalse(row.enabled.get("delete"));
        row.actions.get("apply").run();
        row.actions.get("delete").run();
        assertEquals(0, port.entries);
    }


    @Test
    public void singleColumnNavigationUsesAdjacentRewardsWithoutChangingNativeGeometry() throws Exception {
        Class<?> direction = xin.vanilla.sakura.client.gui.RewardKeyboardNavigator.Direction.class;
        java.lang.reflect.Method projection;
        try {
            projection = RewardEditorUiSession.class.getMethod("listDirection", direction);
        } catch (NoSuchMethodException absent) {
            fail("AUI list navigation still uses native grid geometry");
            return;
        }
        lombok.var up = xin.vanilla.sakura.client.gui.RewardKeyboardNavigator.Direction.UP;
        lombok.var down = xin.vanilla.sakura.client.gui.RewardKeyboardNavigator.Direction.DOWN;
        assertEquals(xin.vanilla.sakura.client.gui.RewardKeyboardNavigator.Direction.LEFT, projection.invoke(null, up));
        assertEquals(xin.vanilla.sakura.client.gui.RewardKeyboardNavigator.Direction.RIGHT, projection.invoke(null, down));

        Port port = new Port(100);
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        session.reveal("base,90");
        port.revision++;
        session.tick();
        assertEquals(90, view.state.rowPosition(), 0);
    }


    @Test
    public void filteredNavigationCannotSelectRewardsOutsideTheVisibleSearch() throws Exception {
        Port port = new Port(100);
        RewardEditorUiSession session = new RewardEditorUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        view.drafts.get("search").accept("base,90");
        assertEquals(1, view.rows.size());
        java.lang.reflect.Method navigation;
        try {
            navigation = RewardEditorUiSession.class.getMethod("navigationIds");
        } catch (NoSuchMethodException absent) {
            fail("Filtered keyboard navigation needs the actual visible row order");
            return;
        }
        assertEquals(com.google.common.collect.ImmutableList.of("base,90"), navigation.invoke(session));

    }


    @Test
    public void multiFieldAdapterUsesSharedDraftValidationAndSingleSubmit() {
        AtomicInteger submits = new AtomicInteger();
        net.minecraft.client.gui.screens.Screen parent = new net.minecraft.client.gui.screens.Screen(new net.minecraft.network.chat.TextComponent("")) {
        };
        StringInputScreen screen = new StringInputScreen(parent, Text.literal("Value"), Text.literal("Hint"), "\\d*", "12", values -> {
            if (Integer.parseInt(values.get(0)) == 0) return new StringList("Zero");
            submits.incrementAndGet();
            return new StringList();
        });
        UiPageSpec page = screen.uiPage();
        assertNotNull("Final Sakura input adapter must opt into the common form", page);
        RecordingUiView view = new RecordingUiView();
        page.session().bind(view);
        RecordingUiView row = new RecordingUiView();
        view.rows.bind(row, 0);
        assertEquals("12", row.draftValues.get("input"));
        row.drafts.get("input").accept("0");
        view.actions.get("submit").run();
        assertEquals(0, submits.get());
        page.session().detach(UiDetachReason.SWITCH);
        RecordingUiView next = new RecordingUiView();
        page.session().bind(next);
        RecordingUiView restored = new RecordingUiView();
        next.rows.bind(restored, 0);
        assertEquals("0", restored.draftValues.get("input"));
        restored.drafts.get("input").accept("42");
        Runnable submit = next.actions.get("submit");
        submit.run();
        submit.run();
        assertEquals(1, submits.get());
    }


}
