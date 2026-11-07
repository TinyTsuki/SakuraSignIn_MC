package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.sakura.client.gui.lottery.LotteryFeePresentation;
import xin.vanilla.sakura.client.gui.lottery.LotteryMotion;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.reward.Reward;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class SakuraApprovedPagesTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @Test
    public void onlyTheThreeApprovedPagesLoadTheNewScopedStylesWithoutScriptsOrCalendarImages() throws Exception {
        Path root = Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        for (String page : List.of("signin", "rewards", "lottery")) {
            String html = Files.readString(root.resolve(page + ".html"));
            assertTrue(html.contains(page.equals("lottery") ? "sakura-lottery.css" : "sakura-pages.css"));
            assertFalse(html.contains("<script"));
            assertFalse(html.contains("https:"));
        }
        assertFalse(Files.readString(root.resolve("personal-date.html")).contains("sakura-pages.css"));
        assertFalse(Files.readString(root.resolve("signin.css")).contains("url("));
        String rewards = Files.readString(root.resolve("rewards.html"));
        for (String id : List.of("group_nav", "groups", "add_group", "edit_group", "upload", "search", "fields"))
            assertTrue(rewards.contains("id=\"" + id + "\""));
        assertEquals(List.of(), UiBoundaryChecks.resources(UiBoundaryChecks.ownResources("sakura_sign_in"), false));
    }

    @Test
    public void physicalItemFeeUsesAnIconAndTheNameOnlyInTooltip() {
        xin.vanilla.sakura.test.BaniraTestPlatform.register("minecraft:diamond", net.minecraft.world.item.Items.DIAMOND);
        xin.vanilla.banira.common.util.ItemUtils.clearCache();
        var fee = LotteryFeePresentation.of("item(\"minecraft:diamond\",2)");
        assertNotNull(fee.icon());
        assertEquals("\u00d72", fee.text());
        assertTrue(fee.tooltip().endsWith("\u00d72"));
        assertNull(LotteryFeePresentation.of(null).icon());
        assertNull(LotteryFeePresentation.of("xpPoints(2)").icon());
        assertEquals("all(xpPoints(2),xpLevels(1))", LotteryFeePresentation.of("all(xpPoints(2),xpLevels(1))").text());
    }

    @Test
    public void eachNewServerStyleSurvivesReloadAndNativeHandoffWithoutEarlyOrRepeatedClaim() {
        for (var style : LotteryAnimationStyle.editorChoices(null)) {
            int[] claims = {0};
            long[] now = {0};
            var packet = new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Pool",
                    Collections.nCopies(5, Reward.getDefault()), List.of(), LotteryPreviewMode.NONE, style);
            var session = new LotteryRevealUiSession(packet, style, () -> now[0], () -> true, () -> 69, () -> claims[0]++);
            var first = new RecordingUiView();
            session.bind(first);
            var stale = first.actions.get("action");
            long duration = LotteryMotion.duration(style, 5, packet.getOperationId().hashCode());
            now[0] = duration - 1;
            session.tick();
            assertFalse(session.revealed());
            assertEquals(0, claims[0]);
            session.detach(UiDetachReason.SWITCH);
            var nativeView = new RecordingUiView();
            nativeView.nativeView = true;
            session.bind(nativeView);
            stale.run();
            assertEquals(0, claims[0]);
            session.detach(UiDetachReason.RELOAD);
            var next = new RecordingUiView();
            session.bind(next);
            now[0] = duration;
            session.tick();
            session.tick();
            assertEquals(1, claims[0]);
            var row = new RecordingUiView();
            next.providers.get("fields").bind(row, 1);
            assertEquals("", row.texts.get("index").toString());
            session.close(UiCloseReason.USER_CLOSE);
            assertEquals(1, claims[0]);
        }
    }

    @Test
    public void calendarSelectionAndOpeningTipsDoNotChangeSignInDates() {
        var port = new SignInUiParityTest.Port();
        port.tips = true;
        var view = new RecordingUiView();
        var session = new SignInUiSession(port, () -> true);
        session.bind(view);
        assertNull(view.icons.get("status0"));
        view.actions.get("day0").run();
        assertTrue(port.requests.isEmpty());
        view.actions.get("tips-confirm").run();
        view.actions.get("day1").run();
        assertEquals(List.of(20261008), port.requests);
        assertTrue(view.enabled.get("selected1"));
        assertFalse(view.enabled.get("selected0"));
        port.empty = true;
        port.revision++;
        session.tick();
        assertFalse(view.enabled.get("selected1"));
    }

    @Test
    public void flipDoesNotFillDecoysWithExtraCopiesOfTheWinningReward() {
        long[] now = {0};
        var packet = new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Pool",
                List.of(Reward.getDefault()), Collections.nCopies(64, Reward.getDefault()), LotteryPreviewMode.ALL, LotteryAnimationStyle.FLIP);
        var session = new LotteryRevealUiSession(packet, packet.getAnimationStyle(), () -> now[0], () -> true, () -> 69, () -> {
        });
        var view = new RecordingUiView();
        session.bind(view);
        now[0] = 3400;
        session.tick();
        long prizes = java.util.stream.IntStream.range(0, 5).filter(i -> view.icons.get("motion_icon" + i) != null).count();
        assertTrue(prizes == 2 || prizes == 3);
    }

    @Test
    public void groupNavigationUsesRealGroupIdsAndCannotSurviveDetachOrPermissionLoss() {
        class Port implements RewardEditorUiSession.Port {
            long revision;
            boolean redacted, collapsed;
            int commands;
            String focused = "";

            public long revision() {
                return revision;
            }

            public void refresh() {
            }

            public List<RewardEditorUiSession.Entry> entries() {
                return List.of(new RewardEditorUiSession.Entry("group-a", "A", "1", "A", null, true, collapsed, false, true),
                        new RewardEditorUiSession.Entry("a,0", "Apple", "item", "Apple", null, false, false, false, true),
                        new RewardEditorUiSession.Entry("group-b", "B", "1", "B", null, true, false, false, true),
                        new RewardEditorUiSession.Entry("b,0", "Bread", "item", "Bread", null, false, false, false, true));
            }

            public int rule() {
                return 2;
            }

            public void rule(int value) {
            }

            public boolean redacted() {
                return redacted;
            }

            public boolean enabled(RewardEditorUiSession.Action action) {
                return !redacted;
            }

            public boolean sidebarOpen() {
                return true;
            }

            public boolean groupNavigation() {
                return true;
            }

            public void command(RewardEditorUiSession.Action action) {
                commands++;
            }

            public void entry(String id, RewardEditorUiSession.EntryAction action) {
                focused = id;
                if (action == RewardEditorUiSession.EntryAction.FOCUS_GROUP) collapsed = false;
                revision++;
            }

            public void suspend() {
            }

            public void closed(UiCloseReason reason) {
            }

            public boolean key(int key, int scan, int mods, boolean pressed) {
                return false;
            }
        }
        var port = new Port();
        var view = new RecordingUiView();
        var session = new RewardEditorUiSession(port, () -> true);
        session.bind(view);
        assertEquals(2, view.providers.get("groups").size());
        assertEquals("2:a,0", view.providers.get("fields").key(0));
        var group = new RecordingUiView();
        view.providers.get("groups").bind(group, 0);
        var recycled = group.actions.get("label");
        view.providers.get("groups").bind(group, 1);
        recycled.run();
        assertEquals("", port.focused);
        var stale = group.actions.get("label");
        stale.run();
        assertEquals("group-b", port.focused);
        assertEquals("2:b,0", view.providers.get("fields").key(0));
        view.actions.get("add").run();
        assertEquals("group-b", port.focused);
        assertEquals(1, port.commands);
        port.redacted = true;
        port.revision++;
        session.tick();
        assertEquals(0, view.providers.get("fields").size());
        stale.run();
        assertEquals(1, port.commands);
        session.detach(UiDetachReason.SWITCH);
        stale.run();
        assertEquals(1, port.commands);
        var folded = new Port();
        folded.collapsed = true;
        var restored = new RecordingUiView();
        new RewardEditorUiSession(folded, () -> true).bind(restored);
        assertFalse(folded.collapsed);
        assertEquals("group-a", folded.focused);
        assertEquals("2:a,0", restored.providers.get("fields").key(0));
    }
}
