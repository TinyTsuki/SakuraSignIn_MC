package xin.vanilla.mc.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.mc.SakuraSignIn;

import java.util.function.Supplier;

public class PlayerDataReceivedNotice {

    public PlayerDataReceivedNotice() {
    }

    public PlayerDataReceivedNotice(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buf) {
    }

    public static void handle(PlayerDataReceivedNotice packet, Supplier<NetworkEvent.Context> ctx) {
        // 获取网络事件上下文并排队执行工作
        ctx.get().enqueueWork(() -> {
            // 获取发送数据包的玩家实体
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), true);
            }
        });
        // 设置数据包已处理状态，防止重复处理
        ctx.get().setPacketHandled(true);
    }
}
