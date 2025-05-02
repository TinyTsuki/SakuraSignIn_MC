package xin.vanilla.sakura.network;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.simple.SimpleChannel;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.network.packet.*;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int ID = 0;
    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder.named(new ResourceLocation(SakuraSignIn.MODID, "main_network"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions((version) -> true)    // 客户端版本始终有效
            .serverAcceptedVersions((version) -> true)    // 服务端版本始终有效
            .simpleChannel();

    public static int nextID() {
        return ID++;
    }

    public static void registerPackets() {
        INSTANCE.messageBuilder(PlayerDataSyncPacket.class, nextID()).encoder(PlayerDataSyncPacket::toBytes).decoder(PlayerDataSyncPacket::new).consumerMainThread(PlayerDataSyncPacket::handle).add();
        INSTANCE.messageBuilder(ClientConfigSyncPacket.class, nextID()).encoder(ClientConfigSyncPacket::toBytes).decoder(ClientConfigSyncPacket::new).consumerMainThread(ClientConfigSyncPacket::handle).add();
        INSTANCE.messageBuilder(RewardOptionSyncPacket.class, nextID()).encoder(RewardOptionSyncPacket::toBytes).decoder(RewardOptionSyncPacket::new).consumerMainThread(RewardOptionSyncPacket::handle).add();
        INSTANCE.messageBuilder(ItemStackPacket.class, nextID()).encoder(ItemStackPacket::toBytes).decoder(ItemStackPacket::new).consumerMainThread(ItemStackPacket::handle).add();
        INSTANCE.messageBuilder(SignInPacket.class, nextID()).encoder(SignInPacket::toBytes).decoder(SignInPacket::new).consumerMainThread(SignInPacket::handle).add();
        INSTANCE.messageBuilder(AdvancementPacket.class, nextID()).encoder(AdvancementPacket::toBytes).decoder(AdvancementPacket::new).consumerMainThread(AdvancementPacket::handle).add();
        INSTANCE.messageBuilder(DownloadRewardOptionNotice.class, nextID()).encoder(DownloadRewardOptionNotice::toBytes).decoder(DownloadRewardOptionNotice::new).consumerMainThread(DownloadRewardOptionNotice::handle).add();
        INSTANCE.messageBuilder(PlayerDataReceivedNotice.class, nextID()).encoder(PlayerDataReceivedNotice::toBytes).decoder(PlayerDataReceivedNotice::new).consumerMainThread(PlayerDataReceivedNotice::handle).add();
        INSTANCE.messageBuilder(ClientModLoadedNotice.class, nextID()).encoder(ClientModLoadedNotice::toBytes).decoder(ClientModLoadedNotice::new).consumerMainThread(ClientModLoadedNotice::handle).add();
        INSTANCE.messageBuilder(ServerTimeSyncPacket.class, nextID()).encoder(ServerTimeSyncPacket::toBytes).decoder(ServerTimeSyncPacket::new).consumerMainThread(ServerTimeSyncPacket::handle).add();
        INSTANCE.messageBuilder(RewardOptionDataReceivedNotice.class, nextID()).encoder(RewardOptionDataReceivedNotice::toBytes).decoder(RewardOptionDataReceivedNotice::new).consumerMainThread(RewardOptionDataReceivedNotice::handle).add();
    }
}
