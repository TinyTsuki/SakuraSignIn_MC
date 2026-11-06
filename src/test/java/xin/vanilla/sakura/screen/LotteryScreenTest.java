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
    public void onlyConfiguredModesAppear() {
        LotteryPool pool = new LotteryPool("pool", "Pool", LotteryLimitPolicy.WEEKLY, 100, 0, new RewardList());
        assertEquals(java.util.List.of("1"), LotteryScreen.drawCounts(pool));
        pool.setBatchSize(7);
        assertEquals(java.util.List.of("1", "7"), LotteryScreen.drawCounts(pool));
        pool.setSingleDraw(false);
        assertEquals(java.util.List.of("7"), LotteryScreen.drawCounts(pool));
    }
}
