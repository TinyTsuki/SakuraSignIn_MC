package xin.vanilla.sakura.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.network.packet.*;

public class ModNetworkHandler {
    public static void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SakuraSignIn.MODID).optional();

        registrar.playToClient(PlayerDataSyncPacket.TYPE, PlayerDataSyncPacket.STREAM_CODEC,
                PlayerDataSyncPacket::handle);
        registrar.playToServer(ClientConfigSyncPacket.TYPE, ClientConfigSyncPacket.STREAM_CODEC,
                ClientConfigSyncPacket::handle);
        registrar.playBidirectional(RewardOptionSyncPacket.TYPE, RewardOptionSyncPacket.STREAM_CODEC,
                RewardOptionSyncPacket::handle);
        registrar.playToServer(ItemStackPacket.TYPE, ItemStackPacket.STREAM_CODEC,
                ItemStackPacket::handle);
        registrar.playToServer(SignInPacket.TYPE, SignInPacket.STREAM_CODEC,
                SignInPacket::handle);
        registrar.playToClient(AdvancementPacket.TYPE, AdvancementPacket.STREAM_CODEC,
                AdvancementPacket::handle);
        registrar.playToServer(DownloadRewardOptionNotice.TYPE, DownloadRewardOptionNotice.STREAM_CODEC,
                DownloadRewardOptionNotice::handle);
        registrar.playToServer(PlayerDataReceivedNotice.TYPE, PlayerDataReceivedNotice.STREAM_CODEC,
                PlayerDataReceivedNotice::handle);
        registrar.playToServer(ClientModLoadedNotice.TYPE, ClientModLoadedNotice.STREAM_CODEC,
                ClientModLoadedNotice::handle);
        registrar.playToClient(ServerTimeSyncPacket.TYPE, ServerTimeSyncPacket.STREAM_CODEC,
                ServerTimeSyncPacket::handle);
        registrar.playToClient(RewardOptionDataReceivedNotice.TYPE, RewardOptionDataReceivedNotice.STREAM_CODEC,
                RewardOptionDataReceivedNotice::handle);
    }
}
