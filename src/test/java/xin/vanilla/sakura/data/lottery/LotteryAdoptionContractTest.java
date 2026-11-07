package xin.vanilla.sakura.data.lottery;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class LotteryAdoptionContractTest {
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
        assertTrue("Current lottery protocol missing", source.contains("\"sakura-10\""));
        assertTrue(source.contains("LotteryQuotePacket.class"));
        assertTrue(source.contains("LotteryDrawOutcomePacket.class"));
    }

    @Test
    public void extendedAnimationOrdinalsKeepOptionalChannelAndRequireTheNewProtocol() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/sakura/network/SakuraNetwork.java")), StandardCharsets.UTF_8);
        assertTrue(source.matches("(?s).*PROTOCOL_VERSION,\\s*true\\s*\\).*"));
        assertFalse(source.contains("\"sakura-8\""));
        assertFalse(source.contains("\"sakura-9\""));
        assertEquals(0, LotteryAnimationStyle.STRIP.ordinal());
        assertEquals(1, LotteryAnimationStyle.CARDS.ordinal());
        assertEquals(2, LotteryAnimationStyle.ROULETTE.ordinal());
        assertEquals(3, LotteryAnimationStyle.INSTANT.ordinal());
        assertEquals(7, LotteryAnimationStyle.CHEST.ordinal());
        assertEquals(8, LotteryAnimationStyle.FLIP.ordinal());
        String reveal = new String(Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/sakura/network/packet/LotteryRevealPacket.java")), StandardCharsets.UTF_8);
        assertTrue(reveal.contains("animationStyle = buffer.readBoolean()"));
        assertTrue(reveal.contains("buffer.writeBoolean(animationStyle != null)"));
    }
}
