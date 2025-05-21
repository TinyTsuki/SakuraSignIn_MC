package xin.vanilla.sakura.command;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import lombok.NonNull;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.PlayerDataAttachment;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.rewards.RewardList;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.*;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class SignInCommand {

    public static int HELP_INFO_NUM_PER_PAGE = 5;

    public static final List<KeyValue<String, String>> HELP_MESSAGE = new ArrayList<>() {{
        add(new KeyValue<>("/sakura help[ <page>]", "va_help"));                                             // 获取帮助信息
        add(new KeyValue<>("/sign[ <year> <month> <day>]", "sign"));                                         // 签到简洁版本
        add(new KeyValue<>("/reward[ <year> <month> <day>]", "reward"));                                     // 领取今天的奖励简洁版本
        add(new KeyValue<>("/signex[ <year> <month> <day>]", "signex"));                                     // 签到并领取奖励简洁版本
        add(new KeyValue<>("/cdk <key>", "cdk"));                                                            // 领取兑换码奖励
        add(new KeyValue<>("/sakura sign <year> <month> <day>", "va_sign"));                                 // 签到/补签指定日期
        add(new KeyValue<>("/sakura reward[ <year> <month> <day>]", "va_reward"));                           // 领取指定日期奖励
        add(new KeyValue<>("/sakura signex[ <year> <month> <day>]", "va_signex"));                           // 签到/补签并领取指定日期奖励
        add(new KeyValue<>("/sakura cdk <key>", "va_cdk"));                                                  // 签到/补签并领取指定日期奖励
        add(new KeyValue<>("/sakura card give <num>[ <player>]", "va_card_give"));                           // 给予玩家补签卡
        add(new KeyValue<>("/sakura card set <num>[ <player>]", "va_card_set"));                             // 设置玩家补签卡
        add(new KeyValue<>("/sakura card get <player>", "va_card_get"));                                     // 获取玩家补签卡
        add(new KeyValue<>("/sakura config get", "va_config_get"));                                          // 获取服务器配置项信息
        add(new KeyValue<>("/sakura config set date <year> <month> <day> <hour> <minute> <second>", "va_config_set_date"));    // 设置服务器时间
    }};

    /*
        1：绕过服务器原版的出生点保护系统，可以破坏出生点地形。
        2：使用原版单机一切作弊指令（除了/publish，因为其只能在单机使用，/debug也不能使用）。
        3：可以使用大多数多人游戏指令，例如/op，/ban（/debug属于3级OP使用的指令）。
        4：使用所有命令，可以使用/stop关闭服务器。
    */

    /**
     * 注册命令到命令调度器
     *
     * @param dispatcher 命令调度器，用于管理服务器中的所有命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // 提供日期建议的 SuggestionProvider
        SuggestionProvider<CommandSourceStack> dateSuggestions = (context, builder) -> {
            LocalDateTime localDateTime = DateUtils.getLocalDateTime(DateUtils.getServerDate());
            builder.suggest(localDateTime.getYear() + " " + localDateTime.getMonthValue() + " " + localDateTime.getDayOfMonth());
            builder.suggest("~ ~ ~");
            builder.suggest("~ ~ ~-1");
            builder.suggest("all");
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> datetimeSuggestions = (context, builder) -> {
            LocalDateTime localDateTime = DateUtils.getLocalDateTime(DateUtils.getServerDate());
            builder.suggest(localDateTime.getYear() + " " + localDateTime.getMonthValue() + " " + localDateTime.getDayOfMonth()
                    + " " + localDateTime.getHour() + " " + localDateTime.getMinute() + " " + localDateTime.getSecond());
            builder.suggest("~ ~ ~ ~ ~ ~");
            builder.suggest("~ ~ ~ ~ ~ ~-1");
            return builder.buildFuture();
        };
        // 提供布尔值建议的 SuggestionProvider
        SuggestionProvider<CommandSourceStack> booleanSuggestions = (context, builder) -> {
            builder.suggest("true");
            builder.suggest("false");
            return builder.buildFuture();
        };

        Command<CommandSourceStack> helpCommand = context -> {
            int page = 1;
            try {
                page = IntegerArgumentType.getInteger(context, "page");
            } catch (IllegalArgumentException ignored) {
            }
            int pages = (int) Math.ceil((double) HELP_MESSAGE.size() / HELP_INFO_NUM_PER_PAGE);
            if (page < 1 || page > pages) {
                throw new IllegalArgumentException("page must be between 1 and " + (HELP_MESSAGE.size() / HELP_INFO_NUM_PER_PAGE));
            }
            Component helpInfo = Component.literal("-----==== Sakura Sign In Help (" + page + "/" + pages + ") ====-----\n");
            for (int i = 0; (page - 1) * HELP_INFO_NUM_PER_PAGE + i < HELP_MESSAGE.size() && i < HELP_INFO_NUM_PER_PAGE; i++) {
                KeyValue<String, String> keyValue = HELP_MESSAGE.get((page - 1) * HELP_INFO_NUM_PER_PAGE + i);
                Component commandTips = Component.translatable(context.getSource().getPlayerOrException(), EI18nType.COMMAND, keyValue.getValue());
                commandTips.setColor(Color.GRAY.getRGB());
                helpInfo.append(keyValue.getKey())
                        .append(Component.literal(" -> ").setColor(Color.YELLOW.getRGB()))
                        .append(commandTips);
                if (i != HELP_MESSAGE.size() - 1) {
                    helpInfo.append("\n");
                }
            }
            ServerPlayer player = context.getSource().getPlayerOrException();
            SakuraUtils.sendMessage(player, helpInfo);
            return 1;
        };

        Command<CommandSourceStack> signInCommand = context -> {
            List<KeyValue<Date, ESignInType>> signInTimeList = new ArrayList<>();
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerSignInData signInData = PlayerDataAttachment.getData(player);
            try {
                String string = StringArgumentType.getString(context, "date");
                if (ServerConfig.SIGN_IN_CARD.get() && "all".equalsIgnoreCase(string)) {
                    int days = 0;
                    for (int i = 1; i <= ServerConfig.RE_SIGN_IN_DAYS.get() && days < signInData.getSignInCard(); i++) {
                        Date date = DateUtils.addDay(DateUtils.getServerDate(), -i);
                        if (signInData.getSignInRecords().stream().noneMatch(data -> DateUtils.toDateInt(data.getCompensateTime()) == DateUtils.toDateInt(date))) {
                            signInTimeList.add(new KeyValue<>(DateUtils.format(DateUtils.toString(date)), ESignInType.RE_SIGN_IN));
                            days++;
                        }
                    }
                } else {
                    long date = getRelativeLong(string, "date");
                    Date signDate = DateUtils.getDate(date);
                    if (DateUtils.toDateInt(signDate) == RewardManager.getCompensateDateInt()) {
                        signInTimeList.add(new KeyValue<>(signDate, ESignInType.SIGN_IN));
                    } else {
                        signInTimeList.add(new KeyValue<>(signDate, ESignInType.RE_SIGN_IN));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                signInTimeList.add(new KeyValue<>(DateUtils.getServerDate(), ESignInType.SIGN_IN));
            }
            for (KeyValue<Date, ESignInType> keyValue : signInTimeList) {
                RewardManager.signIn(player, new SignInPacket(DateUtils.toDateTimeString(keyValue.getKey()), signInData.isAutoRewarded(), keyValue.getValue()));
            }
            return 1;
        };
        Command<CommandSourceStack> rewardCommand = context -> {
            List<Date> rewardTimeList = new ArrayList<>();
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerSignInData signInData = PlayerDataAttachment.getData(player);
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
                RewardManager.signIn(player, new SignInPacket(DateUtils.toString(date), true, ESignInType.REWARD));
            }
            return 1;
        };
        Command<CommandSourceStack> signAndRewardCommand = context -> {
            List<KeyValue<Date, ESignInType>> signInTimeList = new ArrayList<>();
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerSignInData signInData = PlayerDataAttachment.getData(player);
            try {
                String string = StringArgumentType.getString(context, "date");
                if (ServerConfig.SIGN_IN_CARD.get() && "all".equalsIgnoreCase(string)) {
                    int days = 0;
                    for (int i = 1; i <= ServerConfig.RE_SIGN_IN_DAYS.get() && days < signInData.getSignInCard(); i++) {
                        Date date = DateUtils.addDay(DateUtils.getServerDate(), -i);
                        if (signInData.getSignInRecords().stream().noneMatch(data -> DateUtils.toDateInt(data.getCompensateTime()) == DateUtils.toDateInt(date))) {
                            signInTimeList.add(new KeyValue<>(date, ESignInType.RE_SIGN_IN));
                            days++;
                        }
                    }
                } else {
                    long date = getRelativeLong(string, "date");
                    Date signDate = DateUtils.getDate(date);
                    if (DateUtils.toDateInt(signDate) == RewardManager.getCompensateDateInt()) {
                        signInTimeList.add(new KeyValue<>(signDate, ESignInType.SIGN_IN));
                    } else {
                        signInTimeList.add(new KeyValue<>(signDate, ESignInType.RE_SIGN_IN));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                signInTimeList.add(new KeyValue<>(DateUtils.getServerDate(), ESignInType.SIGN_IN));
            }
            for (KeyValue<Date, ESignInType> keyValue : signInTimeList) {
                RewardManager.signIn(player, new SignInPacket(DateUtils.toDateTimeString(keyValue.getKey()), true, keyValue.getValue()));
            }
            return 1;
        };
        Command<CommandSourceStack> cdkCommand = context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerSignInData signInData = PlayerDataAttachment.getData(player);
            String string = StringArgumentType.getString(context, "key");
            if (signInData.getCdkRecords().stream()
                    .filter(keyValue -> DateUtils.toDateInt(keyValue.getValue().getKey()) == DateUtils.toDateInt(DateUtils.getServerDate()))
                    .filter(keyValue -> !keyValue.getValue().getValue())
                    .count() >= 5) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "cdk_error_too_many_times").setColor(0xFFFF0000));
            } else if (signInData.getCdkRecords().stream()
                    .filter(keyValue -> keyValue.getKey().equals(string))
                    .anyMatch(keyValue -> keyValue.getValue().getValue())) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "cdk_already_received").setColor(0xFFFFFF00));
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
                        SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "cdk_not_exist_or_already_received").setColor(0xFFFF0000));
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
                            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "cdk_expired").setColor(0xFFFF0000));
                        } else {
                            Component msg = Component.translatable(player, EI18nType.MESSAGE, "receive_reward_success");
                            rewardKeyValue.getValue().getKey().forEach(reward -> {
                                Component detail = reward.getName(SakuraUtils.getPlayerLanguage(player), true);
                                if (RewardManager.giveRewardToPlayer(player, signInData, reward)) {
                                    detail.setColor(Color.GREEN.getRGB());
                                } else {
                                    detail.setColor(Color.RED.getRGB());
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
        Command<CommandSourceStack> languageCommand = context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            PlayerSignInData signInData = PlayerDataAttachment.getData(player);
            String language = StringArgumentType.getString(context, "language");
            if (I18nUtils.getI18nFiles().contains(language)) {
                signInData.setLanguage(language);
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "player_default_language", language));
            } else if ("server".equalsIgnoreCase(language) || "client".equalsIgnoreCase(language)) {
                signInData.setLanguage(language);
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "player_default_language", language));
            } else {
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "language_not_exist").setColor(0xFFFF0000));
            }
            return 1;
        };

        LiteralArgumentBuilder<CommandSourceStack> sign = Commands.literal(CommonConfig.COMMAND_SIGN_IN.get()).executes(signInCommand)
                // 带有日期参数 -> 补签
                .then(Commands.argument("date", StringArgumentType.greedyString())
                        .suggests(dateSuggestions)
                        .executes(signInCommand)
                );
        LiteralArgumentBuilder<CommandSourceStack> reward = Commands.literal(CommonConfig.COMMAND_REWARD.get()).executes(rewardCommand)
                // 带有日期参数 -> 补签
                .then(Commands.argument("date", StringArgumentType.greedyString())
                        .suggests(dateSuggestions)
                        .executes(rewardCommand)
                );
        LiteralArgumentBuilder<CommandSourceStack> signex = Commands.literal(CommonConfig.COMMAND_SIGN_IN_EX.get()).executes(signAndRewardCommand)
                // 带有日期参数 -> 补签
                .then(Commands.argument("date", StringArgumentType.greedyString())
                        .suggests(dateSuggestions)
                        .executes(signAndRewardCommand)
                );
        LiteralArgumentBuilder<CommandSourceStack> cdk = Commands.literal(CommonConfig.COMMAND_CDK.get())
                // 补签 /sakura signex <year> <month> <day>
                .then(Commands.argument("key", StringArgumentType.greedyString())
                        .executes(cdkCommand)
                );
        LiteralArgumentBuilder<CommandSourceStack> language = Commands.literal(CommonConfig.COMMAND_LANGUAGE.get())
                // 设置语言 /sakura language <code>
                .then(Commands.argument("language", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("client");
                            builder.suggest("server");
                            I18nUtils.getI18nFiles().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(languageCommand)
                );
        LiteralArgumentBuilder<CommandSourceStack> card = Commands.literal(CommonConfig.COMMAND_CARD.get())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!ServerConfig.SIGN_IN_CARD.get()) {
                        SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_not_enable_sign_in_card"));
                    } else {
                        SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "has_sign_in_card_d", PlayerDataAttachment.getData(player).getSignInCard()));
                    }
                    return 1;
                })
                // region give
                // 增加/减少补签卡 /sakura card give <num> [<players>]
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("num", IntegerArgumentType.integer())
                                .suggests((context, builder) -> {
                                    builder.suggest(1);
                                    builder.suggest(10);
                                    builder.suggest(50);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    int num = IntegerArgumentType.getInteger(context, "num");
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    PlayerSignInData signInData = PlayerDataAttachment.getData(player);
                                    signInData.setSignInCard(signInData.getSignInCard() + num);
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "give_sign_in_card_d", num));
                                    PlayerDataAttachment.syncPlayerData(player);
                                    return 1;
                                })
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(context -> {
                                            int num = IntegerArgumentType.getInteger(context, "num");
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "player");
                                            for (ServerPlayer player : players) {
                                                PlayerSignInData signInData = PlayerDataAttachment.getData(player);
                                                signInData.setSignInCard(signInData.getSignInCard() + num);
                                                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "get_sign_in_card_d", num));
                                                PlayerDataAttachment.syncPlayerData(player);
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
                        .requires(source -> source.hasPermission(2))
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
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    PlayerSignInData signInData = PlayerDataAttachment.getData(player);
                                    signInData.setSignInCard(num);
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_sign_in_card_d", num));
                                    PlayerDataAttachment.syncPlayerData(player);
                                    return 1;
                                })
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(context -> {
                                            int num = IntegerArgumentType.getInteger(context, "num");
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "player");
                                            for (ServerPlayer player : players) {
                                                PlayerSignInData signInData = PlayerDataAttachment.getData(player);
                                                signInData.setSignInCard(num);
                                                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_sign_in_card_d", num));
                                                PlayerDataAttachment.syncPlayerData(player);
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
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    PlayerSignInData signInData = PlayerDataAttachment.getData(target);
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_player_s_sign_in_card_d", target.getDisplayName().getString(), signInData.getSignInCard()));
                                    PlayerDataAttachment.syncPlayerData(target);
                                    return 1;
                                })
                        )
                )
                // endregion get
                ;
        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config")
                // region 获取服务器配置
                .then(Commands.literal("get")
                        .requires(source -> source.hasPermission(ServerConfig.PERMISSION_SERVER_CONFIG_GET.get()))
                        .then(Commands.literal("autoSignIn")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_auto_sign", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.AUTO_SIGN_IN.get())));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("timeCoolingMethod")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ETimeCoolingMethod coolingMethod = ServerConfig.TIME_COOLING_METHOD.get();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "sign_in_time_cool_down_mode_s", coolingMethod.name()));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("timeCoolingTime")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Double time = ServerConfig.TIME_COOLING_TIME.get();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "sign_in_time_cool_down_refresh_time_f", time));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("timeCoolingInterval")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Double time = ServerConfig.TIME_COOLING_INTERVAL.get();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "sign_in_time_cool_down_refresh_interval_f", time));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("signInCard")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_sign_in_card", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.SIGN_IN_CARD.get())));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("reSignInDays")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int time = ServerConfig.RE_SIGN_IN_DAYS.get();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "max_sign_in_day_d", time));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("signInCardOnlyBaseReward")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_sign_in_card_only_basic_reward", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.get())));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("date")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_current_time_s", DateUtils.toDateTimeString(DateUtils.getServerDate())));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("playerDataSyncPacketSize")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "player_data_sync_packet_size_d", ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.get()));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("rewardAffectedByLuck")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_reward_affected_by_luck", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.REWARD_AFFECTED_BY_LUCK.get())));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("continuousRewardsRepeatable")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_continuous_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.CONTINUOUS_REWARDS_REPEATABLE.get())));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("cycleRewardsRepeatable")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_cycle_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), ServerConfig.CYCLE_REWARDS_REPEATABLE.get())));
                                    return 1;
                                })
                        )
                        .then(Commands.literal("language")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_default_language", ServerConfig.DEFAULT_LANGUAGE.get()));
                                    return 1;
                                })
                        )
                )
                // endregion 获取服务器配置
                // region 修改服务器配置
                // 设置服务器时间 /sakura config set date <year> <month> <day> <hour> <minute> <second>
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(ServerConfig.PERMISSION_SERVER_CONFIG_SET.get()))
                        .then(Commands.literal("date")
                                .then(Commands.argument("datetime", StringArgumentType.greedyString())
                                        .suggests(datetimeSuggestions)
                                        .executes(context -> {
                                            String string = StringArgumentType.getString(context, "datetime");
                                            long datetime = getRelativeLong(string, "datetime");
                                            Date date = DateUtils.getDate(datetime);
                                            ServerConfig.SERVER_TIME.set(DateUtils.toDateTimeString(new Date()));
                                            ServerConfig.ACTUAL_TIME.set(DateUtils.toDateTimeString(date));
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_server_time_s", DateUtils.toDateTimeString(date)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("autoSignIn")
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.AUTO_SIGN_IN.set(bool);
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_auto_sign", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("signInCard")
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.SIGN_IN_CARD.set(bool);
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_sign_in_card", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("reSignInDays")
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
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
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_max_sign_in_day_d", days));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("signInCardOnlyBaseReward")
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.set(bool);
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_sign_in_card_only_basic_reward", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("timeCoolingMethod")
                                .then(Commands.argument("method", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (ETimeCoolingMethod value : ETimeCoolingMethod.values()) {
                                                builder.suggest(value.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String method = StringArgumentType.getString(context, "method");
                                            ServerConfig.TIME_COOLING_METHOD.set(ETimeCoolingMethod.valueOf(method));
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_sign_in_time_cool_down_mode_s", method));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("timeCoolingTime")
                                .then(Commands.argument("time", DoubleArgumentType.doubleArg(-23.59, 23.59))
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
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_sign_in_time_cool_down_refresh_time_f", time));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("timeCoolingInterval")
                                .then(Commands.argument("time", DoubleArgumentType.doubleArg(0, 23.59f))
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
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_sign_in_time_cool_down_refresh_interval_f", time));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("playerDataSyncPacketSize")
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 1024))
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
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "set_player_data_sync_packet_size_d", size));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("rewardAffectedByLuck")
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.REWARD_AFFECTED_BY_LUCK.set(bool);
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_reward_affected_by_luck", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("continuousRewardsRepeatable")
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.CONTINUOUS_REWARDS_REPEATABLE.set(bool);
                                            RewardConfigManager.getRewardConfig().refreshContinuousRewardsRelation();
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_continuous_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("cycleRewardsRepeatable")
                                .then(Commands.argument("bool", StringArgumentType.word())
                                        .suggests(booleanSuggestions)
                                        .executes(context -> {
                                            String boolString = StringArgumentType.getString(context, "bool");
                                            boolean bool = StringUtils.stringToBoolean(boolString);
                                            ServerConfig.CYCLE_REWARDS_REPEATABLE.set(bool);
                                            RewardConfigManager.getRewardConfig().refreshCycleRewardsRelation();
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_enabled_or_not_cycle_rewards_repeatable", I18nUtils.enabled(SakuraUtils.getPlayerLanguage(player), bool)));
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("language")
                                .then(Commands.argument("language", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            I18nUtils.getI18nFiles().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String code = StringArgumentType.getString(context, "language");
                                            ServerConfig.DEFAULT_LANGUAGE.set(code);
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            SakuraUtils.broadcastMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_default_language", ServerConfig.DEFAULT_LANGUAGE.get()));
                                            return 1;
                                        })
                                )
                        )
                )
                // endregion 修改服务器配置
                ;

        // 注册简短的指令
        {
            // 签到 /sign
            if (CommonConfig.CONCISE_SIGN_IN.get()) {
                dispatcher.register(sign);
            }

            // 领取奖励 /reward
            if (CommonConfig.CONCISE_REWARD.get()) {
                dispatcher.register(reward);
            }

            // 签到并领取奖励 /signex
            if (CommonConfig.CONCISE_SIGN_IN_EX.get()) {
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

            // 设置语言 /language
            if (CommonConfig.CONCISE_LANGUAGE.get()) {
                dispatcher.register(language);
            }
        }

        // 注册有前缀的指令
        dispatcher.register(Commands.literal(SakuraUtils.getCommandPrefix())
                .executes(helpCommand)
                .then(Commands.literal("help")
                        .executes(helpCommand)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, (int) Math.ceil((double) HELP_MESSAGE.size() / HELP_INFO_NUM_PER_PAGE)))
                                .suggests((context, builder) -> {
                                    int totalPages = (int) Math.ceil((double) HELP_MESSAGE.size() / HELP_INFO_NUM_PER_PAGE);
                                    for (int i = 0; i < totalPages; i++) {
                                        builder.suggest(i + 1);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(helpCommand)
                        )
                )
                // 签到 /sakura sign
                .then(sign)
                // 奖励 /sakura reward
                .then(reward)
                // 签到并领取奖励 /sakura signex
                .then(signex)
                // 領取CDK獎勵 /sakura cdk
                .then(cdk)
                // 获取补签卡数量 /sakura card
                .then(card)
                // 设置语言 /sakura language
                .then(language)
                // 获取服务器配置 /sakura config get
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
                offset = switch (units[i]) {
                    case "year" -> DateUtils.getLocalDateTime(DateUtils.getServerDate()).getYear();
                    case "month" -> DateUtils.getLocalDateTime(DateUtils.getServerDate()).getMonthValue();
                    case "day" -> DateUtils.getLocalDateTime(DateUtils.getServerDate()).getDayOfMonth();
                    case "hour" -> DateUtils.getLocalDateTime(DateUtils.getServerDate()).getHour();
                    case "minute" -> DateUtils.getLocalDateTime(DateUtils.getServerDate()).getMinute();
                    case "second" -> DateUtils.getLocalDateTime(DateUtils.getServerDate()).getSecond();
                    default -> 0;
                };
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
}
