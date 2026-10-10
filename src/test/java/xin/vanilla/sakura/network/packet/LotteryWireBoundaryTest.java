package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.*;

import java.util.Collections;

import static org.junit.Assert.*;

public class LotteryWireBoundaryTest {
    private static final java.util.UUID SESSION = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String TOKEN = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    @Test public void drawRejectsInvalidPoolAndCountAtConstruction() {
        for (int count : new int[]{Integer.MIN_VALUE, -2, 0, 101, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new LotteryDrawRequestPacket(SESSION, 1, 0, "daily", count, TOKEN));
        }
        assertThrows(IllegalArgumentException.class, () -> new LotteryDrawRequestPacket(SESSION, 1, 0, "../daily", 1, TOKEN));
    }

    @Test public void claimRejectsOversizedToken() {
        assertThrows(IllegalArgumentException.class, () -> new LotteryClaimPacket(SESSION + "-1", repeat('a', 65)));
    }

    @Test public void revealDoesNotInventOrTruncateWinners() {
        assertThrows(IllegalArgumentException.class, () -> new LotteryRevealPacket(SESSION + "-1", TOKEN, "Daily",
                Collections.emptyList(), Collections.emptyList(), LotteryPreviewMode.ALL));
        assertThrows(IllegalArgumentException.class, () -> new LotteryRevealPacket(SESSION + "-1", TOKEN, "Daily",
                Collections.nCopies(101, Reward.getDefault()), Collections.emptyList(), LotteryPreviewMode.ALL));
    }

    @Test public void oversizedLotteryDocumentIsRejectedBeforeSplitting() {
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY,
                1, 0, new RewardList());
        com.google.gson.JsonObject content = new com.google.gson.JsonObject();
        content.addProperty("text", repeat('x', 70_000));
        pool.getRewards().add(new Reward(content,
                xin.vanilla.sakura.api.reward.SakuraRewardTypes.MESSAGE));
        assertThrows(IllegalArgumentException.class, () ->
                new LotteryPoolSyncPacket(Collections.singletonList(pool), 0));
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count]; java.util.Arrays.fill(chars, value); return new String(chars);
    }
}
