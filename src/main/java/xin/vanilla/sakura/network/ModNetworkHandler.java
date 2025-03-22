package xin.vanilla.sakura.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fmllegacy.network.NetworkRegistry;
import net.minecraftforge.fmllegacy.network.simple.SimpleChannel;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.network.packet.*;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int ID = 0;
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SakuraSignIn.MODID, "main_network"),
            () -> PROTOCOL_VERSION,
            clientVersion -> true,      // 客户端版本始终有效
            serverVersion -> true       // 服务端版本始终有效
    );

    public static int nextID() {
        return ID++;
    }

    public static void registerPackets() {
        INSTANCE.registerMessage(nextID(), PlayerDataSyncPacket.class, PlayerDataSyncPacket::toBytes, PlayerDataSyncPacket::new, PlayerDataSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), ClientConfigSyncPacket.class, ClientConfigSyncPacket::toBytes, ClientConfigSyncPacket::new, ClientConfigSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), RewardOptionSyncPacket.class, RewardOptionSyncPacket::toBytes, RewardOptionSyncPacket::new, RewardOptionSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), ItemStackPacket.class, ItemStackPacket::toBytes, ItemStackPacket::new, ItemStackPacket::handle);
        INSTANCE.registerMessage(nextID(), SignInPacket.class, SignInPacket::toBytes, SignInPacket::new, SignInPacket::handle);
        INSTANCE.registerMessage(nextID(), AdvancementPacket.class, AdvancementPacket::toBytes, AdvancementPacket::new, AdvancementPacket::handle);
        INSTANCE.registerMessage(nextID(), DownloadRewardOptionNotice.class, DownloadRewardOptionNotice::toBytes, DownloadRewardOptionNotice::new, DownloadRewardOptionNotice::handle);
        INSTANCE.registerMessage(nextID(), PlayerDataReceivedNotice.class, PlayerDataReceivedNotice::toBytes, PlayerDataReceivedNotice::new, PlayerDataReceivedNotice::handle);
        INSTANCE.registerMessage(nextID(), ClientModLoadedNotice.class, ClientModLoadedNotice::toBytes, ClientModLoadedNotice::new, ClientModLoadedNotice::handle);
        INSTANCE.registerMessage(nextID(), ServerTimeSyncPacket.class, ServerTimeSyncPacket::toBytes, ServerTimeSyncPacket::new, ServerTimeSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), RewardOptionDataReceivedNotice.class, RewardOptionDataReceivedNotice::toBytes, RewardOptionDataReceivedNotice::new, RewardOptionDataReceivedNotice::handle);
    }
}
