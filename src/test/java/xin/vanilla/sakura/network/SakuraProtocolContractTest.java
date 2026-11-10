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
    public void extendedNullableAnimationOrdinalsRequireExactProtocolTen() {
        assertEquals("sakura-10", SakuraNetwork.PROTOCOL_VERSION);
        assertNotEquals("sakura-8", SakuraNetwork.PROTOCOL_VERSION);
        assertNotEquals("sakura-9", SakuraNetwork.PROTOCOL_VERSION);
        assertArrayEquals(new LotteryAnimationStyle[]{LotteryAnimationStyle.STRIP, LotteryAnimationStyle.CARDS,
                LotteryAnimationStyle.ROULETTE, LotteryAnimationStyle.INSTANT, LotteryAnimationStyle.ENVELOPE,
                LotteryAnimationStyle.FAN, LotteryAnimationStyle.BOUNCE, LotteryAnimationStyle.CHEST,
                LotteryAnimationStyle.FLIP}, LotteryAnimationStyle.values());
    }


    @Test
    public void oldModdedProtocolsAreRejectedWhileMissingAndVanillaClientsRemainAllowed() {
        Predicate<String> accepted = NetworkRegistry.acceptMissingOr(SakuraNetwork.PROTOCOL_VERSION);
        assertTrue(accepted.test("sakura-10"));
        assertFalse(accepted.test("sakura-8"));
        assertFalse(accepted.test("sakura-9"));
        assertTrue(accepted.test(NetworkRegistry.ABSENT));
        assertTrue(accepted.test(NetworkRegistry.ACCEPTVANILLA));
    }


}
