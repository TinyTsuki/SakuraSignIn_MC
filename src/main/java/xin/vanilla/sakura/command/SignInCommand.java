package xin.vanilla.sakura.command;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import lombok.NonNull;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.capability.IPlayerSignInData;
import xin.vanilla.sakura.capability.PlayerSignInDataCapability;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.config.RewardOptionDataManager;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.network.SignInPacket;
import xin.vanilla.sakura.rewards.RewardList;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.StringUtils;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static xin.vanilla.sakura.util.I18nUtils.getByZh;
import static xin.vanilla.sakura.util.I18nUtils.getI18nKey;

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

        Command<CommandSourceStack> signInCommand = context -> {
            List<KeyValue<Date, ESignInType>> signInTimeList = new ArrayList<>();
            ServerPlayer player = context.getSource().getPlayerOrException();
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
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
                RewardManager.signIn(player, new SignInPacket(DateUtils.toString(date), true, ESignInType.REWARD));
            }
            return 1;
        };
        Command<CommandSourceStack> signAndRewardCommand = context -> {
            List<KeyValue<Date, ESignInType>> signInTimeList = new ArrayList<>();
            ServerPlayer player = context.getSource().getPlayerOrException();
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
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
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
            String string = StringArgumentType.getString(context, "key");
            if (signInData.getCdkErrorRecords().stream()
                    .filter(keyValue -> DateUtils.toDateInt(keyValue.getValue().getKey()) == DateUtils.toDateInt(DateUtils.getServerDate()))
                    .filter(keyValue -> !keyValue.getValue().getValue())
                    .count() >= 5) {
                player.sendSystemMessage(AbstractGuiUtils.textToComponent(Text.i18n("今日CDK输入错误次数过多，请明日再试").setColor(0xFFFF0000)));
            } else if (signInData.getCdkErrorRecords().stream()
                    .filter(keyValue -> keyValue.getKey().equals(string))
                    .anyMatch(keyValue -> keyValue.getValue().getValue())) {
                player.sendSystemMessage(AbstractGuiUtils.textToComponent(Text.i18n("阁下已领取过当前CDK的奖励，请勿重复领取").setColor(0xFFFFFF00)));
            } else {
                List<KeyValue<String, KeyValue<String, RewardList>>> cdkRewards = RewardOptionDataManager.getRewardOptionData().getCdkRewards();
                if (CollectionUtils.isNotNullOrEmpty(cdkRewards)) {
                    // 找到第一个匹配的项的下标
                    int indexToRemove = IntStream.range(0, cdkRewards.size())
                            .filter(i -> cdkRewards.get(i).getKey().equals(string))
                            .findFirst()
                            .orElse(-1);
                    boolean error = true;
                    if (indexToRemove < 0) {
                        player.sendSystemMessage(AbstractGuiUtils.textToComponent(Text.i18n("输入的CDK不存在或已被领取").setColor(0xFFFF0000)));
                    } else {
                        KeyValue<String, KeyValue<String, RewardList>> rewardKeyValue = RewardOptionDataManager.getRewardOptionData().getCdkRewards().remove(indexToRemove);
                        RewardOptionDataManager.saveRewardOption();
                        Date format = DateUtils.format(rewardKeyValue.getValue().getKey());
                        if (format.before(DateUtils.getServerDate())) {
                            player.sendSystemMessage(AbstractGuiUtils.textToComponent(Text.i18n("输入的CDK已过期").setColor(0xFFFF0000)));
                        } else {
                            MutableComponent msg = Component.literal(getByZh("奖励领取详情:"));
                            rewardKeyValue.getValue().getValue().forEach(reward -> {
                                MutableComponent detail = Component.literal(reward.getName(true));
                                if (RewardManager.giveRewardToPlayer(player, signInData, reward)) {
                                    detail.withStyle(style -> style.withColor(Color.GREEN.getRGB()));
                                } else {
                                    detail.withStyle(style -> style.withColor(Color.RED.getRGB()));
                                }
                                msg.append(", ").append(detail);
                            });
                            player.sendSystemMessage(msg);
                            error = false;
                        }
                    }
                    signInData.getCdkErrorRecords().add(new KeyValue<>(string, new KeyValue<>(DateUtils.getServerDate(), !error)));
                }
            }
            return 1;
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
            MutableComponent helpInfo = Component.literal("-----==== Sakura Sign In Help (" + page + "/" + pages + ") ====-----\n");
            for (int i = 0; (page - 1) * HELP_INFO_NUM_PER_PAGE + i < HELP_MESSAGE.size() && i < HELP_INFO_NUM_PER_PAGE; i++) {
                KeyValue<String, String> keyValue = HELP_MESSAGE.get((page - 1) * HELP_INFO_NUM_PER_PAGE + i);
                MutableComponent commandTips = Component.translatable("command." + SakuraSignIn.MODID + "." + keyValue.getValue());
                commandTips.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
                helpInfo.append(keyValue.getKey())
                        .append(Component.literal(" -> ").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                        .append(commandTips);
                if (i != HELP_MESSAGE.size() - 1) {
                    helpInfo.append("\n");
                }
            }
            ServerPlayer player = context.getSource().getPlayerOrException();
            player.sendSystemMessage(helpInfo);
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

        // 領取CDK獎勵 /cdk
        dispatcher.register(Commands.literal("cdk")
                // 带有日期参数 -> 补签
                .then(Commands.argument("key", StringArgumentType.word())
                        .executes(cdkCommand)
                )
        );

        // 注册有前缀的指令
        dispatcher.register(Commands.literal("sakura")
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
                // 領取CDK獎勵 /va cdk
                .then(Commands.literal("cdk")
                        // 补签 /va signex <year> <month> <day>
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(cdkCommand)
                        )
                )
                // 获取补签卡数量 /va card
                .then(Commands.literal("card")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!ServerConfig.SIGN_IN_CARD.get()) {
                                player.sendSystemMessage(Component.translatable(getI18nKey("服务器补签功能被禁用了哦。")));
                            } else {
                                player.sendSystemMessage(Component.translatable(getI18nKey("当前拥有%d张补签卡"), PlayerSignInDataCapability.getData(player).getSignInCard()));
                            }
                            return 1;
                        })
                        // 增加/减少补签卡 /va card give <num> [<players>]
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
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                            signInData.setSignInCard(signInData.getSignInCard() + num);
                                            player.sendSystemMessage(Component.translatable(getI18nKey("给予%d张补签卡"), num));
                                            PlayerSignInDataCapability.syncPlayerData(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(context -> {
                                                    int num = IntegerArgumentType.getInteger(context, "num");
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "player");
                                                    for (ServerPlayer player : players) {
                                                        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                                        signInData.setSignInCard(signInData.getSignInCard() + num);
                                                        player.sendSystemMessage(Component.translatable(getI18nKey("获得%d张补签卡"), num));
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
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                            signInData.setSignInCard(num);
                                            player.sendSystemMessage(Component.translatable(getI18nKey("补签卡被设置为了%d张"), num));
                                            PlayerSignInDataCapability.syncPlayerData(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(context -> {
                                                    int num = IntegerArgumentType.getInteger(context, "num");
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "player");
                                                    for (ServerPlayer player : players) {
                                                        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
                                                        signInData.setSignInCard(num);
                                                        player.sendSystemMessage(Component.translatable(getI18nKey("补签卡被设置为了%d张"), num));
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
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(target);
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey("玩家[%s]拥有%d张补签卡"), target.getDisplayName().getString(), signInData.getSignInCard()));
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
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey(String.format("服务器已%s自动签到", ServerConfig.AUTO_SIGN_IN.get() ? "启用" : "禁用"))));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("timeCoolingMethod")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ETimeCoolingMethod coolingMethod = ServerConfig.TIME_COOLING_METHOD.get();
                                            player.sendSystemMessage(Component.translatable(getI18nKey("服务器签到时间冷却方式为: %s"), coolingMethod.name()));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("timeCoolingTime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            Double time = ServerConfig.TIME_COOLING_TIME.get();
                                            player.sendSystemMessage(Component.translatable(getI18nKey("服务器签到冷却刷新时间为: %05.2f"), time));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("timeCoolingInterval")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            Double time = ServerConfig.TIME_COOLING_INTERVAL.get();
                                            player.sendSystemMessage(Component.translatable(getI18nKey("服务器签到冷却刷新间隔为: %05.2f"), time));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("signInCard")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey(String.format("服务器已%s补签卡", ServerConfig.SIGN_IN_CARD.get() ? "启用" : "禁用"))));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("reSignInDays")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            int time = ServerConfig.RE_SIGN_IN_DAYS.get();
                                            player.sendSystemMessage(Component.translatable(getI18nKey("服务器最大补签天数为: %d"), time));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("signInCardOnlyBaseReward")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey(String.format("服务器已%s补签仅获得基础奖励", ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.get() ? "启用" : "禁用"))));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("date")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey("服务器当前时间: %s"), DateUtils.toDateTimeString(DateUtils.getServerDate())));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("playerDataSyncPacketSize")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey("玩家签到数据同步网络包大小为: %d"), ServerConfig.PLAYER_DATA_SYNC_PACKET_SIZE.get()));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("rewardAffectedByLuck")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey(String.format("服务器已%s奖励领取受幸运影响", ServerConfig.REWARD_AFFECTED_BY_LUCK.get() ? "启用" : "禁用"))));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("continuousRewardsRepeatable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey(String.format("服务器已%s连续签到奖励持续领取", ServerConfig.CONTINUOUS_REWARDS_REPEATABLE.get() ? "启用" : "禁用"))));
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("cycleRewardsRepeatable")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            player.sendSystemMessage(Component.translatable(getI18nKey(String.format("服务器已%s循环签到奖励持续领取", ServerConfig.CYCLE_REWARDS_REPEATABLE.get() ? "启用" : "禁用"))));
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
                                                    String string = StringArgumentType.getString(context, "datetime");
                                                    long datetime = getRelativeLong(string, "datetime");
                                                    Date date = DateUtils.getDate(datetime);
                                                    ServerConfig.SERVER_TIME.set(DateUtils.toDateTimeString(new Date()));
                                                    ServerConfig.ACTUAL_TIME.set(DateUtils.toDateTimeString(date));
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器时间已设置为: %s"), DateUtils.toDateTimeString(date)));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器已%s自动签到"), bool ? "启用" : "禁用"));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器已%s补签卡"), bool ? "启用" : "禁用"));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器最大补签天数已被设置为: %d"), days));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器已%s补签仅获得基础奖励"), bool ? "启用" : "禁用"));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器签到时间冷却方式已被设置为: %s"), method));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器签到冷却刷新时间已被设置为: %05.2f"), time));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器签到冷却刷新间隔已被设置为: %05.2f"), time));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("玩家签到数据同步网络包大小已被设置为: %d"), size));
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
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器已%s奖励领取受幸运影响"), bool ? "启用" : "禁用"));
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
                                                    RewardOptionDataManager.getRewardOptionData().refreshContinuousRewardsRelation();
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器已%s连续签到奖励持续领取"), bool ? "启用" : "禁用"));
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
                                                    RewardOptionDataManager.getRewardOptionData().refreshCycleRewardsRelation();
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    broadcastMessage(player, Component.translatable(getI18nKey("服务器已%s循环签到奖励持续领取"), bool ? "启用" : "禁用"));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    // 广播消息
    private static void broadcastMessage(ServerPlayer player, Component message) {
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("chat.type.announcement", player.getDisplayName(), message), false);
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
