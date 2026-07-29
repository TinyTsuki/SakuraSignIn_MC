package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

/**
 * 配置指令入口，仅组合查询与修改子树。
 */
public final class ConfigCommand {
    private ConfigCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal("config")
                .then(ConfigQueryCommand.build())
                .then(ConfigUpdateCommand.build());
    }
}
