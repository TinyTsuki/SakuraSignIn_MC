package xin.vanilla.sakura.reward.lottery;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 将命令和客户端界面的抽奖请求汇入同一服务端响应流程。 */
public final class LotteryDrawDispatcher {
    private LotteryDrawDispatcher() {
    }

    public static LotteryDrawBatchResult draw(ServerPlayerEntity player, String poolId,
                                              int count) {
        LotteryDrawBatchResult result = LotteryRewardService.drawMany(player, poolId, count);
        if (result.isSuccess()) {
            if (BaniraModPresence.isRemoteClientInstalled(player, SakuraSignIn.MODID)) {
                SakuraNetwork.sendToPlayer(new LotteryRevealPacket(
                        result.getPool().getDisplayName(), result.getRewards(),
                        preview(result), result.getPool().isShowRewards()), player);
            } else {
                String rewards = result.getRewards().stream().map(reward -> reward.getName(
                                SakuraUtils.getPlayerLanguage(player), true).toString())
                        .collect(Collectors.joining(", "));
                SakuraMessages.send(player, SakuraComponent.get().trans(player, "format",
                        "lottery_draw_success_s", rewards), SakuraNotificationTypes.REWARD);
            }
            return result;
        }
        switch (result.getStatus()) {
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
        return result;
    }

    private static List<Reward> preview(LotteryDrawBatchResult result) {
        if (!result.getPool().isShowRewards()) {
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
