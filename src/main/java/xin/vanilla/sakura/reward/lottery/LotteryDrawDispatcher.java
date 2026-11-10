package xin.vanilla.sakura.reward.lottery;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.lottery.LotteryOperation;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将命令和客户端界面的抽奖请求汇入同一服务端响应流程。
 */
public final class LotteryDrawDispatcher {
    private static final LotteryCommandQueue<QueuedDraw> COMMANDS = new LotteryCommandQueue<>(3_000_000_000L, 256);
    private static final ThreadLocal<Boolean> COMMAND_SETTLEMENT = new ThreadLocal<>();

    private static final class QueuedDraw {
        final ServerPlayerEntity player;
        final String pool;
        final int count;
        final long revision;

        QueuedDraw(ServerPlayerEntity player, String pool, int count, long revision) {
            this.player = player;
            this.pool = pool;
            this.count = count;
            this.revision = revision;
        }
    }

    public static boolean enqueueCommand(ServerPlayerEntity player, String pool, int count) {
        boolean queued = COMMANDS.offer(player.getUUID(), new QueuedDraw(player, pool, count,
                xin.vanilla.sakura.config.reward.RewardConfigManager.getLotteryGeneration()), System.nanoTime());
        if (queued)
            xin.vanilla.banira.common.util.MessageUtils.sendActionBarMessage(player,
                    SakuraComponent.get().trans(player, "word", "lottery_drawing"));
        return queued;
    }

    public static void tickCommands(net.minecraft.server.MinecraftServer server) {
        QueuedDraw pending = COMMANDS.poll(System.nanoTime(), entry -> !entry.player.hasDisconnected()
                && server.getPlayerList().getPlayer(entry.player.getUUID()) == entry.player);
        if (pending == null) return;
        if (pending.revision != xin.vanilla.sakura.config.reward.RewardConfigManager.getLotteryGeneration()) {
            SakuraMessages.send(pending.player, SakuraComponent.get().trans(pending.player, "word", "lottery_configuration_changed"));
            return;
        }
        COMMAND_SETTLEMENT.set(true);
        try {
            draw(pending.player, pending.pool, pending.count);
        } finally {
            COMMAND_SETTLEMENT.remove();
        }
    }

    public static void clearCommands() {
        COMMANDS.clear();
    }

    private LotteryDrawDispatcher() {
    }

    public static LotteryDrawBatchResult draw(ServerPlayerEntity player, String poolId,
                                              int count) {
        LotteryDrawBatchResult result = LotteryRewardService.drawMany(player, poolId, count);
        if (!result.isSuccess()) notifyFailure(player, result);
        return result;
    }

    public static void completed(ServerPlayerEntity player, LotteryOperation operation,
                                 LotteryDrawBatchResult result, boolean recovered) {
        if (!Boolean.TRUE.equals(COMMAND_SETTLEMENT.get()) && BaniraModPresence.isRemoteClientInstalled(player, SakuraSignIn.MODID)) {
            try {
                PendingLotteryDraws.Pending pending = PendingLotteryDraws.put(player.getUUID(), operation.getOperationId(), operation.getDisplayToken());
                sendReveal(player, pending, result);
                return;
            } catch (IllegalArgumentException failure) {
                PendingLotteryDraws.remove(player.getUUID());
            }
        }
        LotteryOperationService service = LotteryRewardService.operations(player);
        if (service != null && service.acknowledge(player, operation.getOperationId(), operation.getDisplayToken()) != null
                && !recovered) notifySuccess(player, result.getRewards());
    }

    public static void acknowledge(ServerPlayerEntity player, String operationId, String token) {
        LotteryOperationService service = LotteryRewardService.operations(player);
        if (service == null) return;
        LotteryOperation operation = service.acknowledge(player, operationId, token);
        if (operation != null) {
            PendingLotteryDraws.acknowledge(player.getUUID(), operationId, token);
            notifySuccess(player, operation.getRewards());
        }
    }

    private static void sendReveal(ServerPlayerEntity player, PendingLotteryDraws.Pending pending,
                                   LotteryDrawBatchResult result) {
        SakuraNetwork.sendToPlayer(new LotteryRevealPacket(pending.getOperationId(), pending.getToken(),
                result.getPool().getDisplayName(), result.getRewards(), preview(result),
                result.getPool().getPreviewMode(), result.getPool().getAnimationStyle()), player);
    }

    private static void notifySuccess(ServerPlayerEntity player, List<Reward> awarded) {
        SakuraMessages.success(player, successMessage(SakuraUtils.getPlayerLanguage(player), awarded),
                SakuraNotificationTypes.REWARD);
    }

    static xin.vanilla.banira.common.data.Component successMessage(String language, List<Reward> awarded) {
        xin.vanilla.banira.common.data.Component rewards = SakuraComponent.get().empty();
        for (int index = 0; index < awarded.size(); index++) {
            if (index > 0) rewards.append(", ");
            rewards.append(awarded.get(index).getName(language, true));
        }
        return SakuraComponent.get().transLang(language, "format", "lottery_draw_success_s", rewards);
    }

    public static void notifyFailure(ServerPlayerEntity player, LotteryDrawBatchResult result) {
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
            case POOL_CLOSED:
                SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "lottery_pool_closed"));
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
