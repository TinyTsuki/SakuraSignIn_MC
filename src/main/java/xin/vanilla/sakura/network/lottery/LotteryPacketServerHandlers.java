package xin.vanilla.sakura.network.lottery;

import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.api.BaniraNetwork;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.network.packet.LotteryQuotePacket;
import xin.vanilla.sakura.network.packet.LotteryDrawOutcomePacket;
import xin.vanilla.sakura.util.SakuraUtils;
import xin.vanilla.sakura.reward.lottery.*;

public final class LotteryPacketServerHandlers {
    private LotteryPacketServerHandlers() { }

    public static LotteryQuote.Status status(LotteryQuoteRequest request, long generation,
                                              boolean permitted, boolean poolExists) {
        if (!permitted) return LotteryQuote.Status.FORBIDDEN;
        if (request.getGeneration() != generation) return LotteryQuote.Status.STALE_GENERATION;
        if (!poolExists) return LotteryQuote.Status.POOL_NOT_FOUND;
        return LotteryQuote.Status.PAYMENT_UNAVAILABLE;
    }

    public static void quote(LotteryQuoteRequest request, BaniraNetworkContext context, Object origin) {
        ServerPlayer player = context.senderAs(ServerPlayer.class);
        if (!LotteryRewardService.isCurrent(player, origin)) return;
        BaniraNetwork.sendToPlayer(new LotteryQuotePacket(LotteryRewardService.operations(player).quote(player, request)), player);
    }

    public static void draw(LotteryDrawRequest request, BaniraNetworkContext context, Object origin) {
        ServerPlayer player = context.senderAs(ServerPlayer.class);
        if (!LotteryRewardService.isCurrent(player, origin)) return;
        LotteryDrawBatchResult result = LotteryRewardService.operations(player).draw(player, request);
        BaniraNetwork.sendToPlayer(new LotteryDrawOutcomePacket(request.getOperationId()), player);
        if (!result.isSuccess()) LotteryDrawDispatcher.notifyFailure(player, result);
    }
}
