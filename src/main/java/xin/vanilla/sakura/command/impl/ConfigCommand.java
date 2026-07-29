package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.command.CommandDateTimeParser;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.I18nUtils;
import xin.vanilla.sakura.util.SakuraUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 服务器配置查询与修改指令。
 */
public final class ConfigCommand {
    private ConfigCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal("config")
                .then(buildGet())
                .then(buildSet());
    }

    private static LiteralArgumentBuilder<CommandSource> buildGet() {
        return Commands.literal("get")
                .requires(source -> source.hasPermission(
                        CommonConfig.get().permission().permissionServerConfigGet()
                ))
                .then(query("autoSignIn", player -> translated(
                        player, "server_enabled_or_not_auto_sign",
                        enabled(player, CommonConfig.get().server().autoSignIn())
                )))
                .then(query("timeCoolingMethod", player -> translated(
                        player, "sign_in_time_cool_down_mode_s",
                        CommonConfig.get().cooling().timeCoolingMethod().name()
                )))
                .then(query("timeCoolingTime", player -> translated(
                        player, "sign_in_time_cool_down_refresh_time_f",
                        CommonConfig.get().cooling().timeCoolingTime()
                )))
                .then(query("timeCoolingInterval", player -> translated(
                        player, "sign_in_time_cool_down_refresh_interval_f",
                        CommonConfig.get().cooling().timeCoolingInterval()
                )))
                .then(query("signInCard", player -> translated(
                        player, "server_enabled_or_not_sign_in_card",
                        enabled(player, CommonConfig.get().makeUp().signInCard())
                )))
                .then(query("reSignInDays", player -> translated(
                        player, "max_sign_in_day_d",
                        CommonConfig.get().makeUp().reSignInDays()
                )))
                .then(query("signInCardOnlyBaseReward", player -> translated(
                        player, "server_enabled_or_not_sign_in_card_only_basic_reward",
                        enabled(player, CommonConfig.get().makeUp().signInCardOnlyBaseReward())
                )))
                .then(query("date", player -> translated(
                        player, "server_current_time_s",
                        DateUtils.toDateTimeString(DateUtils.getServerDate())
                )))
                .then(query("rewardAffectedByLuck", player -> translated(
                        player, "server_enabled_or_not_reward_affected_by_luck",
                        enabled(player, CommonConfig.get().reward().rewardAffectedByLuck())
                )))
                .then(query("continuousRewardsRepeatable", player -> translated(
                        player, "server_enabled_or_not_continuous_rewards_repeatable",
                        enabled(player, CommonConfig.get().reward().continuousRewardsRepeatable())
                )))
                .then(query("cycleRewardsRepeatable", player -> translated(
                        player, "server_enabled_or_not_cycle_rewards_repeatable",
                        enabled(player, CommonConfig.get().reward().cycleRewardsRepeatable())
                )))
                .then(query("language", player -> translated(
                        player, "server_default_language",
                        CommonConfig.get().server().defaultLanguage()
                )));
    }

    private static LiteralArgumentBuilder<CommandSource> buildSet() {
        return Commands.literal("set")
                .requires(source -> source.hasPermission(
                        CommonConfig.get().permission().permissionServerConfigSet()
                ))
                .then(Commands.literal("date")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    LocalDateTime now = DateUtils.getLocalDateTime(
                                            DateUtils.getServerDate()
                                    );
                                    builder.suggest(String.format(
                                            "%d %d %d %d %d %d",
                                            now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                                            now.getHour(), now.getMinute(), now.getSecond()
                                    ));
                                    builder.suggest("~ ~ ~ ~ ~ ~");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    long value = CommandDateTimeParser.parse(
                                            StringArgumentType.getString(context, "value"),
                                            CommandDateTimeParser.Kind.DATE_TIME,
                                            DateUtils.getLocalDateTime(DateUtils.getServerDate())
                                    );
                                    Date date = DateUtils.getDate(value);
                                    CommonConfig.get().dateTime().serverTime(
                                            DateUtils.toDateTimeString(new Date())
                                    );
                                    CommonConfig.get().dateTime().serverCalibrationTime(
                                            DateUtils.toDateTimeString(date)
                                    );
                                    return saveAndBroadcast(
                                            context.getSource().getPlayerOrException(),
                                            "set_server_time_s",
                                            DateUtils.toDateTimeString(date)
                                    );
                                })))
                .then(booleanSetting(
                        "autoSignIn",
                        value -> CommonConfig.get().server().autoSignIn(value),
                        (player, value) -> translated(
                                player, "server_enabled_or_not_auto_sign", enabled(player, value)
                        ),
                        null
                ))
                .then(booleanSetting(
                        "signInCard",
                        value -> CommonConfig.get().makeUp().signInCard(value),
                        (player, value) -> translated(
                                player, "server_enabled_or_not_sign_in_card", enabled(player, value)
                        ),
                        null
                ))
                .then(Commands.literal("reSignInDays")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 365))
                                .suggests((context, builder) -> builder
                                        .suggest(1).suggest(7).suggest(30).suggest(365)
                                        .buildFuture())
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    CommonConfig.get().makeUp().reSignInDays(value);
                                    return saveAndBroadcast(
                                            context.getSource().getPlayerOrException(),
                                            "set_max_sign_in_day_d",
                                            value
                                    );
                                })))
                .then(booleanSetting(
                        "signInCardOnlyBaseReward",
                        value -> CommonConfig.get().makeUp().signInCardOnlyBaseReward(value),
                        (player, value) -> translated(
                                player,
                                "server_enabled_or_not_sign_in_card_only_basic_reward",
                                enabled(player, value)
                        ),
                        null
                ))
                .then(Commands.literal("timeCoolingMethod")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (ETimeCoolingMethod value : ETimeCoolingMethod.values()) {
                                        builder.suggest(value.name());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ETimeCoolingMethod value = ETimeCoolingMethod.valueOf(
                                            StringArgumentType.getString(context, "value").toUpperCase()
                                    );
                                    CommonConfig.get().cooling().timeCoolingMethod(value);
                                    return saveAndBroadcast(
                                            context.getSource().getPlayerOrException(),
                                            "set_sign_in_time_cool_down_mode_s",
                                            value.name()
                                    );
                                })))
                .then(doubleSetting(
                        "timeCoolingTime", -23.59, 23.59,
                        value -> CommonConfig.get().cooling().timeCoolingTime(value),
                        "set_sign_in_time_cool_down_refresh_time_f"
                ))
                .then(doubleSetting(
                        "timeCoolingInterval", 0, 23.59,
                        value -> CommonConfig.get().cooling().timeCoolingInterval(value),
                        "set_sign_in_time_cool_down_refresh_interval_f"
                ))
                .then(booleanSetting(
                        "rewardAffectedByLuck",
                        value -> CommonConfig.get().reward().rewardAffectedByLuck(value),
                        (player, value) -> translated(
                                player,
                                "server_enabled_or_not_reward_affected_by_luck",
                                enabled(player, value)
                        ),
                        null
                ))
                .then(booleanSetting(
                        "continuousRewardsRepeatable",
                        value -> CommonConfig.get().reward().continuousRewardsRepeatable(value),
                        (player, value) -> translated(
                                player,
                                "server_enabled_or_not_continuous_rewards_repeatable",
                                enabled(player, value)
                        ),
                        () -> RewardConfigManager.getRewardConfig()
                                .refreshContinuousRewardsRelation()
                ))
                .then(booleanSetting(
                        "cycleRewardsRepeatable",
                        value -> CommonConfig.get().reward().cycleRewardsRepeatable(value),
                        (player, value) -> translated(
                                player,
                                "server_enabled_or_not_cycle_rewards_repeatable",
                                enabled(player, value)
                        ),
                        () -> RewardConfigManager.getRewardConfig().refreshCycleRewardsRelation()
                ))
                .then(Commands.literal("language")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    I18nUtils.getI18nFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String value = StringArgumentType.getString(context, "value");
                                    CommonConfig.get().server().defaultLanguage(value);
                                    return saveAndBroadcast(
                                            context.getSource().getPlayerOrException(),
                                            "server_default_language",
                                            value
                                    );
                                })));
    }

    private static LiteralArgumentBuilder<CommandSource> query(
            String name,
            java.util.function.Function<ServerPlayerEntity, Component> message
    ) {
        return Commands.literal(name).executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            SakuraMessages.send(player, message.apply(player));
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSource> booleanSetting(
            String name,
            Consumer<Boolean> setter,
            BiFunction<ServerPlayerEntity, Boolean, Component> message,
            Runnable after
    ) {
        return Commands.literal(name)
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean value = BoolArgumentType.getBool(context, "value");
                            setter.accept(value);
                            if (after != null) {
                                after.run();
                            }
                            CommonConfig.save();
                            ServerPlayerEntity player =
                                    context.getSource().getPlayerOrException();
                            SakuraMessages.broadcast(player, message.apply(player, value));
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSource> doubleSetting(
            String name,
            double minimum,
            double maximum,
            Consumer<Double> setter,
            String messageKey
    ) {
        return Commands.literal(name)
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(minimum, maximum))
                        .suggests((context, builder) -> builder
                                .suggest("0.00").suggest("6.00").suggest("12.00")
                                .suggest("23.59").buildFuture())
                        .executes(context -> {
                            double value = DoubleArgumentType.getDouble(context, "value");
                            CommandDateTimeParser.validateClockValue(value);
                            setter.accept(value);
                            return saveAndBroadcast(
                                    context.getSource().getPlayerOrException(),
                                    messageKey,
                                    value
                            );
                        }));
    }

    private static int saveAndBroadcast(
            ServerPlayerEntity player,
            String messageKey,
            Object... args
    ) {
        CommonConfig.save();
        SakuraMessages.broadcast(player, translated(player, messageKey, args));
        return 1;
    }

    private static Component translated(
            ServerPlayerEntity player,
            String key,
            Object... args
    ) {
        return Component.translatable(player, EI18nType.MESSAGE, key, args);
    }

    private static Component enabled(ServerPlayerEntity player, boolean value) {
        return I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), value);
    }
}
