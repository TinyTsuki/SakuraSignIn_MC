package xin.vanilla.sakura.network;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定 Sakura 网络层的跨加载器边界以及按月同步结构。
 */
public class SakuraNetworkContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void networkPackagesDoNotExposeForgeOrVanillaBuffers() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN.resolve("network"))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = read(path);
                assertFalse(path + " imports PacketBuffer", source.contains("net.minecraft.network.PacketBuffer"));
                assertFalse(path + " imports Forge network", source.contains("net.minecraftforge.fml.network"));
            });
        }
    }

    @Test
    public void oldChannelAndSplitCacheAreRemoved() {
        assertFalse(Files.exists(MAIN.resolve("network/ModNetworkHandler.java")));
        assertFalse(Files.exists(MAIN.resolve("network/packet/SplitPacket.java")));

        String entry = read(MAIN.resolve("SakuraSignIn.java"));
        assertFalse(entry.contains("packetCache"));
        assertFalse(entry.contains("PROTOCOL_VERSION"));
    }

    @Test
    public void playerSynchronizationIsSummaryPlusRequestedMonth() {
        String summary = read(MAIN.resolve("network/packet/PlayerDataSyncPacket.java"));
        String network = read(MAIN.resolve("network/SakuraNetwork.java"));
        String signIn = read(MAIN.resolve("network/packet/SignInPacket.java"));

        assertFalse(summary.contains("signInRecords"));
        assertTrue(Files.exists(MAIN.resolve("network/packet/PlayerMonthRequestPacket.java")));
        assertTrue(Files.exists(MAIN.resolve("network/packet/PlayerMonthSyncPacket.java")));
        assertTrue(network.contains("public static void syncMonth(ServerPlayer player, Date date)"));
        assertTrue(network.contains("monthOf(date)"));
        assertTrue(signIn.contains("SakuraNetwork.syncMonth(player, signInDate)"));
    }

    @Test
    public void networkUsesBaniraRegistrationAndPresence() {
        String network = read(MAIN.resolve("network/SakuraNetwork.java"));
        assertTrue(network.contains("NetworkHandler.create"));
        assertTrue(network.contains("BaniraModPresence.register"));
        assertFalse(network.contains("PROTOCOL_VERSION"));
        assertFalse(network.contains("NetworkRegistry.ABSENT"));
        assertFalse(network.contains("NetworkRegistry.ACCEPTVANILLA"));
    }

    @Test
    public void rewardSyncUsesNamespacedJsonAndServerAddPermissionChecks() {
        String packet = read(MAIN.resolve("network/packet/RewardOptionSyncPacket.java"));

        assertTrue(packet.contains("RewardJsonCodec.decode"));
        assertTrue(packet.contains("RewardJsonCodec.encode"));
        assertTrue(packet.contains("RewardAddPermissionChecker.canApply"));
        assertTrue(packet.contains("RewardRuleAddPermissionChecker.canApply"));
        assertFalse(packet.contains("TypeToken<Reward>"));
        assertFalse(packet.contains("reward.getType().ordinal"));
        assertFalse(packet.contains("ERewardType.valueOf(buf"));
    }

    @Test
    public void personalDateSyncSeparatesServerPresetsFromPlayerSelections() {
        String network = read(MAIN.resolve("network/SakuraNetwork.java"));
        String presets = read(MAIN.resolve(
                "network/packet/PersonalDatePresetSyncPacket.java"));
        String selections = read(MAIN.resolve(
                "network/packet/PersonalDateSlotUpdatePacket.java"));

        assertTrue(network.contains("registerSplit(PersonalDatePresetSyncPacket.class"));
        assertTrue(network.contains("register(PersonalDateSlotUpdatePacket.class"));
        assertTrue(presets.contains("ctx.isClientSide()"));
        assertFalse(selections.contains("writeUtf(slot.getLastClaimedOccurrenceKey())"));
        assertTrue(selections.contains("PersonalDateSelectionService"));
    }

    @Test
    public void initialHandshakeDoesNotCreateAPlayerDataFeedbackLoop() {
        String client = read(MAIN.resolve("network/ClientProxy.java"));
        String clientConfig = read(MAIN.resolve("network/packet/ClientConfigSyncPacket.java"));
        String network = read(MAIN.resolve("network/SakuraNetwork.java"));
        String loaderEvents = read(MAIN.resolve(
                "internal/neoforge/event/NeoForgeSakuraGameEventAdapter.java"));

        assertTrue(client.contains("boolean initialSync = !SakuraClientState.isEnabled()"));
        assertTrue(client.contains("if (initialSync)"));
        assertTrue(clientConfig.contains("SakuraPlayerData.save(player)"));
        assertFalse(clientConfig.contains("SakuraPlayerData.saveAndSync(player)"));
        assertTrue(network.contains("sendToPlayer(new ServerTimeSyncPacket(), player)"));
        assertFalse(loaderEvents.contains("onPlayerTick"));
        assertFalse(loaderEvents.contains("new ServerTimeSyncPacket()"));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
