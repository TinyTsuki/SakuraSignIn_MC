package xin.vanilla.sakura.data.lottery;

import org.junit.Test;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LotteryPoolsTest {
    @Test
    public void hiddenPoolRewardsAreRedactedOnlyForPlayers() {
        LotteryPool pool = new LotteryPool("hidden", "Hidden", LotteryLimitPolicy.DAILY,
                1, 0, false,
                new RewardList(Collections.singletonList(Reward.getDefault())));

        assertTrue(LotteryPools.visibleCopy(Collections.singletonList(pool), false)
                .get(0).getRewards().isEmpty());
        assertEquals(1, LotteryPools.visibleCopy(Collections.singletonList(pool), true)
                .get(0).getRewards().size());
        assertEquals(1, pool.getRewards().size());
    }
}
