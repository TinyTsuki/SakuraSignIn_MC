package xin.vanilla.sakura.config.reward;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LotteryRewardConfigCodecTest {
    @Test
    public void roundTripKeepsPoolMetadataAndRewards() throws Exception {
        RewardConfig source = new RewardConfig();
        source.getLotteryPools().add(new LotteryPool("daily", "Daily Draw",
                LotteryLimitPolicy.WEEKLY, 2, 30, LotteryPreviewMode.NONE,
                new RewardList(Collections.singletonList(Reward.getDefault()))));

        RewardConfig restored = new RewardConfigCodec().decode(
                new RewardConfigCodec().encode(source));

        assertEquals(source.getLotteryPools(), restored.getLotteryPools());
    }

    @Test
    public void migratesLegacyLotteryPreviewFlag() throws Exception {
        RewardConfigCodec codec = new RewardConfigCodec();
        RewardConfig source = new RewardConfig();
        source.getLotteryPools().add(new LotteryPool("daily", "Daily Draw",
                LotteryLimitPolicy.DAILY, 1, 0, LotteryPreviewMode.ALL,
                new RewardList(Collections.singletonList(Reward.getDefault()))));
        String encoded = codec.encode(source);
        String visible = encoded.replace("\"previewMode\": \"ALL\"", "\"showRewards\": true");
        String hidden = encoded.replace("\"previewMode\": \"ALL\"", "\"showRewards\": false");

        assertEquals(LotteryPreviewMode.ALL,
                codec.decode(visible).getLotteryPools().get(0).getPreviewMode());
        assertEquals(LotteryPreviewMode.NONE,
                codec.decode(hidden).getLotteryPools().get(0).getPreviewMode());
        assertFalse(codec.encode(codec.decode(visible)).contains("showRewards"));
    }
}
