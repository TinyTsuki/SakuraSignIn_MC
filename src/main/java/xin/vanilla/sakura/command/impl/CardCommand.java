package xin.vanilla.sakura.command.impl;

import xin.vanilla.sakura.SakuraComponent;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.banira.common.data.Component;

import java.util.Collection;

/**
 * 查询、增减或设置玩家补签卡。
 */
public final class CardCommand {
    private CardCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(CommonConfig.get().command().commandCard())
                .executes(context -> showOwn(context.getSource().getPlayerOrException()))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("num", IntegerArgumentType.integer())
                                .suggests((context, builder) -> {
                                    builder.suggest(1);
                                    builder.suggest(10);
                                    builder.suggest(50);
                                    return builder.buildFuture();
                                })
                                .executes(context -> add(
                                        singleton(context.getSource().getPlayerOrException()),
                                        IntegerArgumentType.getInteger(context, "num")
                                ))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(context -> add(
                                                EntityArgument.getPlayers(context, "player"),
                                                IntegerArgumentType.getInteger(context, "num")
                                        )))))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("num", IntegerArgumentType.integer())
                                .suggests((context, builder) -> {
                                    builder.suggest(0);
                                    builder.suggest(1);
                                    builder.suggest(10);
                                    builder.suggest(50);
                                    return builder.buildFuture();
                                })
                                .executes(context -> set(
                                        singleton(context.getSource().getPlayerOrException()),
                                        IntegerArgumentType.getInteger(context, "num")
                                ))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(context -> set(
                                                EntityArgument.getPlayers(context, "player"),
                                                IntegerArgumentType.getInteger(context, "num")
                                        )))))
                .then(Commands.literal("get")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> showTarget(
                                        context.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(context, "player")
                                ))));
    }

    private static int showOwn(ServerPlayer player) {
        String key = CommonConfig.get().makeUp().signInCard()
                ? "has_sign_in_card_d"
                : "server_not_enable_sign_in_card";
        Component message = CommonConfig.get().makeUp().signInCard()
                ? SakuraComponent.get().trans(player, "format", key, SakuraPlayerData.get(player).getSignInCard()
                )
                : SakuraComponent.get().trans(player, "word", key);
        SakuraMessages.send(player, message);
        return 1;
    }

    private static int add(Collection<ServerPlayer> players, int amount) {
        for (ServerPlayer player : players) {
            IPlayerSignInData data = SakuraPlayerData.get(player);
            data.setSignInCard(data.getSignInCard() + amount);
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "format", "get_sign_in_card_d", amount
            ));
            SakuraPlayerData.saveAndSync(player);
        }
        return 1;
    }

    private static int set(Collection<ServerPlayer> players, int amount) {
        for (ServerPlayer player : players) {
            SakuraPlayerData.get(player).setSignInCard(amount);
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "format", "set_sign_in_card_d", amount
            ));
            SakuraPlayerData.saveAndSync(player);
        }
        return 1;
    }

    private static int showTarget(ServerPlayer source, ServerPlayer target) {
        SakuraMessages.send(source, SakuraComponent.get().trans(
                source,
                "format", "set_player_s_sign_in_card_d",
                target.getDisplayName().getString(),
                SakuraPlayerData.get(target).getSignInCard()
        ));
        return 1;
    }

    private static Collection<ServerPlayer> singleton(ServerPlayer player) {
        return java.util.Collections.singletonList(player);
    }
}
