package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.reward.lottery.LotteryDrawDispatcher;
import xin.vanilla.sakura.util.SakuraUtils;

/**
 * 列出并领取服务端权威抽奖池。
 */
public final class LotteryCommand {
    private LotteryCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
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
                                        StringArgumentType.getString(context, "pool"), null))
                                .then(Commands.argument("count", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            xin.vanilla.sakura.data.lottery.LotteryPools.find(
                                                    RewardConfigManager.getRewardConfig().getLotteryPools(),
                                                    StringArgumentType.getString(context, "pool")).ifPresent(pool -> {
                                                pool.getDrawCounts().forEach(builder::suggest);
                                            });
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> draw(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "pool"),
                                                StringArgumentType.getString(context, "count"))))));
    }

    private static int list(ServerPlayer player) {
        if (RewardConfigManager.getRewardConfig().getLotteryPools().isEmpty()) {
            SakuraMessages.send(player, SakuraComponent.get().trans(
                    player, "word", "lottery_no_pools"));
            return 1;
        }
        Component message = SakuraComponent.get().trans(
                player, "word", "lottery_available_pools").color(0xFFAAAAAA);
        RewardConfigManager.getRewardConfig().getLotteryPools().forEach(pool -> message
                .append("\n")
                .append(SakuraComponent.get().literal(pool.getDisplayName()
                        + " (" + pool.getId() + ")").color(0xFFFFAA00)));
        SakuraMessages.send(player, message);
        return 1;
    }

    private static int draw(ServerPlayer player, String poolId, String countText) {
        var pool = xin.vanilla.sakura.data.lottery.LotteryPools.find(RewardConfigManager.getRewardConfig().getLotteryPools(), poolId);
        int count;
        try {
            count = countText == null ? pool.map(value -> value.defaultDrawCount()).orElse(1) : Integer.parseInt(countText);
        } catch (NumberFormatException ignored) {
            SakuraMessages.send(player, SakuraComponent.get().trans(
                    player, "word", "lottery_invalid_count"));
            return 0;
        }
        if (count < 1 || count > xin.vanilla.sakura.data.lottery.LotteryPoolValidator.MAX_BATCH_SIZE
                || pool.isPresent() && !pool.get().allowsCount(count)) {
            SakuraMessages.send(player, SakuraComponent.get().trans(
                    player, "word", "lottery_invalid_count"));
            return 0;
        }
        return LotteryDrawDispatcher.enqueueCommand(player, poolId, count) ? 1 : 0;
    }
}
