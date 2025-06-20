package xin.vanilla.sakura.rewards;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.NonNull;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.config.RewardConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.KeyValue;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.data.RewardList;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.data.player.IPlayerSignInData;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.enums.*;
import xin.vanilla.sakura.network.packet.RewardCellDirtiedToClient;
import xin.vanilla.sakura.network.packet.SignInToServer;
import xin.vanilla.sakura.rewards.impl.*;
import xin.vanilla.sakura.util.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 奖励管理器
 */
public class RewardManager {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<EnumRewardType, RewardParser<?>> rewardParsers = new HashMap<>();

    private static final Random random = new Random();

    // 注册不同类型的奖励解析器
    static {
        rewardParsers.put(EnumRewardType.ITEM, new ItemRewardParser());
        rewardParsers.put(EnumRewardType.EFFECT, new EffectRewardParser());
        rewardParsers.put(EnumRewardType.EXP_POINT, new ExpPointRewardParser());
        rewardParsers.put(EnumRewardType.EXP_LEVEL, new ExpLevelRewardParser());
        rewardParsers.put(EnumRewardType.SIGN_IN_CARD, new SignInCardRewardParser());
        rewardParsers.put(EnumRewardType.ADVANCEMENT, new AdvancementRewardParser());
        rewardParsers.put(EnumRewardType.MESSAGE, new MessageRewardParser());
        rewardParsers.put(EnumRewardType.COMMAND, new CommandRewardParser());
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
    public static <T> JsonObject serializeReward(T reward, EnumRewardType type) {
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
        double cooling;
        switch (EnumTimeCoolingMethod.valueOf(ServerConfig.TIME_COOLING_METHOD.get())) {
            case MIXED:
            case FIXED_TIME:
                cooling = ServerConfig.TIME_COOLING_TIME.get();
                break;
            default:
                cooling = 0;
                break;
        }
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
        double cooling;
        switch (EnumTimeCoolingMethod.valueOf(ServerConfig.TIME_COOLING_METHOD.get())) {
            case MIXED:
            case FIXED_TIME:
                cooling = ServerConfig.TIME_COOLING_TIME.get();
                break;
            default:
                cooling = 0;
                break;
        }
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
    public static Map<KeyValue<String, EnumSignInStatus>, RewardList> getMonthRewardList(Date currentMonth, IPlayerSignInData playerData, int lastOffset, int nextOffset) {
        Map<KeyValue<String, EnumSignInStatus>, RewardList> result = new LinkedHashMap<>();
        // 选中月份的上一个月
        Date lastMonth = DateUtils.addMonth(currentMonth, -1);
        // 选中月份的下一个月
        Date nextMonth = DateUtils.addMonth(currentMonth, 1);
        // 当前日期
        Date curDate = DateUtils.getServerDate();
        // 上月的总天数
        int daysOfLastMonth = DateUtils.getDaysOfMonth(lastMonth);
        // 本月总天数
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(currentMonth);

        // 计算本月+上月最后offset天+下月开始offset的奖励
        for (int i = 1; i <= daysOfCurrentMonth + lastOffset + nextOffset; i++) {
            int cellMonth, cellDay, cellYear;
            if (i <= lastOffset) {
                // 属于上月的日期
                cellYear = DateUtils.getYearPart(lastMonth);
                cellMonth = DateUtils.getMonthOfDate(lastMonth);
                cellDay = daysOfLastMonth - (lastOffset - i);
            } else if (i <= lastOffset + daysOfCurrentMonth) {
                // 属于当前月的日期
                cellYear = DateUtils.getYearPart(currentMonth);
                cellMonth = DateUtils.getMonthOfDate(currentMonth);
                cellDay = i - lastOffset;
            } else {
                // 属于下月的日期
                cellYear = DateUtils.getYearPart(nextMonth);
                cellMonth = DateUtils.getMonthOfDate(nextMonth);
                cellDay = i - daysOfCurrentMonth - nextOffset;
            }
            int cellDateInt = cellYear * 10000 + cellMonth * 100 + cellDay;
            Date cellDate = DateUtils.getDate(cellYear, cellMonth, cellDay
                    , DateUtils.getHourOfDay(currentMonth)
                    , DateUtils.getMinuteOfHour(currentMonth)
                    , DateUtils.getSecondOfMinute(currentMonth)
            );
            RewardList rewardList = RewardManager.getRewardListByDate(cellDate, playerData, false, false).clone();
            EnumSignInStatus status = EnumSignInStatus.NO_ACTION;
            // 判断是否已领奖
            if (RewardManager.isRewarded(playerData, cellDate, false)) {
                status = EnumSignInStatus.REWARDED;
            }
            // 判断是否已签到
            else if (RewardManager.isSignedIn(playerData, cellDate, false)) {
                status = EnumSignInStatus.SIGNED_IN;
            }
            // 判断是否当前日期
            else if (cellYear == DateUtils.getYearPart(curDate)
                    && cellMonth == DateUtils.getMonthOfDate(curDate)
                    && cellDay == DateUtils.getDayOfMonth(curDate)
            ) {
                status = EnumSignInStatus.NOT_SIGNED_IN;
            }
            // 是否能补签
            else if (ServerConfig.SIGN_IN_CARD.get()
                    && cellDateInt <= DateUtils.toDateInt(cellDate)
                    && DateUtils.toDateInt(DateUtils.addDay(cellDate, -ServerConfig.RE_SIGN_IN_DAYS.get())) <= cellDateInt
            ) {
                status = EnumSignInStatus.CAN_REPAIR;
            }
            result.put(new KeyValue<>(DateUtils.toString(cellDate), status), rewardList);
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
        RewardConfig serverData = RewardConfigManager.getRewardConfig();
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
                    .collect(Collectors.toList());
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
                        .collect(Collectors.toList());
                if (CollectionUtils.isNotNullOrEmpty(dateTimeRewards)) result.addAll(dateTimeRewards);
                // 累计签到奖励
                result.addAll(serverData.getCumulativeRewards().getOrDefault(String.valueOf(playerData.getTotalSignInDays() + 1), new RewardList()));

                //  若日历日期>=当前日期，则添加连续签到奖励(不同玩家不一样)
                if (key >= nowCompensate8) {
                    // 连续签到天数
                    int continuousSignInDays = playerData.calculateContinuousDays();
                    continuousSignInDays += (int) DateUtils.daysOfTwo(DateUtils.toTheDayStart(getCompensateDate(DateUtils.getServerDate())), DateUtils.toTheDayStart(currentDay));
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
        Map<String, RewardList> randomRewards = RewardConfigManager.getRewardConfig().getRandomRewards();
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
                            EnumRewardType type = reward.getType();
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
                                    EffectInstance effectInstance = RewardManager.deserializeReward(reward);
                                    key = effectInstance.getEffect().getRegistryName().toString() + " " + effectInstance.getAmplifier();
                                    break;
                                case EXP_POINT:
                                    break;
                                case EXP_LEVEL:
                                    break;
                                case SIGN_IN_CARD:
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

                            EnumRewardType type = reward1.getType();
                            Object content1 = RewardManager.deserializeReward(reward1);
                            Object content2 = RewardManager.deserializeReward(reward2);
                            switch (type) {
                                case ITEM:
                                    content1 = new ItemStack(((ItemStack) content1).getItem(), ((ItemStack) content1).getCount() + ((ItemStack) content2).getCount());
                                    ((ItemStack) content1).setTag(((ItemStack) content2).getTag());
                                    break;
                                case EFFECT:
                                    content1 = new EffectInstance(((EffectInstance) content1).getEffect(), ((EffectInstance) content1).getDuration() + ((EffectInstance) content2).getDuration(), ((EffectInstance) content1).getAmplifier());
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
    public static void signIn(ServerPlayerEntity player, SignInToServer packet) {
        IPlayerSignInData signInData = PlayerSignInDataCapability.getData(player);
        Date serverDate = DateUtils.getServerDate();
        Date serverCompensateDate = getCompensateDate(serverDate);
        Date signCompensateDate = packet.getSignInType() == EnumSignInType.SIGN_IN ? serverCompensateDate : DateUtils.format(packet.getSignInTime());
        int serverCompensateDateInt = DateUtils.toDateInt(serverCompensateDate);
        int signCompensateDateInt = DateUtils.toDateInt(signCompensateDate);

        EnumTimeCoolingMethod coolingMethod = EnumTimeCoolingMethod.valueOf(ServerConfig.TIME_COOLING_METHOD.get());
        // 判断签到/补签时间合法性
        if (EnumSignInType.SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt < signCompensateDateInt) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "sign_in_date_late_server_current_date_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (EnumSignInType.SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt > signCompensateDateInt) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "sign_in_date_early_server_current_date_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (EnumSignInType.SIGN_IN.equals(packet.getSignInType()) && signInData.getSignInRecords().stream().anyMatch(record -> DateUtils.toDateInt(record.getCompensateTime()) == signCompensateDateInt)) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "already_signed"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (EnumSignInType.RE_SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt <= signCompensateDateInt) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "compensate_date_not_early_server_current_date_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        }
        // 判断签到CD
        if (EnumSignInType.SIGN_IN.equals(packet.getSignInType()) && coolingMethod.getCode() >= EnumTimeCoolingMethod.FIXED_INTERVAL.getCode()) {
            Date lastSignInTime = DateUtils.addDate(signInData.getLastSignInTime(), ServerConfig.TIME_COOLING_INTERVAL.get());
            if (serverDate.before(lastSignInTime)) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "sign_in_cool_down_fail"));
                PlayerSignInDataCapability.syncPlayerData(player);
                return;
            }
        }
        // 判断补签
        if (EnumSignInType.RE_SIGN_IN.equals(packet.getSignInType()) && !ServerConfig.SIGN_IN_CARD.get()) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "server_not_enable_sign_in_card_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (EnumSignInType.RE_SIGN_IN.equals(packet.getSignInType()) && signInData.getSignInCard() <= 0) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "not_enough_sign_in_card_fail"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        } else if (EnumSignInType.RE_SIGN_IN.equals(packet.getSignInType()) && isSignedIn(signInData, signCompensateDate, false)) {
            SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "already_signed"));
            PlayerSignInDataCapability.syncPlayerData(player);
            return;
        }
        // 判断领取奖励
        if (EnumSignInType.REWARD.equals(packet.getSignInType())) {
            if (isRewarded(signInData, signCompensateDate, false)) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "already_receive_reward_s", DateUtils.toString(signCompensateDate)));
                PlayerSignInDataCapability.syncPlayerData(player);
                return;
            } else if (!isSignedIn(signInData, signCompensateDate, false)) {
                SakuraUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "not_sign_in", DateUtils.toString(signCompensateDate)));
                PlayerSignInDataCapability.syncPlayerData(player);
                return;
            } else {
                boolean showFailed = player.hasPermissions(ServerConfig.PERMISSION_REWARD_FAILED_TIPS.get());
                Component msg = Component.translatable(player, EnumI18nType.MESSAGE, "receive_reward_success");
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
                                            detail.setColor(EnumMCColor.GREEN.getColor());
                                            msg.append(", ").append(detail);
                                        } else if (showFailed) {
                                            detail.setColor(EnumMCColor.RED.getColor());
                                            msg.append(", ").append(detail);
                                        }
                                    });
                        });
                SakuraUtils.sendMessage(player, msg);
                SakuraUtils.sendPacketToPlayer(new RewardCellDirtiedToClient(), player);
            }
        }
        // 签到/补签
        else {
            RewardList rewardList = RewardManager.getRewardListByDate(signCompensateDate, signInData, false, true).clone();
            if (EnumSignInType.RE_SIGN_IN.equals(packet.getSignInType())) signInData.subSignInCard();
            SignInRecord signInRecord = new SignInRecord();
            signInRecord.setRewarded(packet.isAutoRewarded());
            signInRecord.setRewardList(new RewardList());
            signInRecord.setSignInTime(serverDate);
            signInRecord.setCompensateTime(signCompensateDate);
            signInRecord.setSignInUUID(SakuraUtils.getPlayerUUIDString(player));
            // 是否自动领取
            if (packet.isAutoRewarded()) {
                boolean showFailed = player.hasPermissions(ServerConfig.PERMISSION_REWARD_FAILED_TIPS.get());
                Component msg = Component.translatable(player, EnumI18nType.MESSAGE, "receive_reward_success");
                rewardList.forEach(reward -> {
                    Component detail = reward.getName(SakuraUtils.getPlayerLanguage(player), true);
                    if (giveRewardToPlayer(player, signInData, reward)) {
                        detail.setColor(EnumMCColor.GREEN.getColor());
                        signInRecord.getRewardList().add(reward);
                        msg.append(", ").append(detail);
                    } else if (showFailed) {
                        detail.setColor(EnumMCColor.RED.getColor());
                        msg.append(", ").append(detail);
                    }
                });
                SakuraUtils.sendMessage(player, msg);
            } else {
                signInRecord.getRewardList().addAll(rewardList);
            }
            signInData.setLastSignInTime(serverDate);
            signInData.getSignInRecords().add(signInRecord);
            signInData.setContinuousSignInDays(DateUtils.calculateContinuousDays(signInData.getSignInRecords().stream()
                            .map(SignInRecord::getCompensateTime)
                            .collect(Collectors.toList())
                    , serverCompensateDate)
            );
            signInData.plusTotalSignInDays();
            SakuraUtils.sendMessage(player, Component.translatable(player
                    , EnumI18nType.MESSAGE
                    , "sign_in_success_s"
                    , DateUtils.toString(signInRecord.getCompensateTime())
                    , signInData.calculateContinuousDays()
                    , getTotalSignInDays(signInData)
            ));
            SakuraUtils.sendPacketToPlayer(new RewardCellDirtiedToClient(), player);
        }
        signInData.save(player);
        // 同步数据至客户端
        PlayerSignInDataCapability.syncPlayerData(player);
    }

    public static boolean giveRewardToPlayer(ServerPlayerEntity player, IPlayerSignInData signInData, Reward reward) {
        Object object = RewardManager.deserializeReward(reward);
        // 计算幸运、霉运加成
        int offset = 0;
        if (ServerConfig.REWARD_AFFECTED_BY_LUCK.get()) {
            offset = player.getActiveEffects().stream()
                    .filter(instance -> instance.getEffect() == Effects.LUCK || instance.getEffect() == Effects.UNLUCK)
                    .map(instance -> {
                        if (instance.getEffect() == Effects.LUCK) {
                            return instance.getAmplifier();
                        } else {
                            return -instance.getAmplifier();
                        }
                    }).reduce(0, Integer::sum);
        }
        if (random.nextDouble() > reward.getProbability().add(BigDecimal.valueOf(offset * 0.075)).doubleValue()) {
            reward.setDisabled(true);
            reward.setRewarded(true);
            return false;
        }
        boolean result = false;
        switch (reward.getType()) {
            case ITEM:
                result = RewardManager.giveItemStack(player, (ItemStack) object, true);
                break;
            case SIGN_IN_CARD:
                result = signInData.getSignInCard() != signInData.plusSignInCard((Integer) object);
                break;
            case EFFECT:
                result = player.addEffect((EffectInstance) object);
                break;
            case EXP_LEVEL:
                player.giveExperienceLevels((Integer) object);
                result = true;
                break;
            case EXP_POINT:
                player.giveExperiencePoints((Integer) object);
                result = true;
                break;
            case ADVANCEMENT:
                Advancement advancement = player.server.getAdvancements().getAdvancement((ResourceLocation) object);
                if (advancement != null) {
                    AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
                    for (String criterion : progress.getRemainingCriteria()) {
                        result = result || player.getAdvancements().award(advancement, criterion);
                    }
                }
                break;
            case MESSAGE:
                SakuraUtils.sendMessage(player, (Component) object);
                result = true;
                break;
            case COMMAND:
                String command = (String) object;
                command = command.replaceAll("@s", player.getName().getString());
                if (StringUtils.isNotNullOrEmpty(command)) {
                    result = SakuraUtils.executeCommand(player, command);
                }
                break;
            default:
        }
        reward.setRewarded(result);
        return result;
    }

    /**
     * 给予玩家物品
     *
     * @param player    目标玩家
     * @param itemStack 物品堆
     * @param drop      若玩家背包空间不足, 是否以物品实体的形式生成在世界上
     * @return 是否添加成功
     */
    public static boolean giveItemStack(ServerPlayerEntity player, ItemStack itemStack, boolean drop) {
        // 尝试将物品堆添加到玩家的库存中
        boolean added = player.inventory.add(itemStack);
        // 如果物品堆无法添加到库存，则以物品实体的形式生成在世界上
        if (!added && drop) {
            ItemEntity itemEntity = new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), itemStack);
            added = player.level.addFreshEntity(itemEntity);
        }
        return added;
    }

}
