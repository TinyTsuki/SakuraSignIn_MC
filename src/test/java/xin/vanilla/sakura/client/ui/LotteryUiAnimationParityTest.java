package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.reward.Reward;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class LotteryUiAnimationParityTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    static LotteryRevealPacket packet() {
        return new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Daily",
                List.of(Reward.getDefault()), List.of(Reward.getDefault()), LotteryPreviewMode.ALL);
    }

    @Test
    public void scanCodeInventoryBindingSkipsOnlyOnPressAndClaimsOnce() {
        int[] claims = {0};
        var session = new LotteryRevealUiSession(packet(), LotteryAnimationStyle.STRIP, () -> 0, () -> true,
                (key, scan) -> key == -1 && scan == 42, () -> claims[0]++);
        var view = new RecordingUiView();
        session.bind(view);
        assertFalse(session.key(-1, 41, 0, true));
        assertFalse(session.key(-1, 42, 0, false));
        assertEquals(0, claims[0]);
        assertTrue(session.key(-1, 42, 0, true));
        assertTrue(session.revealed());
        assertEquals(1, claims[0]);
        assertTrue(session.key(-1, 42, 0, true));
        assertEquals(1, view.closes);
        session.close(UiCloseReason.USER_CLOSE);
        assertEquals(1, claims[0]);
    }

    @Test
    public void loopingStripKeepsCandidatesOnBothSidesAtStartAndFinish() {
        for (double width : new double[]{110, 300, 536, 1200})
            for (long time : new long[]{0, 2000, 6400, 6500, 6799}) {
                var visible = LotteryRevealUiSession.frame(LotteryAnimationStyle.STRIP, time, width, 140, 12).stream()
                        .filter(s -> s.x() >= 0 && s.x() + 16 <= width).sorted(Comparator.comparingDouble(LotteryRevealUiSession.Slot::x)).toList();
                assertTrue("Left edge must retain a candidate", visible.get(0).x() <= 38);
                assertTrue("Right edge must retain a candidate", visible.get(visible.size() - 1).x() + 16 >= width - 38);
                for (int i = 1; i < visible.size(); i++)
                    assertEquals(38, visible.get(i).x() - visible.get(i - 1).x(), 1);
            }
    }

    @Test
    public void rectangularWheelRetainsClearanceIncludingNarrowAreas() {
        for (double width : new double[]{80, 110, 180, 300, 536})
            for (double height : new double[]{60, 90, 140, 188}) {
                var slots = LotteryRevealUiSession.frame(LotteryAnimationStyle.ROULETTE, 6500, width, height, 12);
                assertTrue(slots.size() > 0 && slots.size() <= 12);
                assertTrue(slots.get(slots.size() - 1).active());
                for (var s : slots) {
                    assertTrue(s.x() >= 3 && s.y() >= 3 && s.x() + 19 <= width && s.y() + 19 <= height);
                    for (var other : slots)
                        if (s != other)
                            assertTrue("Wheel icons and markers must not overlap", Math.abs(s.x() - other.x()) >= 28 || Math.abs(s.y() - other.y()) >= 28);
                }
            }
    }

    @Test
    public void nativeStripAndWheelUseTheSameFrameContract() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/xin/vanilla/sakura/screen/LotteryRevealScreen.java"));
        assertTrue(source.contains("LotteryRevealUiSession.frame(LotteryAnimationStyle.STRIP"));
        assertTrue(source.contains("LotteryRevealUiSession.frame(LotteryAnimationStyle.ROULETTE"));
        assertFalse(source.contains("Math.cos(angle)"));
    }

    @Test
    public void fourModesUseOriginalGeometryDurationsAndDeterminedWinnerPositions() {
        var strip = LotteryRevealUiSession.frame(LotteryAnimationStyle.STRIP, 6500, 300, 140, 1);
        assertEquals(48, strip.size());
        assertEquals(142, strip.get(43).x(), 0);
        assertTrue(strip.get(43).active());
        var start = LotteryRevealUiSession.frame(LotteryAnimationStyle.STRIP, 0, 300, 140, 1);
        assertNotEquals(start.get(43).x(), strip.get(43).x(), 0);
        var cards = LotteryRevealUiSession.frame(LotteryAnimationStyle.CARDS, 0, 300, 140, 1);
        assertEquals(7, cards.size());
        assertTrue(cards.get(0).face());
        assertFalse(cards.get(1).face());
        cards = LotteryRevealUiSession.frame(LotteryAnimationStyle.CARDS, 4000, 300, 140, 1);
        assertTrue(cards.stream().allMatch(LotteryRevealUiSession.Slot::face));
        assertTrue(cards.get(6).active());
        var wheel = LotteryRevealUiSession.frame(LotteryAnimationStyle.ROULETTE, 6500, 300, 140, 64);
        assertEquals(12, wheel.size());
        assertTrue(wheel.get(11).active());
        assertEquals(1, wheel.stream().filter(LotteryRevealUiSession.Slot::active).count());
        assertTrue(LotteryRevealUiSession.frame(LotteryAnimationStyle.INSTANT, 0, 300, 140, 1).isEmpty());
        assertEquals(6800, LotteryRevealUiSession.duration(LotteryAnimationStyle.STRIP));
        assertEquals(4800, LotteryRevealUiSession.duration(LotteryAnimationStyle.CARDS));
        assertEquals(6800, LotteryRevealUiSession.duration(LotteryAnimationStyle.ROULETTE));
        assertEquals(0, LotteryRevealUiSession.duration(LotteryAnimationStyle.INSTANT));
    }

    @Test
    public void bindDetachResizeAndReloadDoNotClaimAndAcknowledgedResultKeepsIdentity() {
        for (LotteryAnimationStyle style : LotteryAnimationStyle.values()) {
            long[] clock = {0};
            int[] claims = {0};
            var packet = packet();
            var session = new LotteryRevealUiSession(packet, style, () -> clock[0], () -> true, (key, scan) -> key == 69, () -> claims[0]++);
            var view = new RecordingUiView();
            session.bind(view);
            assertEquals(0, claims[0]);
            Runnable stale = view.actions.get("action");
            for (UiDetachReason reason : new UiDetachReason[]{UiDetachReason.SWITCH, UiDetachReason.RESIZE, UiDetachReason.RELOAD}) {
                session.detach(reason);
                session.bind(new RecordingUiView());
                assertEquals(0, claims[0]);
            }
            stale.run();
            assertEquals(0, claims[0]);
            clock[0] = 7000;
            session.detach(UiDetachReason.RELOAD);
            session.bind(new RecordingUiView());
            assertEquals(0, claims[0]);
            session.tick();
            session.tick();
            assertEquals(1, claims[0]);
            assertEquals(packet.getOperationId(), session.operationId());
            session.detach(UiDetachReason.SWITCH);
            session.bind(new RecordingUiView());
            session.tick();
            session.close(UiCloseReason.USER_CLOSE);
            session.close(UiCloseReason.USER_CLOSE);
            assertEquals(1, claims[0]);
            assertEquals(packet.getOperationId(), session.operationId());
        }
    }

    @Test
    public void skipAndConfirmAreDistinctAndInventoryKeyNeverRepeatsClaim() {
        int[] claims = {0};
        var session = new LotteryRevealUiSession(packet(), LotteryAnimationStyle.STRIP, () -> 0, () -> true, (key, scan) -> key == 69, () -> claims[0]++);
        var view = new RecordingUiView();
        session.bind(view);
        view.actions.get("action").run();
        assertEquals(1, claims[0]);
        assertEquals(0, view.closes);
        assertTrue(session.revealed());
        assertEquals(1, view.rows.size());
        assertTrue(session.key(69, 0, 0, true));
        assertEquals(1, view.closes);
        session.close(UiCloseReason.USER_CLOSE);
        assertEquals(1, claims[0]);
    }

    @Test
    public void nativeBindingHasNoAuiKeyOrClockOwnershipAndDisconnectNeverAcknowledges() {
        boolean[] connected = {true};
        int[] claims = {0};
        long[] clock = {0};
        var session = new LotteryRevealUiSession(packet(), LotteryAnimationStyle.STRIP, () -> clock[0], () -> connected[0], (key, scan) -> key == 69, () -> claims[0]++);
        var view = new RecordingUiView();
        session.bind(view);
        Runnable stale = view.actions.get("action");
        session.detach(UiDetachReason.FALLBACK);
        var nativeView = new RecordingUiView();
        nativeView.nativeView = true;
        session.bind(nativeView);
        assertFalse("Native owner handles its own inventory key", session.key(69, 0, 0, true));
        clock[0] = 7000;
        session.tick();
        assertEquals(0, claims[0]);
        connected[0] = false;
        stale.run();
        session.requestClaim();
        session.close(UiCloseReason.DISCONNECT);
        assertEquals(0, claims[0]);
    }

    @Test
    public void invalidOrTinyAreasNeverProduceNonfiniteGeometry() {
        for (LotteryAnimationStyle style : LotteryAnimationStyle.values()) {
            assertTrue(LotteryRevealUiSession.frame(style, 100, Double.NaN, 100, 1).isEmpty());
            for (var slot : LotteryRevealUiSession.frame(style, -100, 1, 1, 64)) {
                assertTrue(Double.isFinite(slot.x()));
                assertTrue(Double.isFinite(slot.y()));
            }
        }
    }

    @Test
    public void animationUsesFiniteRetainedSlotsAndResultsRemainVirtualized() {
        long[] clock = {0};
        var session = new LotteryRevealUiSession(packet(), LotteryAnimationStyle.STRIP, () -> clock[0], () -> true, (key, scan) -> key == 69, () -> {
        });
        var view = new RecordingUiView();
        session.bind(view);
        assertTrue(view.areas.size() <= 104);
        assertFalse(view.enabled.get("content"));
        clock[0] = 6500;
        session.tick();
        assertEquals(184, view.areas.get("marker").x(), 0);
        clock[0] = 6800;
        session.tick();
        assertEquals(40, view.limit);
        assertTrue(view.enabled.get("result_single"));
        assertFalse(view.enabled.get("content"));
        assertFalse(view.enabled.get("scene"));
        assertTrue(view.areas.get("slot43").width() == 0);
    }

    @Test
    public void revealedStableFramesDoNotRewriteTheDocument() {
        var session = new LotteryRevealUiSession(packet(), LotteryAnimationStyle.INSTANT, () -> 0, () -> true, (key, scan) -> key == 69, () -> {
        });
        var view = new RecordingUiView();
        session.bind(view);
        int writes = view.writes;
        for (int i = 0; i < 100; i++) session.tick();
        assertEquals("Unchanged result needs no DOM updates", writes, view.writes);
    }

    @Test
    public void oldResultRowsCannotWriteIntoNewConnectionOrClosedView() {
        boolean[] connected = {true};
        var session = new LotteryRevealUiSession(packet(), LotteryAnimationStyle.INSTANT, () -> 0, () -> connected[0], (key, scan) -> key == 69, () -> {
        });
        var view = new RecordingUiView();
        session.bind(view);
        var rows = view.rows;
        connected[0] = false;
        var late = new RecordingUiView();
        rows.bind(late, 0);
        assertEquals(0, late.writes);
        connected[0] = true;
        session.detach(UiDetachReason.RELOAD);
        rows.bind(late, 0);
        assertEquals(0, late.writes);
    }

    @Test
    public void cardModeRetainsCardFacesWithoutExposingHiddenPreview() {
        var value = new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Hidden",
                List.of(Reward.getDefault()), List.of(), LotteryPreviewMode.NONE);
        var session = new LotteryRevealUiSession(value, LotteryAnimationStyle.CARDS, () -> 0, () -> true, (key, scan) -> key == 69, () -> {
        });
        var view = new RecordingUiView();
        session.bind(view);
        assertNotNull("Card mode needs its original card surfaces", view.areas.get("card0"));
        assertEquals(24, view.areas.get("card0").width(), 0);
        assertEquals(32, view.areas.get("card0").height(), 0);
        assertTrue(view.enabled.get("card0"));
        assertFalse(view.enabled.get("card1"));
        assertNull(view.icons.get("slot0"));
        assertEquals("?", view.texts.get("slot0").toString());
        view.actions.get("action").run();
        assertTrue(session.revealed());
        assertEquals(1, view.rows.size());
    }
}
