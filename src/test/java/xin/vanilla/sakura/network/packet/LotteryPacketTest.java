package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class LotteryPacketTest {
    @Test
    public void poolPacketKeepsMetadataAndRewards() {
        LotteryPool pool = new LotteryPool("weekly", "Weekly", LotteryLimitPolicy.COOLDOWN,
                1, 600, new RewardList(Collections.singletonList(Reward.getDefault())));
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();

        new LotteryPoolSyncPacket(Collections.singletonList(pool)).toBytes(buffer);
        LotteryPoolSyncPacket restored = new LotteryPoolSyncPacket(buffer);

        assertEquals(Collections.singletonList(pool), restored.getPools());
    }

    @Test
    public void revealPacketKeepsWinnerAndPreview() {
        Reward winner = Reward.getDefault();
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();

        new LotteryRevealPacket("Pool", winner, Arrays.asList(winner, winner)).toBytes(buffer);
        LotteryRevealPacket restored = new LotteryRevealPacket(buffer);

        assertEquals("Pool", restored.getPoolName());
        assertEquals(winner, restored.getWinner());
        assertEquals(2, restored.getPreview().size());
    }
}
