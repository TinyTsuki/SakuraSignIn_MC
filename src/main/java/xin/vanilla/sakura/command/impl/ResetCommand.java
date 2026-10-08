package xin.vanilla.sakura.command.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.banira.common.data.Component;

import java.io.IOException;
import java.util.Collection;

public final class ResetCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    private ResetCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal("reset").requires(source -> source.hasPermission(2))
                .then(Commands.literal("personalDate")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("all");
                                            RewardConfigManager.getRewardConfig().getPersonalDatePresets()
                                                    .forEach(preset -> builder.suggest("all".equals(preset.getId())
                                                            ? "preset all" : preset.getId()));
                                            return builder.buildFuture();
                                        }).executes(context -> {
                                            String target = StringArgumentType.getString(context, "target");
                                            return execute(context, target, "all".equals(target));
                                        }))
                                .then(Commands.literal("preset")
                                        .executes(context -> execute(context, "preset", false))
                                        .then(Commands.argument("preset", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    RewardConfigManager.getRewardConfig().getPersonalDatePresets()
                                                            .forEach(preset -> builder.suggest(preset.getId()));
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> execute(context,
                                                        StringArgumentType.getString(context, "preset"), false))))));
    }

    private static int execute(CommandContext<CommandSource> context, String preset, boolean all) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw new SimpleCommandExceptionType(message(source, "word", "delete_one_player").toVanilla()).create();
        }
        GameProfile profile = profiles.iterator().next();
        int cleared;
        try {
            cleared = SakuraPlayerData.resetPersonalDateClaims(profile.getId(), preset, all);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Unable to reset personal date claims for {} ({})", profile.getId(), preset, failure);
            source.sendFailure(message(source, "word", "reset_personal_date_failed").toVanilla());
            return 0;
        }
        if (cleared == 0) {
            source.sendFailure(message(source, "format", "reset_personal_date_empty", profile.getName(), preset).toVanilla());
            return 0;
        }
        ServerPlayerEntity player = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (player != null) {
            try {
                SakuraPlayerData.sync(player);
            } catch (RuntimeException failure) {
                LOGGER.warn("Personal date reset persisted, but synchronization failed for {}", profile.getId(), failure);
            }
        }
        source.sendSuccess(message(source, "format", "reset_personal_date_success",
                profile.getName(), preset, cleared).toVanilla(), true);
        return cleared;
    }

    private static Component message(CommandSource source, String section, String key, Object... args) {
        return source.getEntity() instanceof ServerPlayerEntity
                ? SakuraComponent.get().trans((ServerPlayerEntity) source.getEntity(), section, key, args)
                : SakuraComponent.get().trans(section, key, args);
    }
}
