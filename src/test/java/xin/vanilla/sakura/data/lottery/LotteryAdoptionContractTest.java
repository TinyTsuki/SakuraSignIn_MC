package xin.vanilla.sakura.data.lottery;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class LotteryAdoptionContractTest {
    @Test public void clientLogicalScopeHasExplicitInitializer() throws Exception {
        xin.vanilla.sakura.config.reward.RewardConfigManager.class.getMethod(
                "configureClientScope", java.util.function.BooleanSupplier.class);
    }
    @Test public void poolExposesDescriptionAndVoucherMetadata() {
        try {
            LotteryPool.class.getMethod("getDescription");
            LotteryPool.class.getMethod("getVoucherExpression");
        } catch (NoSuchMethodException missing) { fail("Shared-cost pool metadata is missing"); }
    }
    @Test public void currentProtocolIncludesCorrelatedQuoteDrawAndOutcome() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/sakura/network/SakuraNetwork.java")),StandardCharsets.UTF_8);
        assertTrue("Current lottery protocol missing",source.contains("\"sakura-6\""));
        assertTrue(source.contains("LotteryQuotePacket.class"));
        assertTrue(source.contains("LotteryDrawOutcomePacket.class"));
    }
}
