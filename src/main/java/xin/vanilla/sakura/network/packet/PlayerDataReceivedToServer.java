package xin.vanilla.sakura.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.function.Supplier;

public class PlayerDataReceivedToServer {

    public PlayerDataReceivedToServer() {
    }

    public PlayerDataReceivedToServer(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buf) {
    }

    public static void handle(PlayerDataReceivedToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                SakuraSignIn.getPlayerDataStatus().put(SakuraUtils.getPlayerUUIDString(player), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
