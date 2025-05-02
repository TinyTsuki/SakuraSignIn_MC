package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;

@Getter
public class ServerTimeSyncPacket implements CustomPacketPayload {
    public final static ResourceLocation ID = new ResourceLocation(SakuraSignIn.MODID, "server_time_sync");

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

    public void write(@NonNull FriendlyByteBuf buf) {
        buf.writeUtf(this.serverTime);
    }

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    public static void handle(ServerTimeSyncPacket packet, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            ctx.workHandler().execute(() -> SakuraSignIn.getClientServerTime().setKey(DateUtils.toDateTimeString(new Date())).setValue(packet.serverTime));
        }
    }
}
