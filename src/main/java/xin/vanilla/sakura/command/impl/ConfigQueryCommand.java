package xin.vanilla.sakura.command.impl;

import xin.vanilla.sakura.data.time.SakuraClock;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.function.Function;

/**
 * 构建只读的服务端配置查询节点。
 */
final class ConfigQueryCommand {
    private ConfigQueryCommand() {
    }

    static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal("get")
                .requires(source -> source.hasPermission(
                        CommonConfig.get().permission().permissionServerConfigGet()
                ))
                .then(query("autoSignIn", player -> ConfigCommandMessages.translated(
                        player, "server_enabled_or_not_auto_sign",
                        ConfigCommandMessages.enabled(player, CommonConfig.get().server().autoSignIn())
                )))
                .then(query("timeCoolingMethod", player -> ConfigCommandMessages.translated(
                        player, "sign_in_time_cool_down_mode_s",
                        CommonConfig.get().cooling().timeCoolingMethod().name()
                )))
                .then(query("timeCoolingTime", player -> ConfigCommandMessages.translated(
                        player, "sign_in_time_cool_down_refresh_time_f",
                        CommonConfig.get().cooling().timeCoolingTime()
                )))
                .then(query("timeCoolingInterval", player -> ConfigCommandMessages.translated(
                        player, "sign_in_time_cool_down_refresh_interval_f",
                        CommonConfig.get().cooling().timeCoolingInterval()
                )))
                .then(query("signInCard", player -> ConfigCommandMessages.translated(
                        player, "server_enabled_or_not_sign_in_card",
                        ConfigCommandMessages.enabled(player, CommonConfig.get().makeUp().signInCard())
                )))
                .then(query("reSignInDays", player -> ConfigCommandMessages.translated(
                        player, "max_sign_in_day_d",
                        CommonConfig.get().makeUp().reSignInDays()
                )))
                .then(query("signInCardOnlyBaseReward", player -> ConfigCommandMessages.translated(
                        player, "server_enabled_or_not_sign_in_card_only_basic_reward",
                        ConfigCommandMessages.enabled(
                                player, CommonConfig.get().makeUp().signInCardOnlyBaseReward())
                )))
                .then(query("date", player -> ConfigCommandMessages.translated(
                        player, "server_current_time_s",
                        DateUtils.toDateTimeString(SakuraClock.serverNow())
                )))
                .then(query("rewardAffectedByLuck", player -> ConfigCommandMessages.translated(
                        player, "server_enabled_or_not_reward_affected_by_luck",
                        ConfigCommandMessages.enabled(
                                player, CommonConfig.get().reward().rewardAffectedByLuck())
                )))
                .then(query("continuousRewardsRepeatable", player -> ConfigCommandMessages.translated(
                        player, "server_enabled_or_not_continuous_rewards_repeatable",
                        ConfigCommandMessages.enabled(
                                player, CommonConfig.get().reward().continuousRewardsRepeatable())
                )))
                .then(query("cycleRewardsRepeatable", player -> ConfigCommandMessages.translated(
                        player, "server_enabled_or_not_cycle_rewards_repeatable",
                        ConfigCommandMessages.enabled(
                                player, CommonConfig.get().reward().cycleRewardsRepeatable())
                )))
                .then(query("language", player -> ConfigCommandMessages.translated(
                        player, "server_default_language",
                        CommonConfig.get().server().defaultLanguage()
                )));
    }

    private static LiteralArgumentBuilder<CommandSource> query(
            String name,
            Function<ServerPlayerEntity, Component> message
    ) {
        return Commands.literal(name).executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            SakuraMessages.send(player, message.apply(player));
            return 1;
        });
    }
}
