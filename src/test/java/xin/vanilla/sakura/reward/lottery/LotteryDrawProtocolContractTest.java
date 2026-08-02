package xin.vanilla.sakura.reward.lottery;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** 带动画的客户端必须在揭晓后确认，不能在点击时提前发奖。 */
public class LotteryDrawProtocolContractTest {
    @Test
    public void animatedDispatcherPreparesAndClaimPacketGrants() throws Exception {
        String root = "src/main/java/xin/vanilla/sakura/";
        String dispatcher = source(root + "reward/lottery/LotteryDrawDispatcher.java");
        String pending = source(root + "reward/lottery/PendingLotteryDraws.java");
        String screen = source(root + "screen/LotteryRevealScreen.java");

        assertTrue(dispatcher.contains("LotteryRewardService.prepareMany"));
        assertTrue(dispatcher.contains("PendingLotteryDraws.put"));
        assertTrue(pending.contains("LotteryRewardService.claimPrepared"));
        assertTrue(screen.contains("new LotteryClaimPacket(packet.getToken())"));
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
