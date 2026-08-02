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
                1, 0, LotteryPreviewMode.NONE,
                new RewardList(Collections.singletonList(Reward.getDefault())));

        assertTrue(LotteryPools.visibleCopy(Collections.singletonList(pool), false)
                .get(0).getRewards().isEmpty());
        assertEquals(1, LotteryPools.visibleCopy(Collections.singletonList(pool), true)
                .get(0).getRewards().size());
        assertEquals(1, pool.getRewards().size());
    }

    @Test
    public void probabilityOnlyPoolsKeepWeightsWithoutLeakingRewardIdentity() {
        Reward reward = Reward.getDefault().setProbability(new java.math.BigDecimal("0.25"));
        LotteryPool pool = new LotteryPool("odds", "Odds", LotteryLimitPolicy.DAILY,
                1, 0, LotteryPreviewMode.PROBABILITY_ONLY,
                new RewardList(Collections.singletonList(reward)));

        Reward visible = LotteryPools.visibleCopy(Collections.singletonList(pool), false)
                .get(0).getRewards().get(0);
        assertEquals(new java.math.BigDecimal("0.25"), visible.getProbability());
        assertEquals(Reward.getDefault().getContent(), visible.getContent());
    }

    @Test
    public void itemOnlyPoolsDoNotLeakWeights() {
        Reward reward = Reward.getDefault().setProbability(new java.math.BigDecimal("0.25"));
        LotteryPool pool = new LotteryPool("items", "Items", LotteryLimitPolicy.DAILY,
                1, 0, LotteryPreviewMode.ITEMS_ONLY,
                new RewardList(Collections.singletonList(reward)));

        Reward visible = LotteryPools.visibleCopy(Collections.singletonList(pool), false)
                .get(0).getRewards().get(0);
        assertEquals(java.math.BigDecimal.ONE, visible.getProbability());
        assertEquals(new java.math.BigDecimal("0.25"), reward.getProbability());
    }
}
