package xin.vanilla.sakura.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.NetworkHandler;
import xin.vanilla.banira.common.network.SplitPacket;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.network.packet.*;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

/**
 * Sakura 唯一网络入口；注册、发送与客户端可选握手均由 Banira 承担。
 */
public final class SakuraNetwork {
    private static final NetworkHandler HANDLER = NetworkHandler.create(
            "main_network",
            BaniraIdentifier.of(SakuraSignIn.MODID, "main_network"),
            "sakura-1",
            true
    );
    private static boolean initialized;

    private SakuraNetwork() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        HANDLER.register(PlayerDataSyncPacket.class, PlayerDataSyncPacket::toBytes,
                PlayerDataSyncPacket::new, PlayerDataSyncPacket::handle);
        HANDLER.register(PlayerMonthRequestPacket.class, PlayerMonthRequestPacket::toBytes,
                PlayerMonthRequestPacket::new, PlayerMonthRequestPacket::handle);
        HANDLER.register(PlayerMonthSyncPacket.class, PlayerMonthSyncPacket::toBytes,
                PlayerMonthSyncPacket::new, PlayerMonthSyncPacket::handle);
        HANDLER.register(ClientConfigSyncPacket.class, ClientConfigSyncPacket::toBytes,
                ClientConfigSyncPacket::new, ClientConfigSyncPacket::handle);
        HANDLER.registerSplit(RewardOptionSyncPacket.class, RewardOptionSyncPacket::toBytes,
                RewardOptionSyncPacket::new, RewardOptionSyncPacket::handle);
        HANDLER.register(ItemStackPacket.class, ItemStackPacket::toBytes,
                ItemStackPacket::new, ItemStackPacket::handle);
        HANDLER.register(SignInPacket.class, SignInPacket::toBytes,
                SignInPacket::new, SignInPacket::handle);
        HANDLER.registerSplit(AdvancementPacket.class, AdvancementPacket::toBytes,
                AdvancementPacket::new, AdvancementPacket::handle);
        HANDLER.register(DownloadRewardOptionNotice.class, DownloadRewardOptionNotice::toBytes,
                DownloadRewardOptionNotice::new, DownloadRewardOptionNotice::handle);
        HANDLER.register(ServerTimeSyncPacket.class, ServerTimeSyncPacket::toBytes,
                ServerTimeSyncPacket::new, ServerTimeSyncPacket::handle);
        HANDLER.register(RewardOptionDataReceivedNotice.class,
                RewardOptionDataReceivedNotice::toBytes,
                RewardOptionDataReceivedNotice::new,
                RewardOptionDataReceivedNotice::handle);
        BaniraModPresence.register(SakuraSignIn.MODID, SakuraNetwork::syncInitialData);
        initialized = true;
    }

    public static void sendToServer(INetworkPacket packet) {
        BaniraNetwork.sendToServer(packet);
    }

    public static void sendToPlayer(INetworkPacket packet, Object player) {
        BaniraNetwork.sendToPlayer(packet, player);
    }

    public static void requestMonth(Date date) {
        sendToServer(new PlayerMonthRequestPacket(monthOf(date)));
    }

    /**
     * 签到后同步实际操作月份，保证跨月补签也能立即刷新。
     */
    public static void syncMonth(ServerPlayerEntity player, Date date) {
        IPlayerSignInData data = SakuraPlayerData.get(player);
        sendToPlayer(new PlayerMonthSyncPacket(
                player.getUUID(), monthOf(date), data.getSignInRecords()
        ), player);
    }

    public static <T extends SplitPacket & INetworkPacket> void sendSplitToServer(T packet) {
        for (T part : packet.<T>split()) {
            BaniraNetwork.sendToServer(part);
        }
    }

    public static <T extends SplitPacket & INetworkPacket> void sendSplitToPlayer(
            T packet, Object player
    ) {
        for (T part : packet.<T>split()) {
            BaniraNetwork.sendToPlayer(part, player);
        }
    }

    private static void syncInitialData(Object playerObject) {
        if (!(playerObject instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) playerObject;
        CommonConfig.syncToPlayer(player);
        SakuraPlayerData.sync(player);
        sendToPlayer(new ServerTimeSyncPacket(), player);
        sendSplitToPlayer(RewardConfigManager.toSyncPacket(player), player);
        sendSplitToPlayer(new AdvancementPacket(
                player.server.getAdvancements().getAllAdvancements()
        ), player);
    }

    private static String monthOf(Date date) {
        return YearMonth.from(date.toInstant()
                .atZone(ZoneId.systemDefault())).toString();
    }
}
