package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.reward.lottery.LotteryDrawDispatcher;
import xin.vanilla.sakura.util.SakuraUtils;

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
                                        StringArgumentType.getString(context, "pool"), "1"))
                                .then(Commands.argument("count", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String value : new String[]{"1", "5", "10", "15", "all"}) {
                                                builder.suggest(value);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> draw(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "pool"),
                                                StringArgumentType.getString(context, "count"))))));
    }

    private static int list(ServerPlayerEntity player) {
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

    private static int draw(ServerPlayerEntity player, String poolId, String countText) {
        int count;
        try {
            count = "all".equalsIgnoreCase(countText) ? -1 : Integer.parseInt(countText);
        } catch (NumberFormatException ignored) {
            SakuraMessages.send(player, SakuraComponent.get().trans(
                    player, "word", "lottery_invalid_count"));
            return 0;
        }
        if (count != -1 && (count < 1
                || count > xin.vanilla.sakura.reward.lottery.LotteryRewardService.MAX_BATCH_DRAWS)) {
            SakuraMessages.send(player, SakuraComponent.get().trans(
                    player, "word", "lottery_invalid_count"));
            return 0;
        }
        LotteryDrawDispatcher.draw(player, poolId, count);
        return 1;
    }
}
