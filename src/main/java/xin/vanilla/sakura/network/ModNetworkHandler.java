package xin.vanilla.sakura.network;

import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.network.packet.*;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int ID = 0;
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            SakuraSignIn.createResource("main_network"),
            () -> PROTOCOL_VERSION,
            clientVersion -> true,      // 客户端版本始终有效
            serverVersion -> true       // 服务端版本始终有效
    );

    public static int nextID() {
        return ID++;
    }

    public static void registerPackets() {
        INSTANCE.registerMessage(nextID(), PlayerDataSyncToBoth.class, PlayerDataSyncToBoth::toBytes, PlayerDataSyncToBoth::new, PlayerDataSyncToBoth::handle);
        INSTANCE.registerMessage(nextID(), RewardOptionSyncToBoth.class, RewardOptionSyncToBoth::toBytes, RewardOptionSyncToBoth::new, RewardOptionSyncToBoth::handle);

        INSTANCE.registerMessage(nextID(), ClientConfigToServer.class, ClientConfigToServer::toBytes, ClientConfigToServer::new, ClientConfigToServer::handle);
        INSTANCE.registerMessage(nextID(), SignInToServer.class, SignInToServer::toBytes, SignInToServer::new, SignInToServer::handle);
        INSTANCE.registerMessage(nextID(), RewardOptionRequestToServer.class, RewardOptionRequestToServer::toBytes, RewardOptionRequestToServer::new, RewardOptionRequestToServer::handle);
        INSTANCE.registerMessage(nextID(), PlayerDataReceivedToServer.class, PlayerDataReceivedToServer::toBytes, PlayerDataReceivedToServer::new, PlayerDataReceivedToServer::handle);
        INSTANCE.registerMessage(nextID(), ClientLoadedToServer.class, ClientLoadedToServer::toBytes, ClientLoadedToServer::new, ClientLoadedToServer::handle);
        INSTANCE.registerMessage(nextID(), RewardCellRequestToServer.class, RewardCellRequestToServer::toBytes, RewardCellRequestToServer::new, RewardCellRequestToServer::handle);

        INSTANCE.registerMessage(nextID(), AdvancementToClient.class, AdvancementToClient::toBytes, AdvancementToClient::new, AdvancementToClient::handle);
        INSTANCE.registerMessage(nextID(), ServerTimeSyncToClient.class, ServerTimeSyncToClient::toBytes, ServerTimeSyncToClient::new, ServerTimeSyncToClient::handle);
        INSTANCE.registerMessage(nextID(), RewardOptionReceivedToClient.class, RewardOptionReceivedToClient::toBytes, RewardOptionReceivedToClient::new, RewardOptionReceivedToClient::handle);
        INSTANCE.registerMessage(nextID(), RewardCellSyncToClient.class, RewardCellSyncToClient::toBytes, RewardCellSyncToClient::new, RewardCellSyncToClient::handle);
        INSTANCE.registerMessage(nextID(), RewardCellDirtiedToClient.class, RewardCellDirtiedToClient::toBytes, RewardCellDirtiedToClient::new, RewardCellDirtiedToClient::handle);
    }
}
