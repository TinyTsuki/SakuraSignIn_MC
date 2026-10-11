package xin.vanilla.sakura.reward.lottery;

import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.util.SakuraUtils;
import xin.vanilla.sakura.data.lottery.LotteryOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 将命令和客户端界面的抽奖请求汇入同一服务端响应流程。 */
public final class LotteryDrawDispatcher {
    private LotteryDrawDispatcher() {
    }

    public static LotteryDrawBatchResult draw(ServerPlayer player, String poolId,
                                              int count) {
        LotteryDrawBatchResult result = LotteryRewardService.drawMany(player, poolId, count);
        if (!result.isSuccess()) notifyFailure(player, result);
        return result;
    }
    public static void completed(ServerPlayer player, LotteryOperation operation,
                                 LotteryDrawBatchResult result, boolean recovered) {
        if (!recovered) notifySuccess(player, result);
        if (BaniraModPresence.isRemoteClientInstalled(player, SakuraSignIn.MODID)) {
                try {
                    PendingLotteryDraws.Pending pending = PendingLotteryDraws.put(player.getUUID(), operation.getOperationId(), operation.getDisplayToken());
                    sendReveal(player, pending, result);
                } catch (IllegalArgumentException failure) {
                    PendingLotteryDraws.remove(player.getUUID());
                }
        }
    }

    public static void acknowledge(ServerPlayer player, String operationId, String token) {
        LotteryRewardService.operations(player).acknowledge(player, operationId, token);
        PendingLotteryDraws.acknowledge(player.getUUID(), operationId, token);
    }

    private static void sendReveal(ServerPlayer player, PendingLotteryDraws.Pending pending,
                                    LotteryDrawBatchResult result) {
        SakuraNetwork.sendToPlayer(new LotteryRevealPacket(pending.getOperationId(), pending.getToken(),
                result.getPool().getDisplayName(), result.getRewards(), preview(result),
                result.getPool().getPreviewMode()), player);
    }

    private static void notifySuccess(ServerPlayer player, LotteryDrawBatchResult result) {
        String rewards = result.getRewards().stream().map(reward -> reward.getName(
                        SakuraUtils.getPlayerLanguage(player), true).toString())
                .collect(Collectors.joining(", "));
        SakuraMessages.success(player, SakuraComponent.get().trans(player, "format",
                "lottery_draw_success_s", rewards), SakuraNotificationTypes.REWARD);
    }

    public static void notifyFailure(ServerPlayer player, LotteryDrawBatchResult result) {
        switch (result.getStatus()) {
            case OPERATION_PENDING:
            case OPERATION_UNCERTAIN:
                xin.vanilla.banira.common.data.Component pending = SakuraComponent.get().trans(player, "word",
                        "enum_lottery_draw_status_" + result.getStatus().name().toLowerCase(java.util.Locale.ROOT));
                SakuraMessages.send(player, result.getRewards().isEmpty() ? pending : SakuraComponent.get().trans(player,
                        "format", "lottery_partial_complete_ss", result.getRewards().size(), pending));
                break;
            case PAYMENT_UNAVAILABLE:
                SakuraMessages.send(player, SakuraComponent.get().trans(
                        player, "word", "lottery_payment_unavailable"));
                break;
            case LIMIT_REACHED:
                SakuraMessages.send(player, SakuraComponent.get().trans(player, "format",
                        "lottery_limit_reached_ss", result.getRetryAfterSeconds(),
                        result.getRemainingDraws()), SakuraNotificationTypes.REWARD);
                break;
            case POOL_NOT_FOUND:
                SakuraMessages.send(player, SakuraComponent.get().trans(
                        player, "word", "lottery_pool_not_found"));
                break;
            case EMPTY_POOL:
                SakuraMessages.send(player, SakuraComponent.get().trans(
                        player, "word", "lottery_pool_empty"));
                break;
            case GRANT_FAILED:
            default:
                SakuraMessages.send(player, SakuraComponent.get().trans(
                        player, "word", "lottery_grant_failed"));
                break;
        }
    }

    private static List<Reward> preview(LotteryDrawBatchResult result) {
        if (!result.getPool().getPreviewMode().showsItems()) {
            return Collections.emptyList();
        }
        List<Reward> values = new ArrayList<>();
        result.getPool().getRewards().stream()
                .filter(reward -> reward != null && !reward.isDisabled())
                .forEach(values::add);
        if (values.isEmpty()) {
            return values;
        }
        Collections.shuffle(values);
        List<Reward> preview = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            preview.add(values.get(i % values.size()).clone());
        }
        return preview;
    }
}
