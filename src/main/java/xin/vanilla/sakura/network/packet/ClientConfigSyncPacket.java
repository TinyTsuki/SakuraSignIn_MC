package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.PlayerDataAttachment;
import xin.vanilla.sakura.data.PlayerSignInData;

@Getter
public class ClientConfigSyncPacket implements CustomPacketPayload {
    public final static Type<ClientConfigSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SakuraSignIn.MODID, "client_config_sync"));
    public final static StreamCodec<ByteBuf, ClientConfigSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull ClientConfigSyncPacket decode(@NotNull ByteBuf byteBuf) {
            return new ClientConfigSyncPacket((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull ClientConfigSyncPacket packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    /**
     * 自动领取奖励
     */
    private final boolean autoRewarded;

    public ClientConfigSyncPacket() {
        this.autoRewarded = ClientConfig.AUTO_REWARDED.get();
    }

    public ClientConfigSyncPacket(FriendlyByteBuf buf) {
        this.autoRewarded = buf.readBoolean();
    }

    public void toBytes(@NonNull FriendlyByteBuf buf) {
        buf.writeBoolean(this.autoRewarded);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientConfigSyncPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer player) {
                    PlayerSignInData signInData = PlayerDataAttachment.getData(player);
                    signInData.setAutoRewarded(packet.autoRewarded);
                }
            });
        }
    }
}
