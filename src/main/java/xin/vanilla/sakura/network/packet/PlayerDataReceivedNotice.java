package xin.vanilla.sakura.network.packet;

import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;

public class PlayerDataReceivedNotice implements CustomPacketPayload {
    public final static ResourceLocation ID = new ResourceLocation(SakuraSignIn.MODID, "player_data_received");

    public PlayerDataReceivedNotice() {
    }

    public PlayerDataReceivedNotice(FriendlyByteBuf buf) {
    }

    public void write(@NonNull FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    public static void handle(PlayerDataReceivedNotice packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.workHandler().execute(() -> {
                // 获取发送数据包的玩家实体
                if (ctx.player().isPresent()) {
                    ServerPlayer player = (ServerPlayer) ctx.player().get();
                    SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), true);
                }
            });
        }
    }
}
