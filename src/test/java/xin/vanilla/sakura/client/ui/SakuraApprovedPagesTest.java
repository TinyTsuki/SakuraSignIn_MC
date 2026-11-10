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
    public void envelopeKeepsItsBodyFixedWhileTheFlapMovesAndSmallCardsUseTooltips() throws Exception {
        long[] now = {0};
        lombok.var packet = new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Pool",
                com.google.common.collect.ImmutableList.of(Reward.getDefault()), com.google.common.collect.ImmutableList.of(Reward.getDefault()), LotteryPreviewMode.ALL, LotteryAnimationStyle.ENVELOPE);
        lombok.var session = new LotteryRevealUiSession(packet, packet.getAnimationStyle(), () -> now[0], () -> true, () -> 69, () -> {
        });
        lombok.var view = new RecordingUiView();
        view.geometry.put("scene", new xin.vanilla.banira.client.data.ScreenCoordinate(0, 0, 320, 180));
        session.bind(view);
        view.geometry.put("scene", new xin.vanilla.banira.client.data.ScreenCoordinate(0, 0, 640, 360));
        session.tick();
        lombok.var body = view.areas.get("motion_shell");
        assertEquals(220, body.width(), 0);
        assertEquals(130, body.height(), 0);
        for (long time : new long[]{450, 600, 800, 1000, 1300}) {
            now[0] = time;
            session.tick();
            assertEquals(body, view.areas.get("motion_shell"));
            assertEquals("", view.texts.get("motion_name0").toString());
        }
        assertTrue(view.areas.get("motion_flap_open").height() > 0);
        assertEquals(0, view.areas.get("motion_flap_closed").height(), 0);


    }

    @Test
    public void rewardNavigationStaysBesideTheListAndEmptyDetailsHaveNoPlaceholder() throws Exception {
        Path root = java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        String html = new String(Files.readAllBytes(root.resolve("rewards.html")), java.nio.charset.StandardCharsets.UTF_8);
        int workspace = html.indexOf("id=\"workspace\"");
        int navigation = html.indexOf("id=\"reward_navigation\"");
        assertTrue(workspace < navigation && navigation < html.indexOf("id=\"rules\""));
        assertTrue(html.indexOf("id=\"group_nav\"") < html.indexOf("</nav>"));
        assertTrue(html.indexOf("id=\"add\"") < html.indexOf("class=\"reward-tools\""));
        assertTrue(html.contains("id=\"edit_group_icon\""));
        String css = new String(Files.readAllBytes(root.resolve("sakura-pages.css")), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(css.contains("flex-wrap:wrap"));
        assertTrue(css.contains(".detail-content{white-space:normal"));
        lombok.var view = new RecordingUiView();
        new RewardEditorUiSession(new RewardEditorUiParityTest.Port(1), () -> true).bind(view);
        assertFalse(view.enabled.get("detail_icon"));
        assertFalse(view.enabled.get("detail_subtitle"));
        assertFalse(view.enabled.get("empty"));
        assertNotNull(view.icons.get("edit_group_icon"));
        assertFalse(view.texts.containsKey("edit_group"));
    }


    @Test
    public void approvedCalendarUsesWholeWeeksAndDoesNotCompressDatesUnderStatusIcons() {
        lombok.var cells = SignInCalendarGrid.complete(java.time.LocalDate.of(2026, 11, 1), 7, com.google.common.collect.ImmutableList.of());
        assertEquals(35, SignInUiSession.visibleCellCount(cells));
        assertEquals(42, SignInUiSession.visibleCellCount(SignInCalendarGrid.complete(java.time.LocalDate.of(2026, 8, 1), 7, com.google.common.collect.ImmutableList.of())));
        assertEquals(28, SignInUiSession.visibleCellCount(SignInCalendarGrid.complete(java.time.LocalDate.of(2026, 2, 1), 7, com.google.common.collect.ImmutableList.of())));
        lombok.var port = new SignInUiParityTest.Port() {
            public List<SignInUiSession.Cell> cells() {
                return cells;
            }
        };
        lombok.var view = new RecordingUiView();
        view.geometry.put("calendar", new xin.vanilla.banira.client.data.ScreenCoordinate(0, 0, 351, 150));
        lombok.var session = new SignInUiSession(port, () -> true);
        session.bind(view);
        for (int i = 0; i < 35; i++) {
            lombok.var area = view.areas.get("day" + i + "_slot");
            assertEquals((i % 7) * 50, area.x(), 0);
            assertEquals((i / 7) * 30, area.y(), 0);
            assertEquals(50, area.width(), 0);
            assertEquals(30, area.height(), 0);
        }
        for (int i = 35; i < 42; i++) assertEquals(0, view.areas.get("day" + i + "_slot").height(), 0);
        view.geometry.put("calendar", new xin.vanilla.banira.client.data.ScreenCoordinate(0, 0, 701, 200));
        session.tick();
        assertEquals(100, view.areas.get("day1_slot").width(), 0);
        assertEquals(40, view.areas.get("day7_slot").height(), 0);
    }

    @Test
    public void changingMonthsCannotKeepOrActivateASelectedDateInAnOmittedWeek() {
        java.time.LocalDate[] month = {java.time.LocalDate.of(2026, 12, 1)};
        lombok.var original = new SignInUiSession.Cell(20261207, SignInUiParityTest.text("7"), SignInUiParityTest.text(""), null);
        lombok.var port = new SignInUiParityTest.Port() {
            public List<SignInUiSession.Cell> cells() {
                return SignInCalendarGrid.complete(month[0], 7, com.google.common.collect.ImmutableList.of(original));
            }
        };
        lombok.var view = new RecordingUiView();
        lombok.var session = new SignInUiSession(port, () -> true);
        session.bind(view);
        view.actions.get("day8").run();
        assertEquals("20261207", view.state.selectedId());
        month[0] = java.time.LocalDate.of(2026, 11, 1);
        port.revision++;
        session.tick();
        assertEquals("20261101", view.state.selectedId());
        view.actions.get("day36").run();
        assertEquals(com.google.common.collect.ImmutableList.of(20261207), port.requests);
        assertFalse(view.enabled.get("day36"));
    }

    @Test
    public void dateDetailButtonUsesTheExistingDateActionAndRejectsRetiredViews() {
        lombok.var port = new SignInUiParityTest.Port();
        lombok.var session = new SignInUiSession(port, () -> true);
        lombok.var view = new RecordingUiView();
        session.bind(view);
        Runnable oldAction = view.actions.get("date_action");
        oldAction.run();
        assertEquals(com.google.common.collect.ImmutableList.of(20261009), port.requests);
        session.detach(UiDetachReason.SWITCH);
        oldAction.run();
        assertEquals(1, port.requests.size());
    }


    @Test
    public void physicalItemFeeUsesAnIconAndTheNameOnlyInTooltip() {
        xin.vanilla.sakura.test.BaniraTestPlatform.register("minecraft:diamond", net.minecraft.world.item.Items.DIAMOND);
        xin.vanilla.banira.common.util.ItemUtils.clearCache();
        lombok.var fee = LotteryFeePresentation.of("item(\"minecraft:diamond\",2)");
        assertNotNull(fee.icon());
        assertEquals("\u00d72", fee.text());
        assertTrue(fee.tooltip().endsWith("\u00d72"));
        assertNull(LotteryFeePresentation.of(null).icon());
        assertNull(LotteryFeePresentation.of("xpPoints(2)").icon());
        assertEquals("all(xpPoints(2),xpLevels(1))", LotteryFeePresentation.of("all(xpPoints(2),xpLevels(1))").text());
    }

    @Test
    public void eachNewServerStyleSurvivesReloadAndNativeHandoffWithoutEarlyOrRepeatedClaim() {
        for (lombok.var style : LotteryAnimationStyle.editorChoices(null)) {
            int[] claims = {0};
            long[] now = {0};
            lombok.var packet = new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Pool",
                    Collections.nCopies(5, Reward.getDefault()), com.google.common.collect.ImmutableList.of(), LotteryPreviewMode.NONE, style);
            lombok.var session = new LotteryRevealUiSession(packet, style, () -> now[0], () -> true, () -> 69, () -> claims[0]++);
            lombok.var first = new RecordingUiView();
            session.bind(first);
            lombok.var stale = first.actions.get("action");
            long duration = LotteryMotion.duration(style, 5, packet.getOperationId().hashCode());
            now[0] = duration - 1;
            session.tick();
            assertFalse(session.revealed());
            assertEquals(0, claims[0]);
            session.detach(UiDetachReason.SWITCH);
            lombok.var nativeView = new RecordingUiView();
            nativeView.nativeView = true;
            session.bind(nativeView);
            stale.run();
            assertEquals(0, claims[0]);
            session.detach(UiDetachReason.RELOAD);
            lombok.var next = new RecordingUiView();
            session.bind(next);
            now[0] = duration;
            session.tick();
            session.tick();
            assertEquals(1, claims[0]);
            lombok.var row = new RecordingUiView();
            next.providers.get("fields").bind(row, 1);
            assertEquals("", row.texts.get("index").toString());
            session.close(UiCloseReason.USER_CLOSE);
            assertEquals(1, claims[0]);
        }
    }

    @Test
    public void calendarSelectionAndOpeningTipsDoNotChangeSignInDates() {
        lombok.var port = new SignInUiParityTest.Port();
        port.tips = true;
        lombok.var view = new RecordingUiView();
        lombok.var session = new SignInUiSession(port, () -> true);
        session.bind(view);
        assertNull(view.icons.get("status0"));
        view.actions.get("day0").run();
        assertTrue(port.requests.isEmpty());
        view.actions.get("tips-confirm").run();
        view.actions.get("day1").run();
        assertEquals(com.google.common.collect.ImmutableList.of(20261008), port.requests);
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
        lombok.var packet = new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Pool",
                com.google.common.collect.ImmutableList.of(Reward.getDefault()), Collections.nCopies(64, Reward.getDefault()), LotteryPreviewMode.ALL, LotteryAnimationStyle.FLIP);
        lombok.var session = new LotteryRevealUiSession(packet, packet.getAnimationStyle(), () -> now[0], () -> true, () -> 69, () -> {
        });
        lombok.var view = new RecordingUiView();
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
                return com.google.common.collect.ImmutableList.of(new RewardEditorUiSession.Entry("group-a", "A", "1", "A", null, true, collapsed, false, true),
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
        lombok.var port = new Port();
        lombok.var view = new RecordingUiView();
        lombok.var session = new RewardEditorUiSession(port, () -> true);
        session.bind(view);
        assertEquals(2, view.providers.get("groups").size());
        assertEquals("2:a,0", view.providers.get("fields").key(0));
        lombok.var group = new RecordingUiView();
        view.providers.get("groups").bind(group, 0);
        lombok.var recycled = group.actions.get("label");
        view.providers.get("groups").bind(group, 1);
        recycled.run();
        assertEquals("", port.focused);
        lombok.var stale = group.actions.get("label");
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
        lombok.var folded = new Port();
        folded.collapsed = true;
        lombok.var restored = new RecordingUiView();
        new RewardEditorUiSession(folded, () -> true).bind(restored);
        assertFalse(folded.collapsed);
        assertEquals("group-a", folded.focused);
        assertEquals("2:a,0", restored.providers.get("fields").key(0));
    }
}
