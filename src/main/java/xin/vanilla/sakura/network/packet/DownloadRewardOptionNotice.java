package xin.vanilla.sakura.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.network.SakuraNetwork;

/**
 * 通知服务器将奖励配置文件同步到指定客户端
 */
public class DownloadRewardOptionNotice implements INetworkPacket {

    public DownloadRewardOptionNotice() {
    }

    public DownloadRewardOptionNotice(BaniraPacketBuffer buf) {
    }

    public void toBytes(BaniraPacketBuffer buf) {
    }

    public static void handle(DownloadRewardOptionNotice packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayerEntity player = ctx.senderAs(ServerPlayerEntity.class);
            if (player != null) {
                SakuraNetwork.sendSplitToPlayer(RewardConfigManager.toSyncPacket(player), player);
            }
        });
        ctx.markHandled();
    }
}
