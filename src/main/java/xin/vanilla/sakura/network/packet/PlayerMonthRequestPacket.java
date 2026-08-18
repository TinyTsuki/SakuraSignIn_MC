package xin.vanilla.sakura.network.packet;

import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.api.SakuraPlayerData;

/**
 * 客户端仅请求当前需要展示的月份。
 */
public class PlayerMonthRequestPacket implements INetworkPacket {
    private final String month;

    public PlayerMonthRequestPacket(String month) {
        this.month = requireMonth(month);
    }

    public PlayerMonthRequestPacket(BaniraPacketBuffer buffer) {
        this(buffer.readUtf(7));
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUtf(month, 7);
    }

    public static void handle(PlayerMonthRequestPacket packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.senderAs(ServerPlayer.class);
            if (sender != null) {
                BaniraNetwork.sendToPlayer(new PlayerMonthSyncPacket(
                        sender.getUUID(), packet.month, SakuraPlayerData.get(sender).getSignInRecords()
                ), sender);
            }
        });
        ctx.markHandled();
    }

    private static String requireMonth(String value) {
        if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Month must use YYYY-MM: " + value);
        }
        return value;
    }
}
