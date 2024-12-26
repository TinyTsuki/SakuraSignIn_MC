package xin.vanilla.mc.command;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import lombok.NonNull;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.capability.IPlayerSignInData;
import xin.vanilla.mc.capability.PlayerSignInDataCapability;
import xin.vanilla.mc.config.KeyValue;
import xin.vanilla.mc.config.ServerConfig;
import xin.vanilla.mc.enums.ESignInType;
import xin.vanilla.mc.enums.ETimeCoolingMethod;
import xin.vanilla.mc.network.SignInPacket;
import xin.vanilla.mc.rewards.RewardManager;
import xin.vanilla.mc.util.DateUtils;
import xin.vanilla.mc.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static xin.vanilla.mc.util.I18nUtils.getI18nKey;

public class SignInCommand {

    public static int HELP_INFO_NUM_PER_PAGE = 5;

    public static final List<KeyValue<String, String>> HELP_MESSAGE = new ArrayList<KeyValue<String, String>>() {{
        add(new KeyValue<>("/va help[ <page>]", "va_help"));                                                 // 获取帮助信息
        add(new KeyValue<>("/sign[ <year> <month> <day>]", "sign"));                                         // 签到简洁版本
        add(new KeyValue<>("/reward[ <year> <month> <day>]", "reward"));                                     // 领取今天的奖励简洁版本
        add(new KeyValue<>("/signex[ <year> <month> <day>]", "signex"));                                     // 签到并领取奖励简洁版本
        add(new KeyValue<>("/va sign <year> <month> <day>", "va_sign"));                                     // 签到/补签指定日期
        add(new KeyValue<>("/va reward[ <year> <month> <day>]", "va_reward"));                               // 领取指定日期奖励
        add(new KeyValue<>("/va signex[ <year> <month> <day>]", "va_signex"));                               // 签到/补签并领取指定日期奖励
        add(new KeyValue<>("/va card give <num>[ <player>]", "va_card_give"));                               // 给予玩家补签卡
        add(new KeyValue<>("/va card set <num>[ <player>]", "va_card_set"));                                 // 设置玩家补签卡
        add(new KeyValue<>("/va card get <player>", "va_card_get"));                                         // 获取玩家补签卡
        add(new KeyValue<>("/va config get", "va_config_get"));                                              // 获取服务器配置项信息
        add(new KeyValue<>("/va config set date <year> <month> <day> <hour> <minute> <second>", "va_config_set_date"));    // 设置服务器时间
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
    public static void register(CommandDispatcher<CommandSource> dispatcher) {

        // 提供日期建议的 SuggestionProvider
        SuggestionProvider<CommandSource> dateSuggestions = (context, builder) -> {
            LocalDateTime localDateTime = DateUtils.getLocalDateTime(DateUtils.getServerDate());
            builder.suggest(localDateTime.getYear() + " " + localDateTime.getMonthValue() + " " + localDateTime.getDayOfMonth());
            builder.suggest("~ ~ ~");
            builder.suggest("~ ~ ~-1");
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
        // 提供布尔值建议的 SuggestionProvider
        SuggestionProvider<CommandSource> booleanSuggestions = (context, builder) -> {
            builder.suggest("true");
            builder.suggest("false");
            return builder.buildFuture();
        };

        Command<CommandSource> signInCommand = context -> {
            Date signInTime;
            ESignInType signInType;
            try {
                long date = getRelativeLong(context, "date");
                signInTime = DateUtils.getDate(date);
                signInType = ESignInType.RE_SIGN_IN;
            } catch (IllegalArgumentException ignored) {
                signInTime = DateUtils.getServerDate();
                signInType = ESignInType.SIGN_IN;
            }
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
            RewardManager.signIn(player, new SignInPacket(signInTime, signInData.isAutoRewarded(), signInType));
            return 1;
        };
        Command<CommandSource> rewardCommand = context -> {
            Date rewardTime;
            try {
                long date = getRelativeLong(context, "date");
                rewardTime = DateUtils.getDate(date);
            } catch (IllegalArgumentException ignored) {
                rewardTime = DateUtils.getServerDate();
            }
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            RewardManager.signIn(player, new SignInPacket(rewardTime, true, ESignInType.REWARD));
            return 1;
        };
        Command<CommandSource> signAndRewardCommand = context -> {
            Date signInTime;
            ESignInType signInType;
            try {
                long date = getRelativeLong(context, "date");
                signInTime = DateUtils.getDate(date);
                signInType = ESignInType.RE_SIGN_IN;
            } catch (IllegalArgumentException ignored) {
                signInTime = DateUtils.getServerDate();
                signInType = ESignInType.SIGN_IN;
            }
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            RewardManager.signIn(player, new SignInPacket(signInTime, true, signInType));
            return 1;
        };
        Command<CommandSource> helpCommand = context -> {
            int page = 1;
            try {
                page = IntegerArgumentType.getInteger(context, "page");
            } catch (IllegalArgumentException ignored) {
            }
            int pages = (int) Math.ceil((double) HELP_MESSAGE.size() / HELP_INFO_NUM_PER_PAGE);
            if (page < 1 || page > pages) {
                throw new IllegalArgumentException("page must be between 1 and " + (HELP_MESSAGE.size() / HELP_INFO_NUM_PER_PAGE));
            }
            StringTextComponent helpInfo = new StringTextComponent("-----==== Sakura Sign In Help (" + page + "/" + pages + ") ====-----\n");
            for (int i = 0; (page - 1) * HELP_INFO_NUM_PER_PAGE + i < HELP_MESSAGE.size() && i < HELP_INFO_NUM_PER_PAGE; i++) {
                KeyValue<String, String> keyValue = HELP_MESSAGE.get((page - 1) * HELP_INFO_NUM_PER_PAGE + i);
                TranslationTextComponent commandTips = new TranslationTextComponent("command." + SakuraSignIn.MODID + "." + keyValue.getValue());
                commandTips.withStyle(Style.EMPTY.withColor(TextFormatting.GRAY));
                helpInfo.append(keyValue.getKey())
                        .append(new StringTextComponent(" -> ").withStyle(Style.EMPTY.withColor(TextFormatting.YELLOW)))
                        .append(commandTips);
                if (i != HELP_MESSAGE.size() - 1) {
                    helpInfo.append("\n");
                }
            }
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            player.sendMessage(helpInfo, player.getUUID());
            return 1;
        };

        // 签到 /sign
        dispatcher.register(Commands.literal("sign").executes(signInCommand)
                // 带有日期参数 -> 补签
                .then(Commands.argument("date", StringArgumentType.greedyString())
                        .suggests(dateSuggestions)
                        .executes(signInCommand)
                )
        );

        // 领取奖励 /reward
        dispatcher.register(Commands.literal("reward").executes(rewardCommand)
                // 带有日期参数 -> 补签
                .then(Commands.argument("date", StringArgumentType.greedyString())
                        .suggests(dateSuggestions)
                        .executes(rewardCommand)
                )
        );

        // 签到并领取奖励 /signex
        dispatcher.register(Commands.literal("signex").executes(signAndRewardCommand)
                // 带有日期参数 -> 补签
                .then(Commands.argument("date", StringArgumentType.greedyString())
                        .suggests(dateSuggestions)
                        .executes(signAndRewardCommand)
                )
        );

        // 注册有前缀的指令
        dispatcher.register(Commands.literal("va")
                .executes(helpCommand)
                .then(Commands.literal("help")
                        .executes(helpCommand)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 4))
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
                // 签到 /va sign
                .then(Commands.literal("sign").executes(signInCommand)
                        // 补签 /va sign <year> <month> <day>
                        .then(Commands.argument("date", StringArgumentType.greedyString())
                                .suggests(dateSuggestions)
                                .executes(signInCommand)
                        )
                )
                // 奖励 /va reward
                .then(Commands.literal("reward").executes(rewardCommand)
                        // 补签 /va sign <year> <month> <day>
                        .then(Commands.argument("date", StringArgumentType.greedyString())
                                .suggests(dateSuggestions)
                                .executes(rewardCommand)
                        )
                )
                // 签到并领取奖励 /va signex
                .then(Commands.literal("signex").executes(signAndRewardCommand)
                        // 补签 /va signex <year> <month> <day>
                        .then(Commands.argument("date", StringArgumentType.greedyString())
                                .suggests(dateSuggestions)
                                .executes(signAndRewardCommand)
                        )
                )
                // 获取补签卡数量 /va card
                .then(Commands.literal("card")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                            if (!ServerConfig.SIGN_IN_CARD.get()) {
                                player.sendMessage(new TranslationTextComponent(getI18nKey("服务器补签功能被禁用了哦。")), player.getUUID());
                            } else {
                                player.sendMessage(new TranslationTextComponent(getI18nKey("当前拥有%d张补签卡"), PlayerSignInDataCapability.getData(player).getSignInCard()), player.getUUID());
                            }
                            return 1;
                        })
                        // 增加/减少补签卡 /va card give <num> [<players>]
                        .then(Commands.literal("give")
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
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                            signInData.setSignInCard(signInData.getSignInCard() + num);
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("给予%d张补签卡"), num), player.getUUID());
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
                                                        player.sendMessage(new TranslationTextComponent(getI18nKey("获得%d张补签卡"), num), player.getUUID());
                                                        PlayerSignInDataCapability.syncPlayerData(player);
                                                    }
                                                    return 1;
                                                })
                                        )

                                )
                        )
                        // 设置补签卡数量 /va card set <num> [<players>]
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
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                            signInData.setSignInCard(num);
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("补签卡被设置为了%d张"), num), player.getUUID());
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
                                                        player.sendMessage(new TranslationTextComponent(getI18nKey("补签卡被设置为了%d张"), num), player.getUUID());
                                                        PlayerSignInDataCapability.syncPlayerData(player);
                                                    }
                                                    return 1;
                                                })
                                        )
                                )

                        )
                        // 获取补签卡数量 /va card get [<player>]
                        .then(Commands.literal("get")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayerEntity target = EntityArgument.getPlayer(context, "player");
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(target);
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("玩家[%s]拥有%d张补签卡"), target.getDisplayName().getString(), signInData.getSignInCard()), player.getUUID());
                                            PlayerSignInDataCapability.syncPlayerData(target);
                                            return 1;
                                        })
                                )

                        )
                )
                // 获取服务器配置 /va config get
                .then(Commands.literal("config")
                        .then(Commands.literal("get")
                                .then(Commands.literal("autoSignIn")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey(String.format("服务器已%s自动签到", ServerConfig.AUTO_SIGN_IN.get() ? "启用" : "禁用"))), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("timeCoolingMethod")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            ETimeCoolingMethod coolingMethod = ServerConfig.TIME_COOLING_METHOD.get();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("服务器签到时间冷却方式为: %s"), coolingMethod.getName()), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("timeCoolingTime")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            Double time = ServerConfig.TIME_COOLING_TIME.get();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("服务器签到冷却刷新时间为: %05.2f"), time), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("timeCoolingInterval")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            Double time = ServerConfig.TIME_COOLING_INTERVAL.get();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("服务器签到冷却刷新间隔为: %05.2f"), time), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("signInCard")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey(String.format("服务器已%s补签卡", ServerConfig.SIGN_IN_CARD.get() ? "启用" : "禁用"))), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("reSignInDays")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            int time = ServerConfig.RE_SIGN_IN_DAYS.get();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("服务器最大补签天数为: %d"), time), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("signInCardOnlyBaseReward")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey(String.format("服务器已%s补签仅获得基础奖励", ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.get() ? "启用" : "禁用"))), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("date")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("服务器当前时间: %s"), DateUtils.toDateTimeString(DateUtils.getServerDate())), player.getUUID());
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("playerDataSyncPacketSize")
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                            player.sendMessage(new TranslationTextComponent(getI18nKey("玩家签到数据同步网络包大小为: %d"), ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.get()), player.getUUID());
                                            return 1;
                                        })
                                )
                        )
                        // 设置服务器时间 /va config set date <year> <month> <day> <hour> <minute> <second>
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(3))
                                .then(Commands.literal("date")
                                        .then(Commands.argument("datetime", StringArgumentType.greedyString())
                                                .suggests(datetimeSuggestions)
                                                .executes(context -> {
                                                    long datetime = getRelativeLong(context, "datetime");
                                                    Date date = DateUtils.getDate(datetime);
                                                    ServerConfig.SERVER_TIME.set(DateUtils.toDateTimeString(new Date()));
                                                    ServerConfig.ACTUAL_TIME.set(DateUtils.toDateTimeString(date));
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.server.sendMessage(new TranslationTextComponent(getI18nKey("服务器时间已设置为: %s"), DateUtils.toDateTimeString(date)), player.getUUID());
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
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.server.sendMessage(new TranslationTextComponent(getI18nKey("服务器已%s自动签到"), bool ? "启用" : "禁用"), player.getUUID());
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
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.server.sendMessage(new TranslationTextComponent(getI18nKey("服务器已%s补签卡"), bool ? "启用" : "禁用"), player.getUUID());
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("reSignInDays")
                                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                                .executes(context -> {
                                                    int days = IntegerArgumentType.getInteger(context, "days");
                                                    ServerConfig.RE_SIGN_IN_DAYS.set(days);
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.server.sendMessage(new TranslationTextComponent(getI18nKey("服务器最大补签天数已被设置为: %d"), days), player.getUUID());
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
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.server.sendMessage(new TranslationTextComponent(getI18nKey("服务器已%s补签仅获得基础奖励"), bool ? "启用" : "禁用"), player.getUUID());
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("timeCoolingMethod")
                                        .then(Commands.argument("method", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (ETimeCoolingMethod value : ETimeCoolingMethod.values()) {
                                                        builder.suggest(value.getName());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    String method = StringArgumentType.getString(context, "method");
                                                    ServerConfig.TIME_COOLING_METHOD.set(ETimeCoolingMethod.valueOf(method));
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.sendMessage(new TranslationTextComponent(getI18nKey("服务器签到时间冷却方式已被设置为: %s"), method), player.getUUID());
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("timeCoolingTime")
                                        .then(Commands.argument("time", DoubleArgumentType.doubleArg(-23.59, 23.59))
                                                .executes(context -> {
                                                    double time = DoubleArgumentType.getDouble(context, "time");
                                                    SignInCommand.checkTime(time);
                                                    ServerConfig.TIME_COOLING_TIME.set(time);
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.sendMessage(new TranslationTextComponent(getI18nKey("服务器签到冷却刷新时间已被设置为: %05.2f"), time), player.getUUID());
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("timeCoolingInterval")
                                        .then(Commands.argument("time", DoubleArgumentType.doubleArg(-23.59f, 23.59f))
                                                .executes(context -> {
                                                    double time = DoubleArgumentType.getDouble(context, "time");
                                                    SignInCommand.checkTime(time);
                                                    ServerConfig.TIME_COOLING_INTERVAL.set(time);
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.sendMessage(new TranslationTextComponent(getI18nKey("服务器签到冷却刷新间隔已被设置为: %05.2f"), time), player.getUUID());
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("playerDataSyncPacketSize")
                                        .then(Commands.argument("size", IntegerArgumentType.integer(1, 1024))
                                                .executes(context -> {
                                                    int size = IntegerArgumentType.getInteger(context, "size");
                                                    ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.set(size);
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrException();
                                                    player.server.sendMessage(new TranslationTextComponent(getI18nKey("玩家签到数据同步网络包大小已被设置为: %d"), size), player.getUUID());
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    // 校验时间是否合法
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

    private static long getRelativeLong(CommandContext<CommandSource> context, @NonNull String name) throws CommandSyntaxException {
        String string = StringArgumentType.getString(context, name);
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
}
