package xin.vanilla.sakura.network;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;
import java.util.function.Supplier;

@Getter
public class ServerTimeSyncPacket {
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

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.serverTime);
    }

    public static void handle(ServerTimeSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> SakuraSignIn.getClientServerTime().setKey(DateUtils.toDateTimeString(new Date())).setValue(packet.serverTime));
        ctx.get().setPacketHandled(true);
    }
}
