package xin.vanilla.sakura.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import xin.vanilla.sakura.command.impl.CardCommand;
import xin.vanilla.sakura.command.impl.CdkCommand;
import xin.vanilla.sakura.command.impl.ConfigCommand;
import xin.vanilla.sakura.command.impl.HelpCommand;
import xin.vanilla.sakura.command.impl.LanguageCommand;
import xin.vanilla.sakura.command.impl.SignActionCommand;
import xin.vanilla.sakura.command.impl.LotteryCommand;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.util.SakuraUtils;

/**
 * Sakura 指令入口，仅负责组合各功能节点。
 */
public final class SignInCommand {
    private SignInCommand() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        registerConcise(dispatcher);
        dispatcher.register(Commands.literal(SakuraUtils.getCommandPrefix())
                .executes(HelpCommand::executeRoot)
                .then(HelpCommand.build())
                .then(SignActionCommand.buildSign())
                .then(SignActionCommand.buildReward())
                .then(SignActionCommand.buildSignAndReward())
                .then(CdkCommand.build())
                .then(CardCommand.build())
                .then(LanguageCommand.build())
                .then(LotteryCommand.build())
                .then(ConfigCommand.build()));
    }

    private static void registerConcise(CommandDispatcher<CommandSource> dispatcher) {
        if (CommonConfig.get().concise().conciseSignIn()) {
            dispatcher.register(SignActionCommand.buildSign());
        }
        if (CommonConfig.get().concise().conciseReward()) {
            dispatcher.register(SignActionCommand.buildReward());
        }
        if (CommonConfig.get().concise().conciseSignInEx()) {
            dispatcher.register(SignActionCommand.buildSignAndReward());
        }
        if (CommonConfig.get().concise().conciseCdk()) {
            dispatcher.register(CdkCommand.build());
        }
        if (CommonConfig.get().concise().conciseCard()) {
            dispatcher.register(CardCommand.build());
        }
        if (CommonConfig.get().concise().conciseLanguage()) {
            dispatcher.register(LanguageCommand.build());
        }
    }
}
