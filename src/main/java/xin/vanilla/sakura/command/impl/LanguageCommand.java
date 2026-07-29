package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.I18nUtils;

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
                            I18nUtils.getI18nFiles().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                            IPlayerSignInData data = SakuraPlayerData.get(player);
                            String language = StringArgumentType.getString(context, "language");
                            if (I18nUtils.getI18nFiles().contains(language)
                                    || "server".equalsIgnoreCase(language)
                                    || "client".equalsIgnoreCase(language)) {
                                data.setLanguage(language);
                                SakuraPlayerData.saveAndSync(player);
                                SakuraMessages.send(player, Component.translatable(
                                        player, EI18nType.MESSAGE, "player_default_language", language
                                ));
                            } else {
                                SakuraMessages.send(player, Component.translatable(
                                        player, EI18nType.MESSAGE, "language_not_exist", language
                                ).setColor(0xFFFF0000));
                            }
                            return 1;
                        }));
    }
}
