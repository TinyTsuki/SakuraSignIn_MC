package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LotteryPacketTest {
    private static final java.util.UUID SESSION = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String TOKEN = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    @Test
    public void poolPacketKeepsMetadataAndRewards() {
        LotteryPool pool = new LotteryPool("weekly", "Weekly", LotteryLimitPolicy.COOLDOWN,
                1, 600, new RewardList(Collections.singletonList(Reward.getDefault())));
        pool.setDescription("Weekly\nDraw");
        pool.setVoucherExpression("item(\"minecraft:paper\",2)||xpPoints(10)");
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();

        new LotteryPoolSyncPacket(Collections.singletonList(pool), 7).toBytes(buffer);
        LotteryPoolSyncPacket restored = new LotteryPoolSyncPacket(buffer);

        assertEquals(Collections.singletonList(pool), restored.getPools());
    }

    @Test
    public void splitMergePreservesEveryMetadataField() {
        java.util.List<LotteryPool> pools = new java.util.ArrayList<>();
        for (int i = 0; i < 17; i++) {
            LotteryPool pool = new LotteryPool("pool_" + i, "Pool " + i,
                    LotteryLimitPolicy.DAILY, 1, 0, new RewardList());
            pool.setDescription("Description\n" + i);
            pool.setVoucherExpression("xpPoints(" + (i + 1) + ")");
            pools.add(pool);
        }
        LotteryPoolSyncPacket packet = new LotteryPoolSyncPacket(pools, 7);
        java.util.List<LotteryPoolSyncPacket> restored = new java.util.ArrayList<>();
        for (LotteryPoolSyncPacket part : packet.splitPacket()) {
            TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
            part.toBytes(buffer);
            restored.add(new LotteryPoolSyncPacket(buffer));
        }
        assertEquals(pools, packet.mergePackets(restored).getPools());
    }

    @Test
    public void rejectsOversizedAndDuplicatePoolUpdates() {
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY,
                1, 0, new RewardList());
        try {
            new LotteryPoolSyncPacket(Collections.nCopies(129, pool), 7);
            fail("Oversized update accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            new LotteryPoolSyncPacket(Arrays.asList(pool, pool), 7);
            fail("Duplicate update accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void rejectsInvalidVoucherBeforeSending() {
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY,
                1, 0, new RewardList());
        pool.setVoucherExpression("System.exit(0)");
        try {
            new LotteryPoolSyncPacket(Collections.singletonList(pool), 7);
            fail("Invalid fee accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void revealPacketKeepsWinnerAndPreview() {
        Reward winner = Reward.getDefault();
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();

        new LotteryRevealPacket(SESSION + "-1", TOKEN, "Pool", Arrays.asList(winner, winner),
                Arrays.asList(winner, winner), LotteryPreviewMode.ALL).toBytes(buffer);
        LotteryRevealPacket restored = new LotteryRevealPacket(buffer);

        assertEquals("Pool", restored.getPoolName());
        assertEquals(TOKEN, restored.getToken());
        assertEquals(SESSION + "-1", restored.getOperationId());
        assertEquals(2, restored.getWinners().size());
        assertEquals(2, restored.getPreview().size());
        assertEquals(true, restored.isPreviewVisible());
    }

    @Test
    public void drawRequestKeepsPoolAndBatchCount() {
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
        new LotteryDrawRequestPacket(SESSION, 1, 7, "daily", 15, TOKEN).toBytes(buffer);

        LotteryDrawRequestPacket restored = new LotteryDrawRequestPacket(buffer);
        assertEquals("daily", restored.getRequest().getRequest().getPoolId());
        assertEquals(15, restored.getRequest().getRequest().getCount());
        assertEquals(7, restored.getRequest().getRequest().getGeneration());
        assertEquals(SESSION, restored.getRequest().getRequest().getSession());
        assertEquals(TOKEN, restored.getRequest().getQuoteToken());
    }

    @Test
    public void claimPacketKeepsPendingToken() {
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
        new LotteryClaimPacket(SESSION + "-1", TOKEN).toBytes(buffer);

        LotteryClaimPacket restored = new LotteryClaimPacket(buffer);
        assertEquals(TOKEN, restored.getToken());
        assertEquals(SESSION + "-1", restored.getOperationId());
    }
}
