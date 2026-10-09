package xin.vanilla.sakura.network.lottery;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.network.packet.LotteryQuotePacket;
import xin.vanilla.sakura.util.SakuraUtils;

public final class LotteryPacketServerHandlers {
    private LotteryPacketServerHandlers() { }

    public static LotteryQuote.Status status(LotteryQuoteRequest request, long generation,
                                              boolean permitted, boolean poolExists) {
        if (!permitted) return LotteryQuote.Status.FORBIDDEN;
        if (request.getGeneration() != generation) return LotteryQuote.Status.STALE_GENERATION;
        if (!poolExists) return LotteryQuote.Status.POOL_NOT_FOUND;
        return LotteryQuote.Status.PAYMENT_UNAVAILABLE;
    }

    public static void quote(LotteryQuoteRequest request, BaniraNetworkContext context) {
        ServerPlayerEntity player = context.senderAs(ServerPlayerEntity.class);
        if (player == null) return;
        long generation = RewardConfigManager.getLotteryGeneration();
        boolean permitted = player.hasPermissions(SakuraUtils.getRewardPermissionLevel(ERewardRule.LOTTERY_REWARD));
        LotteryQuote.Status status = status(request, generation, permitted,
                RewardConfigManager.lotteryPool(request.getPoolId()) != null);
        BaniraNetwork.sendToPlayer(new LotteryQuotePacket(new LotteryQuote(request, status,
                generation, 0, "", "")), player);
    }

    public static void draw(LotteryDrawRequest request, BaniraNetworkContext context) {
        ServerPlayerEntity player = context.senderAs(ServerPlayerEntity.class);
        if (player == null) return;
        LotteryQuoteRequest header = request.getRequest();
        if (!player.hasPermissions(SakuraUtils.getRewardPermissionLevel(ERewardRule.LOTTERY_REWARD))
                || header.getGeneration() != RewardConfigManager.getLotteryGeneration()
                || RewardConfigManager.lotteryPool(header.getPoolId()) == null) return;
        SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "lottery_payment_unavailable"));
    }
}
