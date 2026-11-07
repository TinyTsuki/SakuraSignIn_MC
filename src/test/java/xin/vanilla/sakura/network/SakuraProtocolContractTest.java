package xin.vanilla.sakura.network;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SakuraProtocolContractTest {
    @Test
    public void extendedAnimationOrdinalsRequireExactProtocolNineAndExcludeLegacyEight() {
        assertEquals("sakura-9", SakuraNetwork.PROTOCOL_VERSION);
        assertFalse(SakuraNetwork.PROTOCOL_VERSION.equals("sakura-8"));
        assertArrayEquals(new LotteryAnimationStyle[]{LotteryAnimationStyle.STRIP, LotteryAnimationStyle.CARDS,
                LotteryAnimationStyle.ROULETTE, LotteryAnimationStyle.INSTANT, LotteryAnimationStyle.ENVELOPE,
                LotteryAnimationStyle.FAN, LotteryAnimationStyle.BOUNCE, LotteryAnimationStyle.CHEST,
                LotteryAnimationStyle.FLIP}, LotteryAnimationStyle.values());
    }

    @Test
    public void versionedChannelRegistrationKeepsTheClientOptional() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/network/SakuraNetwork.java")), StandardCharsets.UTF_8);
        assertTrue(source.replaceAll("\\s+", "").contains(
                "NetworkHandler.create(\"main_network\",BaniraIdentifier.of(SakuraSignIn.MODID,\"main_network\"),PROTOCOL_VERSION,true)"));
        assertFalse(source.contains("\"sakura-8\""));
    }
}
