package xin.vanilla.sakura.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.command.CommandDateTimeParser;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.DateUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 签到、领奖和签到后领奖三个同构指令。
 */
public final class SignActionCommand {
    private SignActionCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> buildSign() {
        return build(CommonConfig.get().command().commandSignIn(), SignActionCommand::sign);
    }

    public static LiteralArgumentBuilder<CommandSource> buildReward() {
        return build(CommonConfig.get().command().commandReward(), SignActionCommand::reward);
    }

    public static LiteralArgumentBuilder<CommandSource> buildSignAndReward() {
        return build(CommonConfig.get().command().commandSignInEx(), context -> sign(context, true));
    }

    private static LiteralArgumentBuilder<CommandSource> build(
            String literal,
            Command<CommandSource> command
    ) {
        return Commands.literal(literal)
                .executes(command)
                .then(Commands.argument("date", StringArgumentType.greedyString())
                        .suggests(dateSuggestions())
                        .executes(command));
    }

    private static int sign(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        return sign(context, SakuraPlayerData.get(player).isAutoRewarded());
    }

    private static int sign(CommandContext<CommandSource> context, boolean reward)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        IPlayerSignInData data = SakuraPlayerData.get(player);
        for (KeyValue<Date, ESignInType> entry : signDates(context, data)) {
            RewardManager.signIn(player, new SignInPacket(
                    DateUtils.toDateTimeString(entry.getKey()), reward, entry.getValue()
            ));
        }
        return 1;
    }

    private static int reward(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        IPlayerSignInData data = SakuraPlayerData.get(player);
        List<Date> dates = new ArrayList<>();
        try {
            String input = StringArgumentType.getString(context, "date");
            if ("all".equalsIgnoreCase(input)) {
                data.getSignInRecords().stream()
                        .filter(record -> !record.isRewarded())
                        .forEach(record -> dates.add(record.getCompensateTime()));
            } else {
                dates.add(DateUtils.getDate(parseDate(input)));
            }
        } catch (IllegalArgumentException ignored) {
            dates.add(DateUtils.getServerDate());
        }
        for (Date date : dates) {
            RewardManager.signIn(player, new SignInPacket(
                    DateUtils.toDateTimeString(date), true, ESignInType.REWARD
            ));
        }
        return 1;
    }

    private static List<KeyValue<Date, ESignInType>> signDates(
            CommandContext<CommandSource> context,
            IPlayerSignInData data
    ) throws CommandSyntaxException {
        List<KeyValue<Date, ESignInType>> dates = new ArrayList<>();
        try {
            String input = StringArgumentType.getString(context, "date");
            if (CommonConfig.get().makeUp().signInCard() && "all".equalsIgnoreCase(input)) {
                int added = 0;
                for (int offset = 1;
                     offset <= CommonConfig.get().makeUp().reSignInDays()
                             && added < data.getSignInCard();
                     offset++) {
                    Date date = DateUtils.addDay(DateUtils.getServerDate(), -offset);
                    if (!RewardManager.isSignedIn(data, date, false)) {
                        dates.add(new KeyValue<>(date, ESignInType.RE_SIGN_IN));
                        added++;
                    }
                }
            } else {
                Date date = DateUtils.getDate(parseDate(input));
                ESignInType type = DateUtils.toDateInt(date) == RewardManager.getCompensateDateInt()
                        ? ESignInType.SIGN_IN
                        : ESignInType.RE_SIGN_IN;
                dates.add(new KeyValue<>(date, type));
            }
        } catch (IllegalArgumentException ignored) {
            dates.add(new KeyValue<>(DateUtils.getServerDate(), ESignInType.SIGN_IN));
        }
        return dates;
    }

    private static long parseDate(String input) throws CommandSyntaxException {
        return CommandDateTimeParser.parse(
                input,
                CommandDateTimeParser.Kind.DATE,
                DateUtils.getLocalDateTime(DateUtils.getServerDate())
        );
    }

    private static SuggestionProvider<CommandSource> dateSuggestions() {
        return (context, builder) -> {
            LocalDateTime now = DateUtils.getLocalDateTime(DateUtils.getServerDate());
            builder.suggest(now.getYear() + " " + now.getMonthValue() + " " + now.getDayOfMonth());
            builder.suggest("~ ~ ~");
            builder.suggest("~ ~ ~-1");
            builder.suggest("all");
            return builder.buildFuture();
        };
    }
}
