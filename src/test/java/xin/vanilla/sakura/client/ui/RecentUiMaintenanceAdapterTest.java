package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.api.client.ui.UiValue;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.Reward;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RecentUiMaintenanceAdapterTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    static final class LegacyPort implements LotteryUiSession.Port {
        final LotteryUiOperationHandoffTest.Port owner = new LotteryUiOperationHandoffTest.Port();
        String count = "5";

        public void prepare() {
            List<Integer> configured = owner.daily.getDrawCounts();
            int requested = configured == null ? ("all".equals(count) ? -1 : Integer.parseInt(count))
                    : java.util.Collections.min(configured);
            owner.payment.select(owner.pool, requested, configured == null ? 1 : java.util.Collections.min(configured));
        }

        public List<LotteryPool> pools() {
            return owner.pools();
        }

        public String poolId() {
            return owner.poolId();
        }

        public void pool(String id) {
            owner.pool = id;
        }

        public List<String> counts() {
            return Arrays.asList("5", "10", "all");
        }

        public String count() {
            return count;
        }

        public void count(String value) {
            count = value;
            prepare();
        }

        public long generation() {
            return owner.generation();
        }

        public xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel payment() {
            return owner.payment;
        }

        public String name(Reward reward) {
            return owner.name(reward);
        }
    }

    @Test
    public void explicitWhitelistRejectsUnconfiguredCountsAndSingleDraw() {
        LegacyPort port = new LegacyPort();
        port.owner.daily.setDrawCounts(Arrays.asList(7, 10));
        LotteryUiSession session = new LotteryUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        session.tick();
        port.owner.accept();
        session.tick();
        assertFalse(view.enabled.get("single_slot"));
        view.actions.get("single").run();
        assertFalse(view.enabled.get("confirm_panel"));
        view.changes.get("batch_count").accept(UiValue.text("5"));
        assertEquals("5", port.count);
        view.actions.get("batch").run();
        view.actions.get("confirm_draw").run();
        assertEquals(7, port.owner.draws.get(0).getRequest().getCount());
        assertEquals(Arrays.asList(7, 10), port.owner.daily.getDrawCounts());
    }

    @Test
    public void nullLegacyCountsKeepAllRequestAndNeedConfirmation() {
        LegacyPort port = new LegacyPort();
        port.owner.daily.setDrawCounts(null);
        port.count = "all";
        LotteryUiSession session = new LotteryUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals("all", view.values.get("batch_count").asText());
        session.tick();
        port.owner.accept();
        session.tick();
        view.actions.get("batch").run();
        assertTrue(port.owner.draws.isEmpty());
        view.actions.get("confirm_draw").run();
        assertEquals(-1, port.owner.draws.get(0).getRequest().getCount());
        assertNull(port.owner.daily.getDrawCounts());
        assertNull(port.owner.daily.getAnimationStyle());
    }

    @Test
    public void poolListPositionSurvivesReloadNativeHandoffAndPoolSelection() {
        LegacyPort port = new LegacyPort();
        LotteryUiSession session = new LotteryUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        view.state.listPosition("pool_rows", 2.75);
        session.bind(view);
        session.detach(UiDetachReason.SWITCH);
        view.nativeView = true;
        session.bind(view);
        session.detach(UiDetachReason.SWITCH);
        view.nativeView = false;
        session.bind(view);
        assertEquals(2.75, view.state.listPosition("pool_rows"), 0);
        assertEquals(0, view.state.rowPosition(), 0);
    }
}
