package xin.vanilla.sakura.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.api.INetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.lottery.LotteryDrawDispatcher;
import xin.vanilla.sakura.reward.lottery.LotteryRewardService;
import xin.vanilla.sakura.util.SakuraUtils;

/** 客户端只提交奖池和次数，结果始终由服务端计算。 */
@Getter
public final class LotteryDrawRequestPacket implements INetworkPacket {
    private final String poolId;
    private final int count;

    public LotteryDrawRequestPacket(String poolId, int count) {
        this.poolId = poolId == null ? "" : poolId;
        this.count = count;
    }

    public LotteryDrawRequestPacket(BaniraPacketBuffer buffer) {
        poolId = buffer.readUtf();
        count = buffer.readInt();
    }

    public void toBytes(BaniraPacketBuffer buffer) {
        buffer.writeUtf(poolId);
        buffer.writeInt(count);
    }

    public static void handle(LotteryDrawRequestPacket packet, BaniraNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayerEntity sender = context.senderAs(ServerPlayerEntity.class);
            if (sender != null && !packet.poolId.isEmpty()
                    && sender.hasPermissions(SakuraUtils.getRewardPermissionLevel(
                    ERewardRule.LOTTERY_REWARD))
                    && (packet.count == -1 || packet.count >= 1
                    && packet.count <= LotteryRewardService.MAX_BATCH_DRAWS)) {
                LotteryDrawDispatcher.draw(sender, packet.poolId, packet.count);
            }
        });
        context.markHandled();
    }
}
