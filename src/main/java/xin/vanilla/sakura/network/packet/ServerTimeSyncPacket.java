package xin.vanilla.sakura.network.packet;

import xin.vanilla.sakura.data.time.SakuraClock;

import lombok.Getter;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.Date;

@Getter
public class ServerTimeSyncPacket implements INetworkPacket {
    /**
     * 服务器时间
     */
    private final String serverTime;

    public ServerTimeSyncPacket() {
        this.serverTime = DateUtils.toDateTimeString(SakuraClock.serverNow());
    }

    public ServerTimeSyncPacket(BaniraPacketBuffer buf) {
        this.serverTime = buf.readUtf();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeUtf(this.serverTime);
    }

    public static void handle(ServerTimeSyncPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> SakuraClientState.getClientServerTime()
                .key(DateUtils.toDateTimeString(new Date())).value(packet.serverTime));
        ctx.markHandled();
    }
}
