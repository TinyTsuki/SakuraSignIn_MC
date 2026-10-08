package xin.vanilla.sakura.command.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.command.DeletionDateParser;
import xin.vanilla.sakura.data.time.SakuraClock;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.reward.RewardManager;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;

public final class DeleteCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    private DeleteCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("delete").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(context -> execute(context, null))
                        .then(Commands.argument("date", StringArgumentType.greedyString())
                                .suggests((context, builder) -> builder.suggest(currentDay().toString()).buildFuture())
                                .executes(context -> execute(context, StringArgumentType.getString(context, "date")))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, String date)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw new SimpleCommandExceptionType(message(source, "word", "delete_one_player").toVanilla()).create();
        }
        GameProfile profile = profiles.iterator().next();
        LocalDate current = currentDay();
        LocalDate day = date == null ? current : DeletionDateParser.parse(date, current);
        try {
            if (!SakuraPlayerData.deleteDay(profile.getId(), day, current)) {
                source.sendFailure(message(source, "format", "delete_sign_in_missing", profile.getName(), day.toString()).toVanilla());
                return 0;
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Unable to delete sign-in day {} for {}", day, profile.getId(), failure);
            source.sendFailure(message(source, "word", "delete_sign_in_failed").toVanilla());
            return 0;
        }
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (player != null) {
            try {
                SakuraPlayerData.sync(player);
                SakuraNetwork.syncMonth(player, YearMonth.from(day).toString());
            } catch (RuntimeException failure) {
                LOGGER.warn("Deleted sign-in day persisted, but client sync failed for {}", profile.getId(), failure);
            }
        }
        source.sendSuccess(message(source, "format", "delete_sign_in_success", profile.getName(), day.toString()).toVanilla(), true);
        return 1;
    }

    private static LocalDate currentDay() {
        return RewardManager.getCompensateDate(SakuraClock.serverNow()).toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static xin.vanilla.banira.common.data.Component message(
            CommandSourceStack source, String section, String key, Object... args) {
        return source.getEntity() instanceof ServerPlayer
                ? SakuraComponent.get().trans((ServerPlayer) source.getEntity(), section, key, args)
                : SakuraComponent.get().trans(section, key, args);
    }
}
