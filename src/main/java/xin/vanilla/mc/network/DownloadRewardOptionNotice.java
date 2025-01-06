package xin.vanilla.mc.network;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;
import xin.vanilla.mc.config.RewardOptionDataManager;

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

    public static void handle(DownloadRewardOptionNotice packet, CustomPayloadEvent.Context ctx) {
        // 获取网络事件上下文并排队执行工作
        ctx.enqueueWork(() -> {
            // 获取发送数据包的玩家实体
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // 同步签到奖励配置到客户端
                for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket(player.hasPermissions(3)).split()) {
                    ModNetworkHandler.INSTANCE.send(rewardOptionSyncPacket, PacketDistributor.PLAYER.with(player));
                }
            }
        });
        // 设置数据包已处理状态，防止重复处理
        ctx.setPacketHandled(true);
    }
}
