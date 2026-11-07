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
    public void exactProtocolRejectsSakura8PeersAndRetainsOptionalClients() throws Exception {
        assertEquals("sakura-9", xin.vanilla.sakura.network.SakuraNetwork.PROTOCOL_VERSION);
        java.util.function.Predicate<String> exactVersion = xin.vanilla.sakura.network.SakuraNetwork.PROTOCOL_VERSION::equals;
        assertTrue(exactVersion.test("sakura-9"));
        assertFalse(exactVersion.test("sakura-8"));
        assertFalse(exactVersion.test("sakura-9-extra"));
        assertFalse(exactVersion.test(null));
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/sakura/network/SakuraNetwork.java")), StandardCharsets.UTF_8);
        assertTrue("Keep the exact marker and optional-client flag on the registered channel",
                source.matches("(?s).*NetworkHandler\\.create\\(\\s*\"main_network\",.*?PROTOCOL_VERSION,\\s*true\\s*\\).*"));
        assertTrue("Unmodded-client sync remains guarded", source.contains("BaniraModPresence.isRemoteClientInstalled(player, SakuraSignIn.MODID)"));
    }
}
