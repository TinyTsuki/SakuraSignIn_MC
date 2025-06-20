package xin.vanilla.sakura.command;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import lombok.NonNull;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.CustomConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.KeyValue;
import xin.vanilla.sakura.data.RewardList;
import xin.vanilla.sakura.data.player.IPlayerSignInData;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.enums.*;
import xin.vanilla.sakura.network.packet.SignInToServer;
import xin.vanilla.sakura.rewards.RewardConfigManager;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SignInCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    public static List<KeyValue<String, EnumCommandType>> HELP_MESSAGE;

    private static void refreshHelpMessage() {
        HELP_MESSAGE = Arrays.stream(EnumCommandType.values())
                .map(type -> {
                    String command = SakuraUtils.getCommand(type);
                    if (StringUtils.isNotNullOrEmpty(command)) {
                        return new KeyValue<>(command, type);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .filter(command -> !command.getValue().isIgnore())
                .sorted(Comparator.comparing(command -> command.getValue().getSort()))
                .collect(Collectors.toList());
    }

    /**
     * 注册命令到命令调度器
     */
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        // 刷新帮助信息
        refreshHelpMessage();

        Command<CommandSource> helpCommand = context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            String command;
            int page;
            try {
                command = StringArgumentType.getString(context, "command");
                page = StringUtils.toInt(command);
            } catch (IllegalArgumentException ignored) {
                command = "";
                page = 1;
            }
            Component helpInfo;
            if (page > 0) {
                int pages = (int) Math.ceil((double) HELP_MESSAGE.size() / ServerConfig.HELP_INFO_NUM_PER_PAGE.get());
                helpInfo = Component.literal(StringUtils.format(ServerConfig.HELP_HEADER.get() + "\n", page, pages));
                for (int i = 0; (page - 1) * ServerConfig.HELP_INFO_NUM_PER_PAGE.get() + i < HELP_MESSAGE.size() && i < ServerConfig.HELP_INFO_NUM_PER_PAGE.get(); i++) {
                    KeyValue<String, EnumCommandType> keyValue = HELP_MESSAGE.get((page - 1) * ServerConfig.HELP_INFO_NUM_PER_PAGE.get() + i);
                    Component commandTips;
                    if (keyValue.getValue().name().toLowerCase().contains("concise")) {
                        commandTips = Component.translatable(SakuraUtils.getPlayerLanguage(player), EnumI18nType.COMMAND, "concise", SakuraUtils.getCommand(keyValue.getValue().replaceConcise()));
                    } else {
                        commandTips = Component.translatable(SakuraUtils.getPlayerLanguage(player), EnumI18nType.COMMAND, keyValue.getValue().name().toLowerCase());
                    }
                    commandTips.setColor(EnumMCColor.GRAY.getColor());
                    String com = "/" + keyValue.getKey();
                    helpInfo.append(Component.literal(com)
                                    .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, com))
                                    .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                            , Component.translatable(SakuraUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "click_to_suggest").toTextComponent()))
                            )
                            .append(new Component(" -> ").setColor(EnumMCColor.YELLOW.getColor()))
                            .append(commandTips);
                    if (i != HELP_MESSAGE.size() - 1) {
                        helpInfo.append("\n");
                    }
                }
                // 添加翻页按钮
                if (pages > 1) {
                    helpInfo.append("\n");
                    Component prevButton = Component.literal("<<< ");
                    if (page > 1) {
                        prevButton.setColor(EnumMCColor.AQUA.getColor())
                                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format("/%s %s %d", SakuraUtils.getCommandPrefix(), "help", page - 1)))
                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(SakuraUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "previous_page").toTextComponent()));
                    } else {
                        prevButton.setColor(EnumMCColor.DARK_AQUA.getColor());
                    }
                    helpInfo.append(prevButton);

                    helpInfo.append(Component.literal(String.format(" %s/%s "
                                    , StringUtils.padOptimizedLeft(page, String.valueOf(pages).length(), " ")
                                    , pages))
                            .setColor(EnumMCColor.WHITE.getColor()));

                    Component nextButton = Component.literal(" >>>");
                    if (page < pages) {
                        nextButton.setColor(EnumMCColor.AQUA.getColor())
                                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format("/%s %s %d", SakuraUtils.getCommandPrefix(), "help", page + 1)))
                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable(SakuraUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "next_page").toTextComponent()));
                    } else {
                        nextButton.setColor(EnumMCColor.DARK_AQUA.getColor());
                    }
                    helpInfo.append(nextButton);
                }
            } else {
                EnumCommandType type = EnumCommandType.valueOf(command);
                helpInfo = Component.empty();
                String com = "/" + SakuraUtils.getCommand(type);
                helpInfo.append(Component.literal(com)
                                .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, com))
                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                        , Component.translatable(SakuraUtils.getPlayerLanguage(player), EnumI18nType.MESSAGE, "click_to_suggest").toTextComponent()))
                        )
                        .append("\n")
                        .append(Component.translatable(SakuraUtils.getPlayerLanguage(player), EnumI18nType.COMMAND, command.toLowerCase() + "_detail").setColor(EnumMCColor.GRAY.getColor()));
            }
            SakuraUtils.sendMessage(player, helpInfo);
            return 1;
        };
        SuggestionProvider<CommandSource> helpSuggestions = (context, builder) -> {
            String input = getStringEmpty(context, "command");
            boolean isInputEmpty = StringUtils.isNullOrEmpty(input);
            int totalPages = (int) Math.ceil((double) HELP_MESSAGE.size() / ServerConfig.HELP_INFO_NUM_PER_PAGE.get());
            for (int i = 0; i < totalPages && isInputEmpty; i++) {
                builder.suggest(i + 1);
            }
            for (EnumCommandType type : Arrays.stream(EnumCommandType.values())
                    .filter(type -> type != EnumCommandType.HELP)
                    .filter(type -> !type.isIgnore())
                    .filter(type -> !type.name().toLowerCase().contains("concise"))
                    .filter(type -> isInputEmpty || type.name().toLowerCase().contains(input.toLowerCase()))
                    .sorted(Comparator.comparing(EnumCommandType::getSort))
                    .collect(Collectors.toList())) {
                builder.suggest(type.name());
            }
            return builder.buildFuture();
        };


        SuggestionProvider<CommandSource> dateSuggestions = (context, builder) -> {
            LocalDateTime localDateTime = DateUtils.getLocalDateTime(DateUtils.getServerDate());
            builder.suggest(localDateTime.getYear() + " " + localDateTime.getMonthValue() + " " + localDateTime.getDayOfMonth());
            builder.suggest("~ ~ ~");
            builder.suggest("~ ~ ~-1");
            builder.suggest("all");
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSource> datetimeSuggestions = (context, builder) -> {
            LocalDateTime localDateTime = DateUtils.getLocalDateTime(DateUtils.getServerDate());
            builder.suggest(localDateTime.getYear() + " " + localDateTime.getMonthValue() + " " + localDateTime.getDayOfMonth()
                    + " " + localDateTime.getHour() + " " + localDateTime.getMinute() + " " + localDateTime.getSecond());
            builder.suggest("~ ~ ~ ~ ~ ~");
            builder.suggest("~ ~ ~ ~ ~ ~-1");
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSource> booleanSuggestions = (context, builder) -> {
            builder.suggest("true");
            builder.suggest("false");
            return builder.buildFuture();
        };


        Command<CommandSource> languageCommand = context -> {
            notifyHelp(context);
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            String language = StringArgumentType.getString(context, "language");
            if (I18nUtils.getI18nFiles().contains(language)) {
                CustomConfig.setPlayerLanguage(SakuraUtils.getPlayerUUIDString(player), language);
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "player_default_language", language));
            } else if ("server".equalsIgnoreCase(language) || "client".equalsIgnoreCase(language)) {
                CustomConfig.setPlayerLanguage(SakuraUtils.getPlayerUUIDString(player), language);
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "player_default_language", language));
            } else {
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "language_not_exist").setColorArgb(0xFFFF0000));
            }
            return 1;
        };
        Command<CommandSource> virtualOpCommand = context -> {
            notifyHelp(context);
            CommandSource source = context.getSource();
            // 如果命令来自玩家
            if (source.getEntity() == null || source.getEntity() instanceof ServerPlayerEntity) {
                EnumOperationType type = EnumOperationType.fromString(StringArgumentType.getString(context, "operation"));
                EnumCommandType[] rules;
                try {
                    rules = Arrays.stream(StringArgumentType.getString(context, "rules").split(","))
                            .filter(StringUtils::isNotNullOrEmpty)
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .map(EnumCommandType::valueOf).toArray(EnumCommandType[]::new);
                } catch (IllegalArgumentException ignored) {
                    rules = new EnumCommandType[]{};
                }
                List<ServerPlayerEntity> targetList = new ArrayList<>();
                try {
                    targetList.addAll(EntityArgument.getPlayers(context, "player"));
                } catch (IllegalArgumentException ignored) {
                }
                String language = ServerConfig.DEFAULT_LANGUAGE.get();
                if (source.getEntity() != null && source.getEntity() instanceof ServerPlayerEntity) {
                    language = SakuraUtils.getPlayerLanguage(source.getPlayerOrException());
                }
                for (ServerPlayerEntity target : targetList) {
                    switch (type) {
                        case ADD:
                            VirtualPermissionManager.addVirtualPermission(target, rules);
                            break;
                        case SET:
                            VirtualPermissionManager.setVirtualPermission(target, rules);
                            break;
                        case DEL:
                        case REMOVE:
                            VirtualPermissionManager.delVirtualPermission(target, rules);
                            break;
                        case CLEAR:
                            VirtualPermissionManager.clearVirtualPermission(target);
                            break;
                    }
                    String permissions = VirtualPermissionManager.buildPermissionsString(VirtualPermissionManager.getVirtualPermission(target));
                    SakuraUtils.sendTranslatableMessage(target, I18nUtils.getKey(EnumI18nType.MESSAGE, "player_virtual_op"), target.getDisplayName().getString(), permissions);
                    if (source.getEntity() != null && source.getEntity() instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = source.getPlayerOrException();
                        if (!target.getStringUUID().equalsIgnoreCase(SakuraUtils.getPlayerUUIDString(player))) {
                            SakuraUtils.sendTranslatableMessage(player, I18nUtils.getKey(EnumI18nType.MESSAGE, "player_virtual_op"), target.getDisplayName().getString(), permissions);
                        }
                    } else {
                        source.sendSuccess(Component.translatable(language, EnumI18nType.MESSAGE, "player_virtual_op", target.getDisplayName().getString(), permissions).toChatComponent(), true);
                    }
                    // 更新权限信息
                    source.getServer().getPlayerList().sendPlayerPermissionLevel(target);
                }
            }
            return 1;
        };
        Command<CommandSource> signInCommand = context -> {
            notifyHelp(context);
            List<KeyValue<Date, EnumSignInType>> signInTimeList = new ArrayList<>();
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
            try {
                String string = StringArgumentType.getString(context, "date");
                if (ServerConfig.SIGN_IN_CARD.get() && "all".equalsIgnoreCase(string)) {
                    int days = 0;
                    for (int i = 1; i <= ServerConfig.RE_SIGN_IN_DAYS.get() && days < signInData.getSignInCard(); i++) {
                        Date date = DateUtils.addDay(DateUtils.getServerDate(), -i);
                        if (signInData.getSignInRecords().stream().noneMatch(data -> DateUtils.toDateInt(data.getCompensateTime()) == DateUtils.toDateInt(date))) {
                            signInTimeList.add(new KeyValue<>(DateUtils.format(DateUtils.toString(date)), EnumSignInType.RE_SIGN_IN));
                            days++;
                        }
                    }
                } else {
                    long date = getRelativeLong(string, "date");
                    Date signDate = DateUtils.getDate(date);
                    if (DateUtils.toDateInt(signDate) == RewardManager.getCompensateDateInt()) {
                        signInTimeList.add(new KeyValue<>(signDate, EnumSignInType.SIGN_IN));
                    } else {
                        signInTimeList.add(new KeyValue<>(signDate, EnumSignInType.RE_SIGN_IN));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                signInTimeList.add(new KeyValue<>(DateUtils.getServerDate(), EnumSignInType.SIGN_IN));
            }
            for (KeyValue<Date, EnumSignInType> keyValue : signInTimeList) {
                RewardManager.signIn(player, new SignInToServer(DateUtils.toDateTimeString(keyValue.getKey()), signInData.isAutoRewarded(), keyValue.getValue()));
            }
            return 1;
        };
        Command<CommandSource> rewardCommand = context -> {
            notifyHelp(context);
            List<Date> rewardTimeList = new ArrayList<>();
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
            try {
                String string = StringArgumentType.getString(context, "date");
                if ("all".equalsIgnoreCase(string)) {
                    signInData.getSignInRecords().stream()
                            .filter(data -> !data.isRewarded())
                            .forEach(data -> rewardTimeList.add(DateUtils.format(DateUtils.toString(data.getCompensateTime()))));
                } else {
                    long date = getRelativeLong(string, "date");
                    rewardTimeList.add(DateUtils.getDate(date));
                }
            } catch (IllegalArgumentException ignored) {
                rewardTimeList.add(DateUtils.getServerDate());
            }
            for (Date date : rewardTimeList) {
                RewardManager.signIn(player, new SignInToServer(DateUtils.toString(date), true, EnumSignInType.REWARD));
            }
            return 1;
        };
        Command<CommandSource> signAndRewardCommand = context -> {
            notifyHelp(context);
            List<KeyValue<Date, EnumSignInType>> signInTimeList = new ArrayList<>();
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
            try {
                String string = StringArgumentType.getString(context, "date");
                if (ServerConfig.SIGN_IN_CARD.get() && "all".equalsIgnoreCase(string)) {
                    int days = 0;
                    for (int i = 1; i <= ServerConfig.RE_SIGN_IN_DAYS.get() && days < signInData.getSignInCard(); i++) {
                        Date date = DateUtils.addDay(DateUtils.getServerDate(), -i);
                        if (signInData.getSignInRecords().stream().noneMatch(data -> DateUtils.toDateInt(data.getCompensateTime()) == DateUtils.toDateInt(date))) {
                            signInTimeList.add(new KeyValue<>(date, EnumSignInType.RE_SIGN_IN));
                            days++;
                        }
                    }
                } else {
                    long date = getRelativeLong(string, "date");
                    Date signDate = DateUtils.getDate(date);
                    if (DateUtils.toDateInt(signDate) == RewardManager.getCompensateDateInt()) {
                        signInTimeList.add(new KeyValue<>(signDate, EnumSignInType.SIGN_IN));
                    } else {
                        signInTimeList.add(new KeyValue<>(signDate, EnumSignInType.RE_SIGN_IN));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                signInTimeList.add(new KeyValue<>(DateUtils.getServerDate(), EnumSignInType.SIGN_IN));
            }
            for (KeyValue<Date, EnumSignInType> keyValue : signInTimeList) {
                RewardManager.signIn(player, new SignInToServer(DateUtils.toDateTimeString(keyValue.getKey()), true, keyValue.getValue()));
            }
            return 1;
        };
        Command<CommandSource> cdkCommand = context -> {
            notifyHelp(context);
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
            String string = StringArgumentType.getString(context, "key");
            if (signInData.getCdkRecords().stream()
                    .filter(keyValue -> DateUtils.toDateInt(keyValue.getValue().getKey()) == DateUtils.toDateInt(DateUtils.getServerDate()))
                    .filter(keyValue -> !keyValue.getValue().getValue())
                    .count() >= 5) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "cdk_error_too_many_times").setColorArgb(0xFFFF0000));
            } else if (signInData.getCdkRecords().stream()
                    .filter(keyValue -> keyValue.getKey().equals(string))
                    .anyMatch(keyValue -> keyValue.getValue().getValue())) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "cdk_already_received").setColorArgb(0xFFFFFF00));
            } else {
                List<KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>>> cdkRewards = RewardConfigManager.getRewardConfig().getCdkRewards();
                if (CollectionUtils.isNotNullOrEmpty(cdkRewards)) {
                    // 找到第一个匹配的项的下标
                    int indexToRemove = IntStream.range(0, cdkRewards.size())
                            .filter(i -> cdkRewards.get(i).getKey().getKey().equals(string))
                            .filter(i -> cdkRewards.get(i).getValue().getValue().get() > 0)
                            .findFirst()
                            .orElse(-1);
                    boolean error = true;
                    if (indexToRemove < 0) {
                        SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "cdk_not_exist_or_already_received").setColorArgb(0xFFFF0000));
                    } else {
                        KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> rewardKeyValue;
                        if (RewardConfigManager.getRewardConfig().getCdkRewards().get(indexToRemove).getValue().getValue().get() > 1) {
                            KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> value = RewardConfigManager.getRewardConfig().getCdkRewards().get(indexToRemove);
                            value.getValue().getValue().decrementAndGet();
                            rewardKeyValue = new KeyValue<>(value.getKey(), new KeyValue<>(value.getValue().getKey(), new AtomicInteger(1)));
                        } else {
                            rewardKeyValue = RewardConfigManager.getRewardConfig().getCdkRewards().remove(indexToRemove);
                        }
                        RewardConfigManager.saveRewardOption();
                        Date format = DateUtils.format(rewardKeyValue.getKey().getValue());
                        if (format.before(DateUtils.getServerDate())) {
                            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "cdk_expired").setColorArgb(0xFFFF0000));
                        } else {
                            Component msg = Component.translatable(player, EnumI18nType.MESSAGE, "receive_reward_success");
                            rewardKeyValue.getValue().getKey().forEach(reward -> {
                                Component detail = reward.getName(SakuraUtils.getPlayerLanguage(player), true);
                                if (RewardManager.giveRewardToPlayer(player, signInData, reward)) {
                                    detail.setColor(EnumMCColor.GREEN.getColor());
                                } else {
                                    detail.setColor(EnumMCColor.RED.getColor());
                                }
                                msg.append(", ").append(detail);
                            });
                            SakuraUtils.sendMessage(player, msg);
                            error = false;
                        }
                    }
                    signInData.getCdkRecords().add(new KeyValue<>(string, new KeyValue<>(DateUtils.getServerDate(), !error)));
                }
            }
            return 1;
        };


        LiteralArgumentBuilder<CommandSource> language = // region language
                Commands.literal(CommonConfig.COMMAND_LANGUAGE.get())
                        .then(Commands.argument("language", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("client");
                                    builder.suggest("server");
                                    I18nUtils.getI18nFiles().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(languageCommand)
                        ); // endregion language
        LiteralArgumentBuilder<CommandSource> virtualOp = // region virtualOp
                Commands.literal(CommonConfig.COMMAND_VIRTUAL_OP.get())
                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                        .then(Commands.argument("operation", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest(EnumOperationType.ADD.name().toLowerCase());
                                    builder.suggest(EnumOperationType.SET.name().toLowerCase());
                                    builder.suggest(EnumOperationType.DEL.name().toLowerCase());
                                    builder.suggest(EnumOperationType.CLEAR.name().toLowerCase());
                                    builder.suggest(EnumOperationType.GET.name().toLowerCase());
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(virtualOpCommand)
                                        .then(Commands.argument("rules", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> {
                                                    String operation = StringArgumentType.getString(context, "operation");
                                                    if (operation.equalsIgnoreCase(EnumOperationType.GET.name().toLowerCase())
                                                            || operation.equalsIgnoreCase(EnumOperationType.CLEAR.name().toLowerCase())
                                                            || operation.equalsIgnoreCase(EnumOperationType.LIST.name().toLowerCase())) {
                                                        return builder.buildFuture();
                                                    }
                                                    String input = getStringEmpty(context, "rules").replace(" ", ",");
                                                    String[] split = input.split(",");
                                                    String current = input.endsWith(",") ? "" : split[split.length - 1];
                                                    for (EnumCommandType value : Arrays.stream(EnumCommandType.values())
                                                            .filter(EnumCommandType::isOp)
                                                            .filter(type -> Arrays.stream(split).noneMatch(in -> in.equalsIgnoreCase(type.name())))
                                                            .filter(type -> StringUtils.isNullOrEmptyEx(current) || type.name().toLowerCase().contains(current.toLowerCase()))
                                                            .sorted(Comparator.comparing(EnumCommandType::getSort))
                                                            .collect(Collectors.toList())) {
                                                        String suggest = value.name();
                                                        if (input.endsWith(",")) {
                                                            suggest = input + suggest;
                                                        }
                                                        builder.suggest(suggest);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(virtualOpCommand)
                                        )
                                )
                        );// endregion virtualOp
        LiteralArgumentBuilder<CommandSource> sign = // region sign
                Commands.literal(CommonConfig.COMMAND_SIGN.get())
                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.SIGN))
                        .executes(signInCommand)
                        // 带有日期参数 -> 补签
                        .then(Commands.argument("date", StringArgumentType.greedyString())
                                .suggests(dateSuggestions)
                                .executes(signInCommand)
                        ); // endregion sign
        LiteralArgumentBuilder<CommandSource> signex = // region signex
                Commands.literal(CommonConfig.COMMAND_SIGNEX.get())
                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.SIGNEX))
                        .executes(signAndRewardCommand)
                        // 带有日期参数 -> 补签
                        .then(Commands.argument("date", StringArgumentType.greedyString())
                                .suggests(dateSuggestions)
                                .executes(signAndRewardCommand)
                        ); // endregion signex
        LiteralArgumentBuilder<CommandSource> reward = // region reward
                Commands.literal(CommonConfig.COMMAND_REWARD.get())
                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.REWARD))
                        .executes(rewardCommand)
                        // 带有日期参数 -> 补签
                        .then(Commands.argument("date", StringArgumentType.greedyString())
                                .suggests(dateSuggestions)
                                .executes(rewardCommand)
                        ); // endregion reward
        LiteralArgumentBuilder<CommandSource> cdk = // region cdk
                Commands.literal(CommonConfig.COMMAND_CDK.get())
                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.CDK))
                        // 补签 /sakura signex <year> <month> <day>
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(cdkCommand)
                        ); // endregion cdk
        LiteralArgumentBuilder<CommandSource> card = // region card
                Commands.literal(CommonConfig.COMMAND_CARD.get())
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                            if (!ServerConfig.SIGN_IN_CARD.get()) {
                                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_not_enable_sign_in_card"));
                            } else {
                                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "has_sign_in_card_d", PlayerSignInDataCapability.getData(player).getSignInCard()));
                            }
                            return 1;
                        })
                        // region give
                        // 增加/减少补签卡 /sakura card give <num> [<players>]
                        .then(Commands.literal("give")
                                .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.CARD_SET))
                                .then(Commands.argument("num", IntegerArgumentType.integer())
                                        .suggests((context, builder) -> {
                                            builder.suggest(1);
                                            builder.suggest(10);
                                            builder.suggest(50);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            int num = IntegerArgumentType.getInteger(context, "num");
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                            signInData.setSignInCard(signInData.getSignInCard() + num);
                                            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "give_sign_in_card_d", num));
                                            PlayerSignInDataCapability.syncPlayerData(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(context -> {
                                                    int num = IntegerArgumentType.getInteger(context, "num");
                                                    Collection<ServerPlayerEntity> players = EntityArgument.getPlayers(context, "player");
                                                    for (ServerPlayerEntity player : players) {
                                                        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                                        signInData.setSignInCard(signInData.getSignInCard() + num);
                                                        SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "get_sign_in_card_d", num));
                                                        PlayerSignInDataCapability.syncPlayerData(player);
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        // endregion give
                        // region set
                        // 设置补签卡数量 /sakura card set <num> [<players>]
                        .then(Commands.literal("set")
                                .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.CARD_SET))
                                .then(Commands.argument("num", IntegerArgumentType.integer())
                                        .suggests((context, builder) -> {
                                            builder.suggest(0);
                                            builder.suggest(1);
                                            builder.suggest(10);
                                            builder.suggest(50);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            int num = IntegerArgumentType.getInteger(context, "num");
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                            signInData.setSignInCard(num);
                                            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_sign_in_card_d", num));
                                            PlayerSignInDataCapability.syncPlayerData(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(context -> {
                                                    int num = IntegerArgumentType.getInteger(context, "num");
                                                    Collection<ServerPlayerEntity> players = EntityArgument.getPlayers(context, "player");
                                                    for (ServerPlayerEntity player : players) {
                                                        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                                        signInData.setSignInCard(num);
                                                        SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_sign_in_card_d", num));
                                                        PlayerSignInDataCapability.syncPlayerData(player);
                                                    }
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        // endregion set
                        // region get
                        // 获取补签卡数量 /sakura card get [<player>]
                        .then(Commands.literal("get")
                                .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.CARD_GET))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(target);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_player_s_sign_in_card_d", target.getDisplayName().getString(), signInData.getSignInCard()));
                                            PlayerSignInDataCapability.syncPlayerData(target);
                                            return 1;
                                        })
                                )
                        )
                // endregion get
                ;  // endregion card
        LiteralArgumentBuilder<CommandSource> config =  // region config
                Commands.literal("config")
                        // region 时间
                        .then(Commands.literal("date")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_current_time_s", DateUtils.toDateTimeString(DateUtils.getServerDate())));
                                    return 1;
                                })
                                .then(Commands.argument("datetime", StringArgumentType.greedyString())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests(datetimeSuggestions)
                                        .executes(context -> {
                                            String string = StringArgumentType.getString(context, "datetime");
                                            long datetime = getRelativeLong(string, "datetime");
                                            Date date = DateUtils.getDate(datetime);
                                            ServerConfig.SERVER_TIME.set(DateUtils.toDateTimeString(new Date()));
                                            ServerConfig.ACTUAL_TIME.set(DateUtils.toDateTimeString(date));
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_server_time_s", DateUtils.toDateTimeString(date)));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 时间
                        // region 自动签到
                        .then(Commands.literal("autoSignIn")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_auto_sign", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.AUTO_SIGN_IN.get())));
                                    return 1;
                                })
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.AUTO_SIGN_IN.set(bool);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_auto_sign", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 自动签到
                        // region 补签卡
                        .then(Commands.literal("signInCard")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_sign_in_card", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.SIGN_IN_CARD.get())));
                                    return 1;
                                })
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.SIGN_IN_CARD.set(bool);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_sign_in_card", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 补签卡
                        // region 补签范围
                        .then(Commands.literal("reSignInDays")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    int time = ServerConfig.RE_SIGN_IN_DAYS.get();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "max_sign_in_day_d", time));
                                    return 1;
                                })
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests((context, builder) -> {
                                            builder.suggest(1);
                                            builder.suggest(7);
                                            builder.suggest(30);
                                            builder.suggest(365);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            int days = IntegerArgumentType.getInteger(context, "days");
                                            ServerConfig.RE_SIGN_IN_DAYS.set(days);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_max_sign_in_day_d", days));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 补签范围
                        // region 补签仅基础奖励
                        .then(Commands.literal("signInCardOnlyBaseReward")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_sign_in_card_only_basic_reward", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.get())));
                                    return 1;
                                })
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.set(bool);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_sign_in_card_only_basic_reward", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 补签仅基础奖励
                        // region 签到冷却方式
                        .then(Commands.literal("timeCoolingMethod")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    EnumTimeCoolingMethod coolingMethod = EnumTimeCoolingMethod.valueOf(ServerConfig.TIME_COOLING_METHOD.get());
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "sign_in_time_cool_down_mode_s", coolingMethod.name()));
                                    return 1;
                                })
                                .then(Commands.argument("method", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests((context, builder) -> {
                                            for (EnumTimeCoolingMethod value : EnumTimeCoolingMethod.values()) {
                                                builder.suggest(value.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String method = StringArgumentType.getString(context, "method");
                                            ServerConfig.TIME_COOLING_METHOD.set(EnumTimeCoolingMethod.valueOf(method).name());
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_sign_in_time_cool_down_mode_s", method));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 签到冷却方式
                        // region 签到冷却时间
                        .then(Commands.literal("timeCoolingTime")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    Double time = ServerConfig.TIME_COOLING_TIME.get();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "sign_in_time_cool_down_refresh_time_f", time));
                                    return 1;
                                })
                                .then(Commands.argument("time", DoubleArgumentType.doubleArg(-23.59, 23.59))
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests((context, builder) -> {
                                            builder.suggest("0.00");
                                            builder.suggest("4.00");
                                            builder.suggest("12.00");
                                            builder.suggest("23.59");
                                            builder.suggest("-23.59");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            double time = DoubleArgumentType.getDouble(context, "time");
                                            SignInCommand.checkTime(time);
                                            ServerConfig.TIME_COOLING_TIME.set(time);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_sign_in_time_cool_down_refresh_time_f", time));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 签到冷却时间
                        // region 签到冷却间隔
                        .then(Commands.literal("timeCoolingInterval")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    Double time = ServerConfig.TIME_COOLING_INTERVAL.get();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "sign_in_time_cool_down_refresh_interval_f", time));
                                    return 1;
                                })
                                .then(Commands.argument("time", DoubleArgumentType.doubleArg(0, 23.59f))
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests((context, builder) -> {
                                            builder.suggest("0.00");
                                            builder.suggest("6.00");
                                            builder.suggest("12.34");
                                            builder.suggest("23.59");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            double time = DoubleArgumentType.getDouble(context, "time");
                                            SignInCommand.checkTime(time);
                                            ServerConfig.TIME_COOLING_INTERVAL.set(time);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_sign_in_time_cool_down_refresh_interval_f", time));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 签到冷却间隔
                        // region 玩家数据包大小
                        .then(Commands.literal("playerDataSyncPacketSize")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "player_data_sync_packet_size_d", ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.get()));
                                    return 1;
                                })
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 1024))
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests((context, builder) -> {
                                            builder.suggest(1);
                                            builder.suggest(10);
                                            builder.suggest(100);
                                            builder.suggest(1024);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            int size = IntegerArgumentType.getInteger(context, "size");
                                            ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.set(size);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "set_player_data_sync_packet_size_d", size));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 玩家数据包大小
                        // region 奖励概率受幸运影响
                        .then(Commands.literal("rewardAffectedByLuck")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_reward_affected_by_luck", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.REWARD_AFFECTED_BY_LUCK.get())));
                                    return 1;
                                })
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.REWARD_AFFECTED_BY_LUCK.set(bool);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_reward_affected_by_luck", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 奖励概率受幸运影响
                        // region 连续签到奖励重复领取
                        .then(Commands.literal("continuousRewardsRepeatable")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_continuous_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.CONTINUOUS_REWARDS_REPEATABLE.get())));
                                    return 1;
                                })
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.CONTINUOUS_REWARDS_REPEATABLE.set(bool);
                                            RewardConfigManager.getRewardConfig().refreshContinuousRewardsRelation();
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_continuous_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 连续签到奖励重复领取
                        // region 循环签到奖励重复领取
                        .then(Commands.literal("cycleRewardsRepeatable")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_cycle_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.CYCLE_REWARDS_REPEATABLE.get())));
                                    return 1;
                                })
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.CYCLE_REWARDS_REPEATABLE.set(bool);
                                            RewardConfigManager.getRewardConfig().refreshCycleRewardsRelation();
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_enabled_or_not_cycle_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        // endregion 循环签到奖励重复领取
                        // region 服务器默认语言
                        .then(Commands.literal("language")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_default_language", ServerConfig.DEFAULT_LANGUAGE.get()));
                                    return 1;
                                })
                                .then(Commands.argument("language", StringArgumentType.word())
                                        .requires(source -> SakuraUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                                        .suggests((context, builder) -> {
                                            I18nUtils.getI18nFiles().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String code = StringArgumentType.getString(context, "language");
                                            ServerConfig.DEFAULT_LANGUAGE.set(code);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_default_language", ServerConfig.DEFAULT_LANGUAGE.get()));
                                            return 1;
                                        })
                                )
                        )
                // endregion 服务器默认语言
                ; // endregion config

        // 注册简短的指令
        {

            // 设置语言 /language
            if (CommonConfig.CONCISE_LANGUAGE.get()) {
                dispatcher.register(language);
            }

            // 设置虚拟权限 /opv
            if (CommonConfig.CONCISE_VIRTUAL_OP.get()) {
                dispatcher.register(virtualOp);
            }

            // 签到 /sign
            if (CommonConfig.CONCISE_SIGN.get()) {
                dispatcher.register(sign);
            }

            // 领取奖励 /reward
            if (CommonConfig.CONCISE_REWARD.get()) {
                dispatcher.register(reward);
            }

            // 签到并领取奖励 /signex
            if (CommonConfig.CONCISE_SIGNEX.get()) {
                dispatcher.register(signex);
            }

            // 領取CDK奖励 /cdk
            if (CommonConfig.CONCISE_CDK.get()) {
                dispatcher.register(cdk);
            }

            // 获取补签卡数量 /card
            if (CommonConfig.CONCISE_CARD.get()) {
                dispatcher.register(card);
            }

        }

        // 注册有前缀的指令
        dispatcher.register(Commands.literal(SakuraUtils.getCommandPrefix())
                .executes(helpCommand)
                .then(Commands.literal("help")
                        .executes(helpCommand)
                        .then(Commands.argument("command", StringArgumentType.word())
                                .suggests(helpSuggestions)
                                .executes(helpCommand)
                        )
                )
                // 设置语言 /sakura language
                .then(language)
                // 设置虚拟权限 /sakura opv
                .then(virtualOp)
                // 签到 /sakura sign
                .then(sign)
                // 签到并领取奖励 /sakura signex
                .then(signex)
                // 奖励 /sakura reward
                .then(reward)
                // 领取CKD奖励 /sakura cdk
                .then(cdk)
                // 获取补签卡数量 /sakura card
                .then(card)
                // 服务器配置 /sakura config
                .then(config)
        );
    }

    /**
     * 校验时间是否合法
     */
    private static void checkTime(double time) throws CommandSyntaxException {
        boolean throwException = false;
        if (time < -23.59 || time > 23.59) {
            throwException = true;
        } else {
            String format = String.format("%05.2f", time);
            String[] split = format.split("\\.");
            if (split.length != 2) {
                throwException = true;
            } else {
                int hour = StringUtils.toInt(split[0]);
                int minute = StringUtils.toInt(split[1]);
                if (hour < -23 || hour > 23) {
                    throwException = true;
                } else if (minute < 0 || minute > 59) {
                    throwException = true;
                }
            }
        }
        if (throwException) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create(time);
        }
    }

    private static long getRelativeLong(String string, @NonNull String name) throws CommandSyntaxException {
        if (StringUtils.isNullOrEmptyEx(string)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().create(string);
        }
        String[] split = string.split(" ");
        String[] units;
        if ((name.equalsIgnoreCase("date") && split.length == 3)) {
            units = new String[]{"year", "month", "day"};
        } else if ((name.equalsIgnoreCase("time") && split.length == 3)) {
            units = new String[]{"hour", "minute", "second"};
        } else if (name.equalsIgnoreCase("datetime") && split.length == 6) {
            units = new String[]{"year", "month", "day", "hour", "minute", "second"};
        } else {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().create(string);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < split.length; i++) {
            int input;
            int offset;
            String inputString = split[i];
            if (inputString.startsWith("_") || inputString.startsWith("~")) {
                switch (units[i]) {
                    case "year":
                        offset = DateUtils.getLocalDateTime(DateUtils.getServerDate()).getYear();
                        break;
                    case "month":
                        offset = DateUtils.getLocalDateTime(DateUtils.getServerDate()).getMonthValue();
                        break;
                    case "day":
                        offset = DateUtils.getLocalDateTime(DateUtils.getServerDate()).getDayOfMonth();
                        break;
                    case "hour":
                        offset = DateUtils.getLocalDateTime(DateUtils.getServerDate()).getHour();
                        break;
                    case "minute":
                        offset = DateUtils.getLocalDateTime(DateUtils.getServerDate()).getMinute();
                        break;
                    case "second":
                        offset = DateUtils.getLocalDateTime(DateUtils.getServerDate()).getSecond();
                        break;
                    default:
                        offset = 0;
                        break;
                }
                if (inputString.equalsIgnoreCase("_") || inputString.equalsIgnoreCase("~")) {
                    inputString = "0";
                } else {
                    inputString = inputString.substring(1);
                }
            } else {
                offset = 0;
            }
            try {
                input = Integer.parseInt(inputString);
            } catch (NumberFormatException e) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().create(inputString);
            }
            if (units[i].equalsIgnoreCase("year")) {
                result.append(String.format("%04d", offset + input));
            } else {
                result.append(String.format("%02d", offset + input));
            }
        }
        return Long.parseLong(result.toString());
    }

    private static String getStringEmpty(CommandContext<?> context, String name) {
        return getStringDefault(context, name, "");
    }

    private static String getStringDefault(CommandContext<?> context, String name, String defaultValue) {
        String result;
        try {
            result = StringArgumentType.getString(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    /**
     * 若为第一次使用指令则进行提示
     */
    private static void notifyHelp(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            IPlayerSignInData data = PlayerSignInDataCapability.getData(player);
            if (!data.isNotified()) {
                Component button = Component.literal("/" + SakuraUtils.getCommandPrefix())
                        .setColor(EnumMCColor.AQUA.getColor())
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + SakuraUtils.getCommandPrefix()))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/" + SakuraUtils.getCommandPrefix())
                                .toTextComponent())
                        );
                SakuraUtils.sendMessage(player, Component.translatable(EnumI18nType.MESSAGE, "notify_help", button));
                data.setNotified(true);
            }
        }
    }
}
