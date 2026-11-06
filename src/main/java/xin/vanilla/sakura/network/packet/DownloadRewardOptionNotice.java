package xin.vanilla.sakura.network.packet;

import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.network.SakuraNetwork;

/**
 * 通知服务器将奖励配置文件同步到指定客户端
 */
public class DownloadRewardOptionNotice implements INetworkPacket {
    private static final long INTERVAL = java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    private static final java.util.Map<Object, Long> LAST_REQUEST = new java.util.WeakHashMap<>();

    static synchronized boolean allow(Object connection, long now) {
        if (connection == null) return false;
        Long last = LAST_REQUEST.get(connection);
        if (last != null && now - last < INTERVAL) return false;
        LAST_REQUEST.put(connection, now);
        return true;
    }

    public DownloadRewardOptionNotice() {
    }

    public DownloadRewardOptionNotice(BaniraPacketBuffer buf) {
    }

    public void toBytes(BaniraPacketBuffer buf) {
    }

    public static void handle(DownloadRewardOptionNotice packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.senderAs(ServerPlayer.class);
            if (player != null && !player.hasDisconnected() && allow(player.connection, System.nanoTime())) {
                SakuraNetwork.sendSplitToPlayer(RewardConfigManager.toSyncPacket(player), player);
                SakuraNetwork.sendSplitToPlayer(SakuraNetwork.personalDatePacket(player), player);
                SakuraNetwork.sendSplitToPlayer(SakuraNetwork.lotteryPoolPacket(player), player);
            }
        });
        ctx.markHandled();
    }
}
