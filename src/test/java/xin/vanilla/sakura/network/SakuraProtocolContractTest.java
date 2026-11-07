package xin.vanilla.sakura.network;

import net.minecraftforge.fml.network.NetworkRegistry;
import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Predicate;

import static org.junit.Assert.*;

public class SakuraProtocolContractTest {
    @Test
    public void extendedAnimationOrdinalsRequireExactProtocolNine() {
        assertEquals("sakura-9", SakuraNetwork.PROTOCOL_VERSION);
        assertNotEquals("sakura-8", SakuraNetwork.PROTOCOL_VERSION);
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

    @Test
    public void oldModdedProtocolIsRejectedWhileMissingAndVanillaClientsRemainAllowed() {
        Predicate<String> accepted = NetworkRegistry.acceptMissingOr(SakuraNetwork.PROTOCOL_VERSION);
        assertTrue(accepted.test("sakura-9"));
        assertFalse(accepted.test("sakura-8"));
        assertTrue(accepted.test(NetworkRegistry.ABSENT));
        assertTrue(accepted.test(NetworkRegistry.ACCEPTVANILLA));
    }
}
