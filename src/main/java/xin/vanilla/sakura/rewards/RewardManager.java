package xin.vanilla.sakura.rewards;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.NonNull;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.capability.IPlayerSignInData;
import xin.vanilla.sakura.capability.PlayerSignInDataCapability;
import xin.vanilla.sakura.capability.SignInRecord;
import xin.vanilla.sakura.config.RewardOptionData;
import xin.vanilla.sakura.config.RewardOptionDataManager;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.network.SignInPacket;
import xin.vanilla.sakura.rewards.impl.*;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.*;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 奖励管理器
 */
public class RewardManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<ERewardType, RewardParser<?>> rewardParsers = new HashMap<>();

    // 注册不同类型的奖励解析器
    static {
        rewardParsers.put(ERewardType.ITEM, new ItemRewardParser());
        rewardParsers.put(ERewardType.EFFECT, new EffectRewardParser());
        rewardParsers.put(ERewardType.EXP_POINT, new ExpPointRewardParser());
        rewardParsers.put(ERewardType.EXP_LEVEL, new ExpLevelRewardParser());
        rewardParsers.put(ERewardType.SIGN_IN_CARD, new SignInCardRewardParser());
        rewardParsers.put(ERewardType.ADVANCEMENT, new AdvancementRewardParser());
        rewardParsers.put(ERewardType.MESSAGE, new MessageRewardParser());
        rewardParsers.put(ERewardType.COMMAND, new CommandRewardParser());
        // MORE ...
    }

    /**
     * 反序列化奖励
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeReward(Reward reward) {
        RewardParser<T> parser = (RewardParser<T>) rewardParsers.get(reward.getType());
        if (parser == null) {
            throw new JsonParseException("Unknown reward type: " + reward.getType());
        }
        return parser.deserialize(reward.getContent());
    }

    /**
     * 序列化奖励
     */
    @SuppressWarnings("unchecked")
    public static <T> JsonObject serializeReward(T reward, ERewardType type) {
        RewardParser<T> parser = (RewardParser<T>) rewardParsers.get(type);
        if (parser == null) {
            throw new JsonParseException("Unknown reward type: " + type);
        }
        return parser.serialize(reward);
    }

    @SuppressWarnings("unchecked")
    public static <T> Component getRewardName(String languageCode, Reward reward, boolean withNum) {
        RewardParser<T> parser = (RewardParser<T>) rewardParsers.get(reward.getType());
        if (parser == null) {
            throw new JsonParseException("Unknown reward type: " + reward.getType());
        }
        return parser.getDisplayName(languageCode, reward.getContent(), withNum);
    }

    /**
     * 判断玩家是否签到
     *
     * @param signInData 玩家签到数据
     * @param date       日期
     * @param compensate 是否校准date
     */
    public static boolean isSignedIn(IPlayerSignInData signInData, Date date, boolean compensate) {
        int dateInt = compensate ? DateUtils.toDateInt(getCompensateDate(date)) : DateUtils.toDateInt(date);
        return signInData.getSignInRecords().stream().anyMatch(record -> DateUtils.toDateInt(record.getCompensateTime()) == dateInt);
    }

    /**
     * 判断玩家是否领取奖励
     *
     * @param signInData 玩家签到数据
     * @param date       日期
     * @param compensate 是否校准date
     */
    public static boolean isRewarded(IPlayerSignInData signInData, Date date, boolean compensate) {
        int dateInt = compensate ? DateUtils.toDateInt(getCompensateDate(date)) : DateUtils.toDateInt(date);
        return signInData.getSignInRecords().stream().anyMatch(record ->
                DateUtils.toDateInt(record.getCompensateTime()) == dateInt && record.isRewarded()
        );
    }

    /**
     * 获取玩家签到总天数
     *
     * @param signInData 玩家签到数据
     */
    public static int getTotalSignInDays(IPlayerSignInData signInData) {
        return (int) signInData.getSignInRecords().stream().map(SignInRecord::getCompensateTime).map(DateUtils::toDateInt).distinct().count();
    }

    /**
     * 获取服务器校准时间的签到时间
     * <p>
     * 服务器校准时间减去 签到冷却刷新时间
     */
    public static int getCompensateDateInt() {
        return DateUtils.toDateInt(getCompensateDate(DateUtils.getServerDate()));
    }

    /**
     * 获取签到时间的校准时间
     * <p>
     * 当前时间减去 签到冷却刷新时间
     *
     * @param date 若date为null, 则使用服务器当前时间
     */
    public static Date getCompensateDate(Date date) {
        if (date == null) {
            date = DateUtils.getServerDate();
        }
        // 签到冷却刷新时间, 固定间隔不需要校准时间
        double cooling = switch (ServerConfig.TIME_COOLING_METHOD.get()) {
            case MIXED, FIXED_TIME -> ServerConfig.TIME_COOLING_TIME.get();
            default -> 0;
        };
        // 校准后当前时间
        return DateUtils.addDate(date, -cooling);
    }

    /**
     * 获取签到时间的反向校准时间
     * <p>
     * 当前时间加上 签到冷却刷新时间
     *
     * @param date 若date为null, 则使用服务器当前时间
     */
    public static Date getUnCompensateDate(Date date) {
        if (date == null) {
            date = DateUtils.getServerDate();
        }
        // 签到冷却刷新时间, 固定间隔不需要校准时间
        double cooling = switch (ServerConfig.TIME_COOLING_METHOD.get()) {
            case MIXED, FIXED_TIME -> ServerConfig.TIME_COOLING_TIME.get();
            default -> 0;
        };
        // 校准后当前时间
        return DateUtils.addDate(date, -cooling);
    }

    /**
     * 获取指定月份的奖励列表
     *
     * @param currentMonth 当前月份
     * @param playerData   玩家签到数据
     * @param lastOffset   上月最后offset天
     * @param nextOffset   下月开始offset天
     */
    public static Map<Integer, RewardList> getMonthRewardList(Date currentMonth, IPlayerSignInData playerData, int lastOffset, int nextOffset) {
        Map<Integer, RewardList> result = new LinkedHashMap<>();
        // 选中月份的上一个月
        Date lastMonth = DateUtils.addMonth(currentMonth, -1);
        // 选中月份的下一个月
        Date nextMonth = DateUtils.addMonth(currentMonth, 1);
        // 上月的总天数
        int daysOfLastMonth = DateUtils.getDaysOfMonth(lastMonth);
        // 本月总天数
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(currentMonth);

        // 计算本月+上月最后offset天+下月开始offset的奖励
        for (int i = 1; i <= daysOfCurrentMonth + lastOffset + nextOffset; i++) {
            int month, day, year;
            if (i <= lastOffset) {
                // 属于上月的日期
                year = DateUtils.getYearPart(lastMonth);
                month = DateUtils.getMonthOfDate(lastMonth);
                day = daysOfLastMonth - (lastOffset - i);
            } else if (i <= lastOffset + daysOfCurrentMonth) {
                // 属于当前月的日期
                year = DateUtils.getYearPart(currentMonth);
                month = DateUtils.getMonthOfDate(currentMonth);
                day = i - lastOffset;
            } else {
                // 属于下月的日期
                year = DateUtils.getYearPart(nextMonth);
                month = DateUtils.getMonthOfDate(nextMonth);
                day = i - daysOfCurrentMonth - nextOffset;
            }
            int key = year * 10000 + month * 100 + day;
            Date currentDay = DateUtils.getDate(year, month, day, DateUtils.getHourOfDay(currentMonth), DateUtils.getMinuteOfHour(currentMonth), DateUtils.getSecondOfMinute(currentMonth));
            RewardList rewardList = RewardManager.getRewardListByDate(currentDay, playerData, false, false).clone();
            result.put(key, rewardList);
        }
        return result;
    }

    /**
     * 获取指定日期的奖励列表
     *
     * @param currentDay  已校准后的日期
     * @param playerData  玩家签到数据
     * @param onlyHistory 是否仅获取玩家签到记录中的奖励
     */
    @NonNull
    public static RewardList getRewardListByDate(Date currentDay, IPlayerSignInData playerData, boolean onlyHistory, boolean withRandom) {
        RewardList result = new RewardList();
        RewardOptionData serverData = RewardOptionDataManager.getRewardOptionData();
        int nowCompensate8 = RewardManager.getCompensateDateInt();
        // long nowCompensate14 = DateUtils.toDateTimeInt(nowCompensate);
        // 本月总天数
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(currentDay);
        // 本年总天数
        int daysOfCurrentYear = DateUtils.getDaysOfYear(currentDay);

        // 计算本月+上月最后offset天+下月开始offset的奖励
        int year, month, day;
        // 属于当前月的日期
        year = DateUtils.getYearPart(currentDay);
        month = DateUtils.getMonthOfDate(currentDay);
        day = DateUtils.getDayOfMonth(currentDay);
        int key = year * 10000 + month * 100 + day;
        Date date = DateUtils.getDate(year, month, day);
        int curDayOfYear = DateUtils.getDayOfYear(date);
        int curDayOfMonth = DateUtils.getDayOfMonth(date);
        int curDayOfWeek = DateUtils.getDayOfWeek(date);

        // 已签到的奖励记录
        List<Reward> rewardRecords = null;
        // 如果日历日期小于等于当前日期, 则从签到记录中查找已签到的奖励记录
        if (key <= nowCompensate8) {
            rewardRecords = playerData.getSignInRecords().stream()
                    .map(SignInRecord::clone)
                    // 若签到日期等于当前日期
                    .filter(record -> DateUtils.toDateInt(record.getCompensateTime()) == key)
                    .flatMap(record -> record.getRewardList().stream())
                    // .peek(reward -> {
                    //     reward.setRewarded(true);
                    //     reward.setDisabled(true);
                    // })
                    .toList();
        }

        // 若签到记录存在，则添加签到奖励记录并直接返回
        if (CollectionUtils.isNotNullOrEmpty(rewardRecords)) {
            result.addAll(rewardRecords);
        }
        // 若签到记录不存在，则计算
        else {
            // 若日期小于当前日期 且 补签仅计算基础奖励
            if (!onlyHistory && key < nowCompensate8 && ServerConfig.SIGN_IN_CARD_ONLY_BASE_REWARD.get()) {
                // 基础奖励
                result.addAll(serverData.getBaseRewards());
                // 累计签到奖励
                result.addAll(serverData.getCumulativeRewards().getOrDefault(String.valueOf(playerData.getTotalSignInDays() + 1), new RewardList()));
            } else if (!onlyHistory) {
                // 基础奖励
                result.addAll(serverData.getBaseRewards());
                // 年度签到奖励(正数第几天)
                result.addAll(serverData.getYearRewards().getOrDefault(String.valueOf(curDayOfYear), new RewardList()));
                // 年度签到奖励(倒数第几天)
                result.addAll(serverData.getYearRewards().getOrDefault(String.valueOf(curDayOfYear - 1 - daysOfCurrentYear), new RewardList()));
                // 月度签到奖励(正数第几天)
                result.addAll(serverData.getMonthRewards().getOrDefault(String.valueOf(curDayOfMonth), new RewardList()));
                // 月度签到奖励(倒数第几天)
                result.addAll(serverData.getMonthRewards().getOrDefault(String.valueOf(curDayOfMonth - 1 - daysOfCurrentMonth), new RewardList()));
                // 周度签到奖励(每周固定7天, 没有倒数的说法)
                result.addAll(serverData.getWeekRewards().getOrDefault(String.valueOf(curDayOfWeek), new RewardList()));
                // 自定义日期奖励
                List<Reward> dateTimeRewards = serverData.getDateTimeRewardsRelation().keySet().stream()
                        .filter(getDateStringList(currentDay)::contains)
                        .map(serverData.getDateTimeRewardsRelation()::get)
                        .distinct()
                        .map(serverData.getDateTimeRewards()::get)
                        .flatMap(Collection::stream)
                        .toList();
                if (CollectionUtils.isNotNullOrEmpty(dateTimeRewards)) result.addAll(dateTimeRewards);
                // 累计签到奖励
                result.addAll(serverData.getCumulativeRewards().getOrDefault(String.valueOf(playerData.getTotalSignInDays() + 1), new RewardList()));

                //  若日历日期>=当前日期，则添加连续签到奖励(不同玩家不一样)
                if (key >= nowCompensate8) {
                    // 连续签到天数
                    int continuousSignInDays = playerData.getContinuousSignInDays();
                    if (DateUtils.toDateInt(playerData.getLastSignInTime()) < nowCompensate8) {
                        continuousSignInDays++;
                    }
                    continuousSignInDays += key - nowCompensate8;
                    // 连续签到奖励
                    int continuousMax = serverData.getContinuousRewardsRelation().keySet().stream().map(Integer::parseInt).max(Comparator.naturalOrder()).orElse(0);
                    RewardList continuousRewards = serverData.getContinuousRewards().get(
                            serverData.getContinuousRewardsRelation().get(
                                    String.valueOf(Math.min(continuousMax, continuousSignInDays))
                            )
                    );
                    if (CollectionUtils.isNotNullOrEmpty(continuousRewards)) result.addAll(continuousRewards);
                    // 签到周期奖励
                    int cycleMax = serverData.getCycleRewardsRelation().keySet().stream().map(Integer::parseInt).max(Comparator.naturalOrder()).orElse(0);
                    RewardList cycleRewards = new RewardList();
                    if (cycleMax > 0) {
                        cycleRewards = serverData.getCycleRewards().get(
                                serverData.getCycleRewardsRelation().get(
                                        String.valueOf(continuousSignInDays % cycleMax == 0 ? cycleMax : continuousSignInDays % cycleMax)
                                )
                        );
                    }
                    if (CollectionUtils.isNotNullOrEmpty(cycleRewards)) result.addAll(cycleRewards);
                }
            }
            if (withRandom) {
                result.addAll(RewardManager.getRandomRewardList());
            }
        }
        return RewardManager.mergeRewards(result);
    }

    public static RewardList getRandomRewardList() {
        RewardList result = new RewardList();
        Map<String, RewardList> randomRewards = RewardOptionDataManager.getRewardOptionData().getRandomRewards();
        // 将概率字符串转换为 BigDecimal，并计算总概率
        Set<Map.Entry<BigDecimal, RewardList>> entries = randomRewards.entrySet().stream()
                .collect(Collectors.toMap(entry -> new BigDecimal(entry.getKey()), Map.Entry::getValue)).entrySet();
        BigDecimal totalProbability = entries.stream()
                .map(Map.Entry::getKey)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 如果总概率为 0，则返回空结果
        if (totalProbability.compareTo(BigDecimal.ZERO) != 0) {
            // 生成 0 到 totalProbability 之间的随机值
            BigDecimal randomValue = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())
                    .multiply(totalProbability)
                    .setScale(10, RoundingMode.HALF_UP);
            // 遍历概率池，找到对应的奖励
            BigDecimal cumulative = BigDecimal.ZERO;
            for (Map.Entry<BigDecimal, RewardList> entry : entries) {
                cumulative = cumulative.add(entry.getKey());
                if (randomValue.compareTo(cumulative) <= 0) {
                    result = entry.getValue();
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 合并重复类型的奖励
     */
    public static RewardList mergeRewards(RewardList rewardList) {
        // return rewardList;
        List<Reward> rewards = rewardList.stream()
                .collect(Collectors.groupingBy(reward -> {
                            ERewardType type = reward.getType();
                            String key = type.name();
                            // 分组时基于type和内容字段进行分组键
                            switch (type) {
                                case ITEM:
                                    ItemStack itemStack = RewardManager.deserializeReward(reward);
                                    key = itemStack.getItem().getRegistryName().toString();
                                    if (itemStack.hasTag()) {
                                        key += itemStack.getTag().toString();
                                    }
                                    break;
                                case EFFECT:
                                    MobEffectInstance mobEffectInstance = RewardManager.deserializeReward(reward);
                                    key = mobEffectInstance.getEffect().getRegistryName().toString() + " " + mobEffectInstance.getAmplifier();
                                    break;
                                case EXP_POINT, SIGN_IN_CARD, EXP_LEVEL:
                                    break;
                                case ADVANCEMENT:
                                case MESSAGE:
                                default:
                                    key = reward.getContent().toString();
                                    break;
                            }
                            return key + reward.getProbability();
                        },
                        Collectors.reducing(null, (reward1, reward2) -> {
                            if (reward1 == null) return reward2;
                            if (reward2 == null) return reward1;

                            ERewardType type = reward1.getType();
                            Object content1 = RewardManager.deserializeReward(reward1);
                            Object content2 = RewardManager.deserializeReward(reward2);
                            switch (type) {
                                case ITEM:
                                    content1 = new ItemStack(((ItemStack) content1).getItem(), ((ItemStack) content1).getCount() + ((ItemStack) content2).getCount());
                                    ((ItemStack) content1).setTag(((ItemStack) content2).getTag());
                                    break;
                                case EFFECT:
                                    content1 = new MobEffectInstance(((MobEffectInstance) content1).getEffect(), ((MobEffectInstance) content1).getDuration() + ((MobEffectInstance) content2).getDuration(), ((MobEffectInstance) content1).getAmplifier());
                                    break;
                                case EXP_POINT:
                                case SIGN_IN_CARD:
                                case EXP_LEVEL:
                                    content1 = ((Integer) content1) + ((Integer) content2);
                                    break;
                                case ADVANCEMENT:
                                case MESSAGE:
                                default:
                                    break;
                            }
                            return new Reward().setRewarded(reward1.isRewarded()).setType(type).setDisabled(reward1.isDisabled()).setContent(RewardManager.serializeReward(content1, type));
                        })))
                .values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new RewardList(rewards);
    }

    public static List<String> getDateStringList(Date date) {
        List<String> result = new ArrayList<>();
        result.add(DateUtils.toDateTimeString(date));
        result.add(DateUtils.toString(date));
        result.add(DateUtils.toString(date, "'0000'-MM-dd"));
        result.add(DateUtils.toString(date, "yyyy-'00'-dd"));
        result.add(DateUtils.toString(date, "yyyy-MM-'00'"));
        result.add(DateUtils.toString(date, "'0000'-'00'-dd"));
        result.add(DateUtils.toString(date, "'0000'-MM-'00'"));
        result.add(DateUtils.toString(date, "yyyy-'00'-'00'"));
        result.add(DateUtils.toString(date, "'0000'-MM-dd HH:mm:ss"));
        result.add(DateUtils.toString(date, "yyyy-'00'-dd HH:mm:ss"));
        result.add(DateUtils.toString(date, "yyyy-MM-'00' HH:mm:ss"));
        result.add(DateUtils.toString(date, "'0000'-'00'-dd HH:mm:ss"));
        result.add(DateUtils.toString(date, "'0000'-MM-'00' HH:mm:ss"));
        result.add(DateUtils.toString(date, "yyyy-'00'-'00' HH:mm:ss"));
        return result;
    }

    /**
     * 签到or补签
     */
    public static void signIn(ServerPlayer player, SignInPacket packet) {
        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
        Date serverDate = DateUtils.getServerDate();
        Date serverCompensateDate = getCompensateDate(serverDate);
        Date signCompensateDate = packet.getSignInType() == ESignInType.SIGN_IN ? serverCompensateDate : DateUtils.format(packet.getSignInTime());
        int serverCompensateDateInt = DateUtils.toDateInt(serverCompensateDate);
        int signCompensateDateInt = DateUtils.toDateInt(signCompensateDate);

        ETimeCoolingMethod coolingMethod = ServerConfig.TIME_COOLING_METHOD.get();
        // 判断签到/补签时间合法性
        if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt < signCompensateDateInt) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "sign_in_date_late_server_current_date_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt > signCompensateDateInt) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "sign_in_date_early_server_current_date_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && signInData.getSignInRecords().stream().anyMatch(record -> DateUtils.toDateInt(record.getCompensateTime()) == signCompensateDateInt)) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "already_signed"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt <= signCompensateDateInt) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "compensate_date_not_early_server_current_date_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        }
        // 判断签到CD
        if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && coolingMethod.getCode() >= ETimeCoolingMethod.FIXED_INTERVAL.getCode()) {
            Date lastSignInTime = DateUtils.addDate(signInData.getLastSignInTime(), ServerConfig.TIME_COOLING_INTERVAL.get());
            if (serverDate.before(lastSignInTime)) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "sign_in_cool_down_fail"));
                PlayerSignInDataCapability.syncPlayerData(player);
                return;
            }
        }
        // 判断补签
        if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && !ServerConfig.SIGN_IN_CARD.get()) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "server_not_enable_sign_in_card_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && signInData.getSignInCard() <= 0) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "not_enough_sign_in_card_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && isSignedIn(signInData, signCompensateDate, false)) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "already_signed"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        }
        // 判断领取奖励
        if (ESignInType.REWARD.equals(packet.getSignInType())) {
            if (isRewarded(signInData, signCompensateDate, false)) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "already_receive_reward_s", DateUtils.toString(signCompensateDate)));
                PlayerSignInDataCapability.syncPlayerData(player);
                return;
            } else if (!isSignedIn(signInData, signCompensateDate, false)) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "not_sign_in", DateUtils.toString(signCompensateDate)));
                PlayerSignInDataCapability.syncPlayerData(player);
                return;
            } else {
                boolean showFailed = player.hasPermissions(ServerConfig.PERMISSION_REWARD_FAILED_TIPS.get());
                Component msg = Component.translatable(player, EI18nType.MESSAGE, "receive_reward_success");
                signInData.getSignInRecords().stream()
                        // 若签到日期等于当前日期
                        .filter(record -> DateUtils.toDateInt(record.getCompensateTime()) == DateUtils.toDateInt(signCompensateDate))
                        // 若奖励未领取
                        .filter(record -> !record.isRewarded())
                        .forEach(record -> {
                            // 设置奖励为已领取
                            record.setRewarded(true);
                            record.getRewardList().stream()
                                    .filter(reward -> !reward.isDisabled())
                                    .filter(reward -> !reward.isRewarded())
                                    .forEach(reward -> {
                                        reward.setDisabled(true);
                                        reward.setRewarded(true);
                                        Component detail = reward.getName(SakuraUtils.getPlayerLanguage(player), true);
                                        if (giveRewardToPlayer(player, signInData, reward)) {
                                            detail.setColor(Color.GREEN.getRGB());
                                            msg.append(", ").append(detail);
                                        } else if (showFailed) {
                                            detail.setColor(Color.RED.getRGB());
                                            msg.append(", ").append(detail);
                                        }
                                    });
                        });
                SakuraUtils.sendMessage(player, msg);
            }
        }
        // 签到/补签
        else {
            RewardList rewardList = RewardManager.getRewardListByDate(signCompensateDate, signInData, false, true).clone();
            if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType())) signInData.subSignInCard();
            SignInRecord signInRecord = new SignInRecord();
            signInRecord.setRewarded(packet.isAutoRewarded());
            signInRecord.setRewardList(new RewardList());
            signInRecord.setSignInTime(serverDate);
            signInRecord.setCompensateTime(signCompensateDate);
            signInRecord.setSignInUUID(player.getUUID().toString());
            // 是否自动领取
            if (packet.isAutoRewarded()) {
                boolean showFailed = player.hasPermissions(ServerConfig.PERMISSION_REWARD_FAILED_TIPS.get());
                Component msg = Component.translatable(player, EI18nType.MESSAGE, "receive_reward_success");
                rewardList.forEach(reward -> {
                    Component detail = reward.getName(SakuraUtils.getPlayerLanguage(player), true);
                    if (giveRewardToPlayer(player, signInData, reward)) {
                        detail.setColor(Color.GREEN.getRGB());
                        signInRecord.getRewardList().add(reward);
                        msg.append(", ").append(detail);
                    } else if (showFailed) {
                        detail.setColor(Color.RED.getRGB());
                        msg.append(", ").append(detail);
                    }
                });
                SakuraUtils.sendMessage(player, msg);
            } else {
                signInRecord.getRewardList().addAll(rewardList);
            }
            signInData.setLastSignInTime(serverDate);
            signInData.getSignInRecords().add(signInRecord);
            signInData.setContinuousSignInDays(DateUtils.calculateContinuousDays(signInData.getSignInRecords().stream().map(SignInRecord::getCompensateTime).collect(Collectors.toList()), serverCompensateDate));
            signInData.plusTotalSignInDays();
            SakuraUtils.sendMessage(player, Component.translatable(player, EI18nType.MESSAGE, "sign_in_success_s", DateUtils.toString(signInRecord.getCompensateTime()), signInData.getContinuousSignInDays(), getTotalSignInDays(signInData)));
        }
        signInData.save(player);
        // 同步数据至客户端
        PlayerSignInDataCapability.syncPlayerData(player);
    }

    public static boolean giveRewardToPlayer(ServerPlayer player, IPlayerSignInData signInData, Reward reward) {
        reward.setRewarded(true);
        Object object = RewardManager.deserializeReward(reward);
        // 判断是否启用
        if (ServerConfig.REWARD_AFFECTED_BY_LUCK.get()) {
            int offset = player.getActiveEffects().stream()
                    .filter(instance -> instance.getEffect() == MobEffects.LUCK || instance.getEffect() == MobEffects.UNLUCK)
                    .map(instance -> {
                        if (instance.getEffect() == MobEffects.LUCK) {
                            return instance.getAmplifier();
                        } else {
                            return -instance.getAmplifier();
                        }
                    }).reduce(0, Integer::sum);
            if (new Random().nextDouble() > reward.getProbability().add(BigDecimal.valueOf(offset * 0.075)).doubleValue())
                return false;
        }
        switch (reward.getType()) {
            case ITEM:
                RewardManager.giveItemStack(player, (ItemStack) object, true);
                break;
            case SIGN_IN_CARD:
                signInData.plusSignInCard((Integer) object);
                break;
            case EFFECT:
                player.addEffect((MobEffectInstance) object);
                break;
            case EXP_LEVEL:
                player.giveExperienceLevels((Integer) object);
                break;
            case EXP_POINT:
                player.giveExperiencePoints((Integer) object);
                break;
            case ADVANCEMENT:
                Advancement advancement = player.server.getAdvancements().getAdvancement((ResourceLocation) object);
                if (advancement != null) {
                    AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
                    progress.getRemainingCriteria().forEach(criterion -> player.getAdvancements().award(advancement, criterion));
                }
                break;
            case MESSAGE:
                SakuraUtils.sendMessage(player, (Component) object);
                break;
            case COMMAND:
                String command = (String) object;
                command = command.replaceAll("@s", player.getName().getString());
                if (StringUtils.isNotNullOrEmpty(command)) {
                    player.server.getCommands().performCommand(player.createCommandSourceStack().withSuppressedOutput().withPermission(2), command);
                }
                break;
            default:
        }
        return true;
    }

    /**
     * 给予玩家物品
     *
     * @param player    目标玩家
     * @param itemStack 物品堆
     * @param drop      若玩家背包空间不足, 是否以物品实体的形式生成在世界上
     * @return 是否添加成功
     */
    public static boolean giveItemStack(ServerPlayer player, ItemStack itemStack, boolean drop) {
        // 尝试将物品堆添加到玩家的库存中
        boolean added = player.getInventory().add(itemStack);
        // 如果物品堆无法添加到库存，则以物品实体的形式生成在世界上
        if (!added && drop) {
            ItemEntity itemEntity = new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), itemStack);
            added = player.level.addFreshEntity(itemEntity);
        }
        return added;
    }

}
