package xin.vanilla.sakura.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.function.Supplier;

public class ClientLoadedToServer {

    public ClientLoadedToServer() {
    }

    public ClientLoadedToServer(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buf) {
    }

    public static void handle(ClientLoadedToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                SakuraSignIn.getPlayerDataStatus().put(SakuraUtils.getPlayerUUIDString(player), false);
                // 同步玩家数据到客户端
                PlayerSignInDataCapability.syncPlayerData(player);
                // 同步进度列表到客户端
                for (AdvancementToClient advancementToClient : new AdvancementToClient((player).server.getAdvancements().getAllAdvancements()).split()) {
                    SakuraUtils.sendPacketToPlayer(advancementToClient, player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
