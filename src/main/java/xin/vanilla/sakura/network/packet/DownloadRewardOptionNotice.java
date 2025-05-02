package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.network.ModNetworkHandler;

/**
 * 通知服务器将奖励配置文件同步到指定客户端
 */
@Getter
public class DownloadRewardOptionNotice {

    public DownloadRewardOptionNotice() {
    }

    public DownloadRewardOptionNotice(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static void handle(DownloadRewardOptionNotice packet, NetworkEvent.ServerCustomPayloadEvent.Context ctx) {
        // 获取网络事件上下文并排队执行工作
        ctx.enqueueWork(() -> {
            // 获取发送数据包的玩家实体
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // 同步签到奖励配置到客户端
                for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardConfigManager.toSyncPacket(player).split()) {
                    ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), rewardOptionSyncPacket);
                }
            }
        });
        // 设置数据包已处理状态，防止重复处理
        ctx.setPacketHandled(true);
    }
}
