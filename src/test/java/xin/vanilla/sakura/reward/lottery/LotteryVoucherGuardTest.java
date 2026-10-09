package xin.vanilla.sakura.reward.lottery;

import org.junit.Test;
import xin.vanilla.sakura.config.reward.*;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.*;

import java.time.ZoneId;
import java.util.*;

import static org.junit.Assert.*;

public class LotteryVoucherGuardTest {
    @Test public void paidPreparedResultCannotUseOldClaimPath() {
        LotteryPool pool = new LotteryPool("paid", "Paid", LotteryLimitPolicy.DAILY,
                1, 0, new RewardList());
        pool.setVoucherExpression("xpPoints(1)");
        LotteryDrawBatchResult prepared = new LotteryDrawBatchResult(LotteryDrawStatus.SUCCESS,
                pool, Collections.singletonList(Reward.getDefault()), 0, 1);
        LotteryDrawBatchResult result = LotteryRewardService.claimPrepared(null, prepared);
        assertFalse(result.isSuccess());
        assertEquals("PAYMENT_UNAVAILABLE", result.getStatus().name());
    }
    @Test public void paidPoolCannotReachUnpaidDrawOrPlayerState() {
        RewardConfig previous = RewardConfigManager.getRewardConfig();
        try {
            RewardConfig config = new RewardConfig();
            LotteryPool pool = new LotteryPool("paid", "Paid", LotteryLimitPolicy.DAILY,
                    1, 0, new RewardList(Collections.singletonList(Reward.getDefault())));
            pool.setVoucherExpression("xpPoints(1)");
            config.getLotteryPools().add(pool);
            RewardConfigManager.setRewardConfig(config);
            LotteryDrawBatchResult result = LotteryRewardService.prepareMany(null, "paid", 1,
                    0, ZoneId.of("UTC"), new Random(1));
            assertFalse(result.isSuccess());
            assertEquals("PAYMENT_UNAVAILABLE", result.getStatus().name());
        } finally {
            RewardConfigManager.setRewardConfig(previous);
        }
    }
}
