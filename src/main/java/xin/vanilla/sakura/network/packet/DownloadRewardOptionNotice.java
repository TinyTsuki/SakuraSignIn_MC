package xin.vanilla.sakura.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
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
    public final static Type<DownloadRewardOptionNotice> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SakuraSignIn.MODID, "download_reward_option"));
    public final static StreamCodec<ByteBuf, DownloadRewardOptionNotice> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull DownloadRewardOptionNotice decode(@NotNull ByteBuf byteBuf) {
            return new DownloadRewardOptionNotice((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull DownloadRewardOptionNotice packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    public DownloadRewardOptionNotice() {
    }

    public DownloadRewardOptionNotice(FriendlyByteBuf buf) {
    }

    public void toBytes(@NonNull FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DownloadRewardOptionNotice packet, IPayloadContext ctx) {
        if (ctx.flow().isServerbound()) {
            // 获取网络事件上下文并排队执行工作
            ctx.enqueueWork(() -> {
                // 获取发送数据包的玩家实体
                if (ctx.player() instanceof ServerPlayer player) {
                    // 同步签到奖励配置到客户端
                    for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardConfigManager.toSyncPacket(player).split()) {
                        player.connection.send(rewardOptionSyncPacket);
                    }
                }
            });
        }
    }
}
