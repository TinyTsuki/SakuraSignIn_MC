package xin.vanilla.sakura.screen;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LotteryScreenTest {
    @Test
    public void finitePoolOffersPresetCountsExactMaximumAndAll() {
        LotteryPool pool = new LotteryPool("pool", "Pool", LotteryLimitPolicy.WEEKLY,
                12, 0, new RewardList());

        assertEquals(Arrays.asList("1", "5", "10", "12", "all"),
                LotteryScreen.drawCounts(pool));
    }

    @Test
    public void unlimitedPoolDoesNotPretendToHaveACompleteAllDraw() {
        LotteryPool pool = new LotteryPool("pool", "Pool", LotteryLimitPolicy.UNLIMITED,
                1, 0, new RewardList());

        assertFalse(LotteryScreen.drawCounts(pool).contains("all"));
        assertEquals("100", LotteryScreen.drawCounts(pool)
                .get(LotteryScreen.drawCounts(pool).size() - 1));
    }
}
