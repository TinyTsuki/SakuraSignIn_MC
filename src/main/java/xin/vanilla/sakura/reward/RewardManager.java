package xin.vanilla.sakura.reward;

import xin.vanilla.sakura.data.time.SakuraClock;

import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.config.CommonConfig;
import lombok.NonNull;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.message.SakuraMessages;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.api.reward.RewardGrantContext;
import xin.vanilla.sakura.api.reward.RewardGrantResult;
import xin.vanilla.sakura.config.reward.RewardGroup;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.util.*;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.CollectionUtils;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.banira.common.util.StringUtils;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 奖励管理器
 */
public class RewardManager {
    private static final Logger LOGGER = LogManager.getLogger();
    public static Component getRewardName(String languageCode, Reward reward, boolean withNum) {
        return RewardOperations.describe(languageCode, reward, withNum);
    }

    /**
     * 判断玩家是否签到
     *
     * @param signInData 玩家签到数据
     * @param date       日期
     * @param compensate 是否校准date
     */
    public static boolean isSignedIn(IPlayerSignInData signInData, Date date, boolean compensate) {
        Date target = compensate ? getCompensateDate(date) : date;
        return signInData.isSignedOn(target);
    }

    /**
     * 判断玩家是否领取奖励
     *
     * @param signInData 玩家签到数据
     * @param date       日期
     * @param compensate 是否校准date
     */
    public static boolean isRewarded(IPlayerSignInData signInData, Date date, boolean compensate) {
        Date target = compensate ? getCompensateDate(date) : date;
        return signInData.isRewardedOn(target);
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
        return DateUtils.toDateInt(getCompensateDate(SakuraClock.serverNow()));
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
            date = SakuraClock.serverNow();
        }
        return getCompensateDate(
                date,
                CommonConfig.get().cooling().timeCoolingMethod(),
                CommonConfig.get().cooling().timeCoolingTime()
        );
    }

    public static Date getCompensateDate(Date date, ETimeCoolingMethod method, double fixedTime) {
        double cooling;
        switch (method) {
            case MIXED:
            case FIXED_TIME:
                cooling = fixedTime;
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
            date = SakuraClock.serverNow();
        }
        // 签到冷却刷新时间, 固定间隔不需要校准时间
        double cooling;
        switch (CommonConfig.get().cooling().timeCoolingMethod()) {
            case MIXED:
            case FIXED_TIME:
                cooling = CommonConfig.get().cooling().timeCoolingTime();
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
                    // .peek(reward -> {
                    //     reward.setRewarded(true);
                    //     reward.setDisabled(true);
                    // })
                    .collect(Collectors.toList());
        }

        // 若签到记录存在，则添加签到奖励记录并直接返回
        if (CollectionUtils.isNotNullOrEmpty(rewardRecords)) {
            result.addAll(rewardRecords);
        }
        // 若签到记录不存在，则计算
        else {
            // 若日期小于当前日期 且 补签仅计算基础奖励
            if (!onlyHistory && key < nowCompensate8 && CommonConfig.get().makeUp().signInCardOnlyBaseReward()) {
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
                    // if (DateUtils.toDateInt(playerData.getLastSignInTime()) < nowCompensate8) {
                    //     continuousSignInDays++;
                    // }
                    continuousSignInDays += (int) DateUtils.daysOfTwo(
                            DateUtils.getDate(DateUtils.getLocalDate(getCompensateDate(SakuraClock.serverNow()))),
                            DateUtils.getDate(DateUtils.getLocalDate(currentDay)));
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
        List<RewardGroup> groups = RewardConfigManager.getRewardConfig().getRandomRewardGroups();
        BigDecimal totalProbability = groups.stream()
                .map(group -> new BigDecimal(group.getKey()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 如果总概率为 0，则返回空结果
        if (totalProbability.compareTo(BigDecimal.ZERO) != 0) {
            // 生成 0 到 totalProbability 之间的随机值
            BigDecimal randomValue = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())
                    .multiply(totalProbability)
                    .setScale(10, RoundingMode.HALF_UP);
            // 遍历概率池，找到对应的奖励
            BigDecimal cumulative = BigDecimal.ZERO;
            for (RewardGroup group : groups) {
                cumulative = cumulative.add(new BigDecimal(group.getKey()));
                if (randomValue.compareTo(cumulative) <= 0) {
                    result = group.getRewards();
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
        RewardList result = new RewardList();
        if (rewardList == null) {
            return result;
        }
        for (Reward candidate : rewardList) {
            boolean merged = false;
            for (int index = 0; index < result.size(); index++) {
                Optional<Reward> value = RewardOperations.merge(result.get(index), candidate);
                if (value.isPresent()) {
                    result.set(index, value.get());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(candidate);
            }
        }
        return result;
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
    public static void signIn(ServerPlayerEntity player, SignInPacket packet) {
        IPlayerSignInData signInData = SakuraPlayerData.get(player);
        String notificationType = ESignInType.REWARD.equals(packet.getSignInType())
                ? SakuraNotificationTypes.REWARD
                : SakuraNotificationTypes.SIGN_IN;
        Date serverDate = SakuraClock.serverNow();
        Date serverCompensateDate = getCompensateDate(serverDate);
        Date signCompensateDate = packet.getSignInType() == ESignInType.SIGN_IN ? serverCompensateDate : DateUtils.format(packet.getSignInTime());
        int serverCompensateDateInt = DateUtils.toDateInt(serverCompensateDate);
        int signCompensateDateInt = DateUtils.toDateInt(signCompensateDate);

        ETimeCoolingMethod coolingMethod = CommonConfig.get().cooling().timeCoolingMethod();
        // 判断签到/补签时间合法性
        if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt < signCompensateDateInt) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "sign_in_date_late_server_current_date_fail"), notificationType);
            SakuraPlayerData.saveAndSync(player);
            return;
        } else if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt > signCompensateDateInt) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "sign_in_date_early_server_current_date_fail"), notificationType);
            SakuraPlayerData.saveAndSync(player);
            return;
        } else if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && isSignedIn(signInData, signCompensateDate, false)) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "already_signed"), notificationType);
            SakuraPlayerData.saveAndSync(player);
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && serverCompensateDateInt <= signCompensateDateInt) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "compensate_date_not_early_server_current_date_fail"), notificationType);
            SakuraPlayerData.saveAndSync(player);
            return;
        }
        // 判断签到CD
        if (ESignInType.SIGN_IN.equals(packet.getSignInType()) && coolingMethod.getCode() >= ETimeCoolingMethod.FIXED_INTERVAL.getCode()) {
            Date lastSignInTime = DateUtils.addDate(signInData.getLastSignInTime(), CommonConfig.get().cooling().timeCoolingInterval());
            if (serverDate.before(lastSignInTime)) {
                SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "sign_in_cool_down_fail"), notificationType);
                SakuraPlayerData.saveAndSync(player);
                return;
            }
        }
        // 判断补签
        if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && !CommonConfig.get().makeUp().signInCard()) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "server_not_enable_sign_in_card_fail"), notificationType);
            SakuraPlayerData.saveAndSync(player);
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && signInData.getSignInCard() <= 0) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "not_enough_sign_in_card_fail"), notificationType);
            SakuraPlayerData.saveAndSync(player);
            return;
        } else if (ESignInType.RE_SIGN_IN.equals(packet.getSignInType()) && isSignedIn(signInData, signCompensateDate, false)) {
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "word", "already_signed"), notificationType);
            SakuraPlayerData.saveAndSync(player);
            return;
        }
        // 判断领取奖励
        if (ESignInType.REWARD.equals(packet.getSignInType())) {
            if (isRewarded(signInData, signCompensateDate, false)) {
                SakuraMessages.send(player, SakuraComponent.get().trans(player, "format", "already_receive_reward_s", DateUtils.toString(signCompensateDate)), notificationType);
                SakuraPlayerData.saveAndSync(player);
                return;
            } else if (!isSignedIn(signInData, signCompensateDate, false)) {
                SakuraMessages.send(player, SakuraComponent.get().trans(player, "format", "not_sign_in", DateUtils.toString(signCompensateDate)), notificationType);
                SakuraPlayerData.saveAndSync(player);
                return;
            } else {
                boolean showFailed = player.hasPermissions(CommonConfig.get().permission().permissionRewardFailedTips());
                Component msg = SakuraComponent.get().trans(player, "word", "receive_reward_success");
                Optional<SignInRecord> storedRecord = signInData.getSignInRecords().stream()
                        .filter(record -> DateUtils.toDateInt(record.getCompensateTime()) == DateUtils.toDateInt(signCompensateDate))
                        .filter(record -> !record.isRewarded())
                        .findFirst();
                RewardList claimable = storedRecord
                        .map(SignInRecord::getRewardList)
                        // DELETE_MONTH_FILE 没有历史快照时，按永久摘要重新计算该日奖励。
                        .orElseGet(() -> getRewardListByDate(
                                signCompensateDate, signInData, false, true).clone());
                claimable.stream()
                        .filter(reward -> !reward.isDisabled())
                        .filter(reward -> !reward.isRewarded())
                        .forEach(reward -> {
                            reward.setDisabled(true);
                            reward.setRewarded(true);
                            Component detail = reward.getName(SakuraUtils.getPlayerLanguage(player), true);
                            if (giveRewardToPlayer(player, signInData, reward)) {
                                detail.color(Color.GREEN.getRGB());
                                msg.append(", ").append(detail);
                            } else if (showFailed) {
                                detail.color(Color.RED.getRGB());
                                msg.append(", ").append(detail);
                            }
                        });
                storedRecord.ifPresent(record -> record.setRewarded(true));
                signInData.markSigned(signCompensateDate, true);
                SakuraMessages.send(player, msg, notificationType);
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
                boolean showFailed = player.hasPermissions(CommonConfig.get().permission().permissionRewardFailedTips());
                Component msg = SakuraComponent.get().trans(player, "word", "receive_reward_success");
                rewardList.forEach(reward -> {
                    Component detail = reward.getName(SakuraUtils.getPlayerLanguage(player), true);
                    if (giveRewardToPlayer(player, signInData, reward)) {
                        detail.color(Color.GREEN.getRGB());
                        signInRecord.getRewardList().add(reward);
                        msg.append(", ").append(detail);
                    } else if (showFailed) {
                        detail.color(Color.RED.getRGB());
                        msg.append(", ").append(detail);
                    }
                });
                SakuraMessages.send(player, msg, notificationType);
            } else {
                signInRecord.getRewardList().addAll(rewardList);
            }
            signInData.setLastSignInTime(serverDate);
            signInData.getSignInRecords().add(signInRecord);
            signInData.markSigned(signCompensateDate, packet.isAutoRewarded());
            signInData.plusTotalSignInDays();
            signInData.setContinuousSignInDays(signInData.calculateContinuousDays(serverCompensateDate));
            SakuraMessages.send(player, SakuraComponent.get().trans(player, "format", "sign_in_success_s", DateUtils.toString(signInRecord.getCompensateTime()), signInData.calculateContinuousDays(), getTotalSignInDays(signInData)), notificationType);
        }
        // 持久化后再同步，客户端不会参与服务端存储。
        SakuraPlayerData.saveAndSync(player);
    }

    public static boolean giveRewardToPlayer(ServerPlayerEntity player, IPlayerSignInData signInData, Reward reward) {
        reward.setRewarded(true);
        // 判断是否启用
        if (CommonConfig.get().reward().rewardAffectedByLuck()) {
            int offset = player.getActiveEffects().stream()
                    .filter(instance -> instance.getEffect() == Effects.LUCK || instance.getEffect() == Effects.UNLUCK)
                    .map(instance -> {
                        if (instance.getEffect() == Effects.LUCK) {
                            return instance.getAmplifier();
                        } else {
                            return -instance.getAmplifier();
                        }
                    }).reduce(0, Integer::sum);
            if (new Random().nextDouble() > reward.getProbability().add(BigDecimal.valueOf(offset * 0.075)).doubleValue())
                return false;
        }
        RewardGrantResult result = RewardOperations.grant(new RewardGrantContext() {
            @Override
            public ServerPlayerEntity player() {
                return player;
            }

            @Override
            public UUID playerId() {
                return player.getUUID();
            }

            @Override
            public Date signInDate() {
                return new Date();
            }

            @Override
            public String sourceId() {
                return "sign_in";
            }

            @Override
            public void addSignInCards(int amount) {
                signInData.plusSignInCard(amount);
            }
        }, reward);
        if (!result.isSuccess()) {
            LOGGER.warn("Skipped reward type {}: {} ({})", reward.getTypeId(),
                    result.getStatus(), result.getDetail());
        }
        return result.isSuccess();
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
        if (!added && !itemStack.isEmpty() && drop) {
            ItemEntity itemEntity = player.drop(itemStack, false);
            if (itemEntity != null) {
                itemEntity.setNoPickUpDelay();
                itemEntity.setThrower(player);
            }
        }
        return added;
    }

}
