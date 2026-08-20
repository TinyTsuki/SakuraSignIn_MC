package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;

/**
 * 配置指令入口：通用配置使用描述符点路径，玩家数据使用独立子树。
 */
public final class ConfigCommand {
    private ConfigCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("config")
                .then(common())
                .then(Commands.literal("player")
                        .then(PersonalDateConfigCommand.build()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> common() {
        return Commands.literal("common")
                .requires(source -> source.hasPermission(
                        CommonConfig.get().permission().permissionServerConfigSet()))
                .then(Commands.argument("configKey", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            CommandUtils.configKeySuggestion(holder(), builder,
                                    CommandUtils.getStringEmpty(context, "configKey"));
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("configValue", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    CommandUtils.configValueSuggestion(holder(), builder,
                                            StringArgumentType.getString(context, "configKey"));
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    int result = CommandUtils.executeModifyConfig(holder(), context);
                                    if (result > 0) {
                                        RewardConfigManager.getRewardConfig()
                                                .refreshContinuousRewardsRelation();
                                        RewardConfigManager.getRewardConfig()
                                                .refreshCycleRewardsRelation();
                                    }
                                    return result;
                                })));
    }

    private static ConfigHolder holder() {
        return BaniraConfigs.holder(CommonConfig.class);
    }
}
