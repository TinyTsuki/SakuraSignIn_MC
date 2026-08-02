package xin.vanilla.sakura.data.player;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;

import static org.junit.Assert.assertEquals;

public class LotteryDrawStatePersistenceTest {
    @Test
    public void summaryRoundTripKeepsLotteryCounters() {
        PlayerSignInSummary summary = new PlayerSignInSummary();
        LotteryDrawState state = new LotteryDrawState("daily");
        state.setPeriodKey("2026-08-02");
        state.setPeriodDraws(2);
        state.setTotalDraws(7);
        state.setLastDrawEpochMillis(123456L);
        summary.getLotteryDrawStates().add(state);

        PlayerSignInSummary restored = PlayerSignInSummary.deserializeNBT(summary.serializeNBT());

        assertEquals(summary.getLotteryDrawStates(), restored.getLotteryDrawStates());
    }
}
