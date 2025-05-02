package xin.vanilla.sakura.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.network.packet.*;

public class ModNetworkHandler {
    public static void registerPackets(final RegisterPayloadHandlerEvent event) {
        final IPayloadRegistrar registrar = event.registrar(SakuraSignIn.MODID).optional();

        registrar.play(PlayerDataSyncPacket.ID, PlayerDataSyncPacket::new,
                handler -> handler.client(PlayerDataSyncPacket::handle));
        registrar.play(ClientConfigSyncPacket.ID, ClientConfigSyncPacket::new,
                handler -> handler.server(ClientConfigSyncPacket::handle));
        registrar.play(RewardOptionSyncPacket.ID, RewardOptionSyncPacket::new,
                handler -> handler.server(RewardOptionSyncPacket::handle).client(RewardOptionSyncPacket::handle));
        registrar.play(ItemStackPacket.ID, ItemStackPacket::new,
                handler -> handler.server(ItemStackPacket::handle));
        registrar.play(SignInPacket.ID, SignInPacket::new,
                handler -> handler.server(SignInPacket::handle));
        registrar.play(AdvancementPacket.ID, AdvancementPacket::new,
                handler -> handler.client(AdvancementPacket::handle));
        registrar.play(DownloadRewardOptionNotice.ID, DownloadRewardOptionNotice::new,
                handler -> handler.server(DownloadRewardOptionNotice::handle));
        registrar.play(PlayerDataReceivedNotice.ID, PlayerDataReceivedNotice::new,
                handler -> handler.server(PlayerDataReceivedNotice::handle));
        registrar.play(ClientModLoadedNotice.ID, ClientModLoadedNotice::new,
                handler -> handler.server(ClientModLoadedNotice::handle));
        registrar.play(ServerTimeSyncPacket.ID, ServerTimeSyncPacket::new,
                handler -> handler.client(ServerTimeSyncPacket::handle));
        registrar.play(RewardOptionDataReceivedNotice.ID, RewardOptionDataReceivedNotice::new,
                handler -> handler.client(RewardOptionDataReceivedNotice::handle));
    }
}
