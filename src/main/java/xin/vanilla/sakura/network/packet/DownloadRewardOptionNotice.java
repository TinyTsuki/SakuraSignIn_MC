package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.RewardConfigManager;

/**
 * 通知服务器将奖励配置文件同步到指定客户端
 */
@Getter
public class DownloadRewardOptionNotice implements CustomPacketPayload {
    public final static ResourceLocation ID = new ResourceLocation(SakuraSignIn.MODID, "download_reward_option");

    public DownloadRewardOptionNotice() {
    }

    public DownloadRewardOptionNotice(FriendlyByteBuf buf) {
    }

    public void write(@NonNull FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    public static void handle(DownloadRewardOptionNotice packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.workHandler().execute(() -> {
                // 获取发送数据包的玩家实体
                if (ctx.player().isPresent()) {
                    ServerPlayer player = (ServerPlayer) ctx.player().get();
                    // 同步签到奖励配置到客户端
                    for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardConfigManager.toSyncPacket(player).split()) {
                        player.connection.send(rewardOptionSyncPacket);
                    }
                }
            });
        }
    }
}
