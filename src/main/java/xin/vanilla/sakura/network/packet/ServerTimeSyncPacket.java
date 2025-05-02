package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;

@Getter
public class ServerTimeSyncPacket implements CustomPacketPayload {
    public final static Type<ServerTimeSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SakuraSignIn.MODID, "server_time_sync"));
    public final static StreamCodec<ByteBuf, ServerTimeSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull ServerTimeSyncPacket decode(@NotNull ByteBuf byteBuf) {
            return new ServerTimeSyncPacket((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull ServerTimeSyncPacket packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    /**
     * 服务器时间
     */
    private final String serverTime;

    public ServerTimeSyncPacket() {
        this.serverTime = DateUtils.toDateTimeString(DateUtils.getServerDate());
    }

    public ServerTimeSyncPacket(FriendlyByteBuf buf) {
        this.serverTime = buf.readUtf();
    }

    public void toBytes(@NonNull FriendlyByteBuf buf) {
        buf.writeUtf(this.serverTime);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerTimeSyncPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            ctx.enqueueWork(() -> SakuraSignIn.getClientServerTime().setKey(DateUtils.toDateTimeString(new Date())).setValue(packet.serverTime));
        }
    }
}
