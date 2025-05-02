package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;

public class PlayerDataReceivedNotice implements CustomPacketPayload {
    public final static Type<PlayerDataReceivedNotice> TYPE = new Type<>(new ResourceLocation(SakuraSignIn.MODID, "player_data_received"));
    public final static StreamCodec<ByteBuf, PlayerDataReceivedNotice> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull PlayerDataReceivedNotice decode(@NotNull ByteBuf byteBuf) {
            return new PlayerDataReceivedNotice((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull PlayerDataReceivedNotice packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    public PlayerDataReceivedNotice() {
    }

    public PlayerDataReceivedNotice(FriendlyByteBuf buf) {
    }

    public void toBytes(@NonNull FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerDataReceivedNotice packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.enqueueWork(() -> {
                // 获取发送数据包的玩家实体
                if (ctx.player() instanceof ServerPlayer player) {
                    SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), true);
                }
            });
        }
    }
}
