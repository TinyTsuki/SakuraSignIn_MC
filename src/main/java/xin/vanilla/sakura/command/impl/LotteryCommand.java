package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.lottery.LotteryDrawResult;
import xin.vanilla.sakura.reward.lottery.LotteryRewardService;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 列出并领取服务端权威抽奖池。 */
public final class LotteryCommand {
    private LotteryCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal(CommonConfig.get().command().commandLottery())
                .requires(source -> source.hasPermission(
                        SakuraUtils.getRewardPermissionLevel(ERewardRule.LOTTERY_REWARD)))
                .then(Commands.literal("list").executes(context ->
                        list(context.getSource().getPlayerOrException())))
                .then(Commands.literal("draw")
                        .then(Commands.argument("pool", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    RewardConfigManager.getRewardConfig().getLotteryPools()
                                            .forEach(pool -> builder.suggest(pool.getId()));
                                    return builder.buildFuture();
                                })
                                .executes(context -> draw(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "pool")))));
    }

    private static int list(ServerPlayerEntity player) {
        if (RewardConfigManager.getRewardConfig().getLotteryPools().isEmpty()) {
            SakuraMessages.send(player, SakuraComponent.get().trans(
                    player, "word", "lottery_no_pools"));
            return 1;
        }
        String names = RewardConfigManager.getRewardConfig().getLotteryPools().stream()
                .map(pool -> pool.getDisplayName() + " (" + pool.getId() + ")")
                .collect(java.util.stream.Collectors.joining("\n"));
        SakuraMessages.send(player, SakuraComponent.get().trans(
                player, "format", "lottery_pool_list_s", names));
        return 1;
    }

    private static int draw(ServerPlayerEntity player, String poolId) {
        LotteryDrawResult result = LotteryRewardService.draw(player, poolId);
        switch (result.getStatus()) {
            case SUCCESS:
                Reward winner = result.getReward();
                SakuraMessages.send(player, SakuraComponent.get().trans(player, "format",
                        "lottery_draw_success_s", winner.getName(
                                SakuraUtils.getPlayerLanguage(player), true).toString()),
                        SakuraNotificationTypes.REWARD);
                if (BaniraModPresence.isRemoteClientInstalled(player, SakuraSignIn.MODID)) {
                    SakuraNetwork.sendToPlayer(new LotteryRevealPacket(
                            result.getPool().getDisplayName(), winner,
                            preview(result.getPool())), player);
                }
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
        return 1;
    }

    private static List<Reward> preview(LotteryPool pool) {
        List<Reward> values = new ArrayList<>();
        pool.getRewards().stream().filter(reward -> reward != null && !reward.isDisabled())
                .forEach(values::add);
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.shuffle(values);
        List<Reward> preview = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            preview.add(values.get(i % values.size()).clone());
        }
        return preview;
    }
}
