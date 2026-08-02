package xin.vanilla.sakura.config.reward;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class LotteryRewardConfigCodecTest {
    @Test
    public void roundTripKeepsPoolMetadataAndRewards() throws Exception {
        RewardConfig source = new RewardConfig();
        source.getLotteryPools().add(new LotteryPool("daily", "Daily Draw",
                LotteryLimitPolicy.WEEKLY, 2, 30, false,
                new RewardList(Collections.singletonList(Reward.getDefault()))));

        RewardConfig restored = new RewardConfigCodec().decode(
                new RewardConfigCodec().encode(source));

        assertEquals(source.getLotteryPools(), restored.getLotteryPools());
    }
}
