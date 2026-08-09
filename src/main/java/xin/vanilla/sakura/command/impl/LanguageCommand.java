package xin.vanilla.sakura.command.impl;

import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraLang;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.banira.common.data.Component;

/**
 * 玩家语言设置指令。
 */
public final class LanguageCommand {
    private LanguageCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal(CommonConfig.get().command().commandLanguage())
                .then(Commands.argument("language", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("client");
                            builder.suggest("server");
                            SakuraLang.get().getI18nFiles().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                            IPlayerSignInData data = SakuraPlayerData.get(player);
                            String language = StringArgumentType.getString(context, "language");
                            if (SakuraLang.get().getI18nFiles().contains(language)
                                    || "server".equalsIgnoreCase(language)
                                    || "client".equalsIgnoreCase(language)) {
                                data.setLanguage(language);
                                SakuraPlayerData.saveAndSync(player);
                                SakuraMessages.send(player, SakuraComponent.get().trans(player, "format", "player_default_language", language
                                ));
                            } else {
                                SakuraMessages.send(player, SakuraComponent.get().trans(player, "format", "language_not_exist", language
                                ).color(0xFFFF0000));
                            }
                            return 1;
                        }));
    }
}
