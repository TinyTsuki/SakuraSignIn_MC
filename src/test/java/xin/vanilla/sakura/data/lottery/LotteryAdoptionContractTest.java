package xin.vanilla.sakura.data.lottery;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class LotteryAdoptionContractTest {
    @Test
    public void clientLogicalScopeHasExplicitInitializer() throws Exception {
        xin.vanilla.sakura.config.reward.RewardConfigManager.class.getMethod(
                "configureClientScope", java.util.function.BooleanSupplier.class);
    }

    @Test
    public void poolExposesDescriptionAndVoucherMetadata() {
        try {
            LotteryPool.class.getMethod("getDescription");
            LotteryPool.class.getMethod("getVoucherExpression");
        } catch (NoSuchMethodException missing) {
            fail("Shared-cost pool metadata is missing");
        }
    }

    @Test
    public void currentProtocolIncludesCorrelatedQuoteDrawAndOutcome() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/sakura/network/SakuraNetwork.java")), StandardCharsets.UTF_8);
        assertTrue("Current lottery protocol missing", source.contains("\"sakura-9\""));
        assertTrue(source.contains("LotteryQuotePacket.class"));
        assertTrue(source.contains("LotteryDrawOutcomePacket.class"));
    }

    @Test
    public void expandedAnimationOrdinalsRequireProtocolNineAndKeepChannelOptional() throws Exception {
        assertEquals("sakura-9", xin.vanilla.sakura.network.SakuraNetwork.PROTOCOL_VERSION);
        assertNotEquals("sakura-8", xin.vanilla.sakura.network.SakuraNetwork.PROTOCOL_VERSION);
        assertEquals(9, LotteryAnimationStyle.values().length);
        assertEquals(0, LotteryAnimationStyle.STRIP.ordinal());
        assertEquals(1, LotteryAnimationStyle.CARDS.ordinal());
        assertEquals(2, LotteryAnimationStyle.ROULETTE.ordinal());
        assertEquals(3, LotteryAnimationStyle.INSTANT.ordinal());
        assertEquals(4, LotteryAnimationStyle.ENVELOPE.ordinal());
        assertEquals(5, LotteryAnimationStyle.FAN.ordinal());
        assertEquals(6, LotteryAnimationStyle.BOUNCE.ordinal());
        assertEquals(7, LotteryAnimationStyle.CHEST.ordinal());
        assertEquals(8, LotteryAnimationStyle.FLIP.ordinal());
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/network/SakuraNetwork.java")), StandardCharsets.UTF_8);
        // Loader handshake behavior is delegated; this guards the source wiring only.
        String registration = source.replaceAll("\\s+", "");
        assertTrue("Optional client protocol delegation changed", registration.contains(
                "NetworkHandler.create(\"main_network\",BaniraIdentifier.of(SakuraSignIn.MODID,\"main_network\"),PROTOCOL_VERSION,true)"));
    }
}
