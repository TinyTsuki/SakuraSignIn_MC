package xin.vanilla.sakura.command.impl;

import xin.vanilla.sakura.data.time.SakuraClock;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.Translator;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.command.CommandDateTimeParser;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.banira.common.util.DateUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 构建服务端配置修改节点并负责参数校验。
 */
final class ConfigUpdateCommand {
    private ConfigUpdateCommand() {
    }

    static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal("set")
                .requires(source -> source.hasPermission(
                        CommonConfig.get().permission().permissionServerConfigSet()
                ))
                .then(dateSetting())
                .then(booleanSetting(
                        "autoSignIn",
                        value -> CommonConfig.get().server().autoSignIn(value),
                        (player, value) -> ConfigCommandMessages.translated(
                                player, "server_enabled_or_not_auto_sign",
                                ConfigCommandMessages.enabled(player, value)
                        ),
                        null
                ))
                .then(booleanSetting(
                        "signInCard",
                        value -> CommonConfig.get().makeUp().signInCard(value),
                        (player, value) -> ConfigCommandMessages.translated(
                                player, "server_enabled_or_not_sign_in_card",
                                ConfigCommandMessages.enabled(player, value)
                        ),
                        null
                ))
                .then(reSignInDaysSetting())
                .then(booleanSetting(
                        "signInCardOnlyBaseReward",
                        value -> CommonConfig.get().makeUp().signInCardOnlyBaseReward(value),
                        (player, value) -> ConfigCommandMessages.translated(
                                player,
                                "server_enabled_or_not_sign_in_card_only_basic_reward",
                                ConfigCommandMessages.enabled(player, value)
                        ),
                        null
                ))
                .then(coolingMethodSetting())
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
                        (player, value) -> ConfigCommandMessages.translated(
                                player, "server_enabled_or_not_reward_affected_by_luck",
                                ConfigCommandMessages.enabled(player, value)
                        ),
                        null
                ))
                .then(booleanSetting(
                        "continuousRewardsRepeatable",
                        value -> CommonConfig.get().reward().continuousRewardsRepeatable(value),
                        (player, value) -> ConfigCommandMessages.translated(
                                player, "server_enabled_or_not_continuous_rewards_repeatable",
                                ConfigCommandMessages.enabled(player, value)
                        ),
                        () -> RewardConfigManager.getRewardConfig()
                                .refreshContinuousRewardsRelation()
                ))
                .then(booleanSetting(
                        "cycleRewardsRepeatable",
                        value -> CommonConfig.get().reward().cycleRewardsRepeatable(value),
                        (player, value) -> ConfigCommandMessages.translated(
                                player, "server_enabled_or_not_cycle_rewards_repeatable",
                                ConfigCommandMessages.enabled(player, value)
                        ),
                        () -> RewardConfigManager.getRewardConfig().refreshCycleRewardsRelation()
                ))
                .then(languageSetting());
    }

    private static LiteralArgumentBuilder<CommandSource> dateSetting() {
        return Commands.literal("date")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            LocalDateTime now = DateUtils.getLocalDateTime(SakuraClock.serverNow());
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
                                    DateUtils.getLocalDateTime(SakuraClock.serverNow())
                            );
                            Date date = DateUtils.getDate(value);
                            CommonConfig.get().dateTime().serverTime(
                                    DateUtils.toDateTimeString(new Date())
                            );
                            CommonConfig.get().dateTime().serverCalibrationTime(
                                    DateUtils.toDateTimeString(date)
                            );
                            return ConfigCommandMessages.saveAndBroadcast(
                                    context.getSource().getPlayerOrException(),
                                    "set_server_time_s",
                                    DateUtils.toDateTimeString(date)
                            );
                        }));
    }

    private static LiteralArgumentBuilder<CommandSource> reSignInDaysSetting() {
        return Commands.literal("reSignInDays")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 365))
                        .suggests((context, builder) -> builder
                                .suggest(1).suggest(7).suggest(30).suggest(365)
                                .buildFuture())
                        .executes(context -> {
                            int value = IntegerArgumentType.getInteger(context, "value");
                            CommonConfig.get().makeUp().reSignInDays(value);
                            return ConfigCommandMessages.saveAndBroadcast(
                                    context.getSource().getPlayerOrException(),
                                    "set_max_sign_in_day_d",
                                    value
                            );
                        }));
    }

    private static LiteralArgumentBuilder<CommandSource> coolingMethodSetting() {
        return Commands.literal("timeCoolingMethod")
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
                            return ConfigCommandMessages.saveAndBroadcast(
                                    context.getSource().getPlayerOrException(),
                                    "set_sign_in_time_cool_down_mode_s",
                                    value.name()
                            );
                        }));
    }

    private static LiteralArgumentBuilder<CommandSource> languageSetting() {
        return Commands.literal("language")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Translator.of(SakuraSignIn.MODID)
                                    .getI18nFiles()
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String value = StringArgumentType.getString(context, "value");
                            CommonConfig.get().server().defaultLanguage(value);
                            return ConfigCommandMessages.saveAndBroadcast(
                                    context.getSource().getPlayerOrException(),
                                    "server_default_language",
                                    value
                            );
                        }));
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
                            return ConfigCommandMessages.saveAndBroadcast(
                                    context.getSource().getPlayerOrException(),
                                    messageKey,
                                    value
                            );
                        }));
    }
}
