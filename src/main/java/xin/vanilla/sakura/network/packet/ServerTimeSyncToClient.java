package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;
import java.util.function.Supplier;

@Getter
public class ServerTimeSyncToClient {
    /**
     * 服务器时间
     */
    private final String serverTime;

    public ServerTimeSyncToClient() {
        this.serverTime = DateUtils.toDateTimeString(DateUtils.getServerDate());
    }

    public ServerTimeSyncToClient(PacketBuffer buf) {
        this.serverTime = buf.readUtf();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeUtf(this.serverTime);
    }

    public static void handle(ServerTimeSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> SakuraSignIn.getClientServerTime().setKey(DateUtils.toDateTimeString(new Date())).setValue(packet.serverTime));
        ctx.get().setPacketHandled(true);
    }
}
