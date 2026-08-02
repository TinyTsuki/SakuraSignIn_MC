package xin.vanilla.sakura.reward.lottery;

import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

/** 领取限制计算保持纯函数；只有 recordSuccessfulDraw 会修改玩家状态。 */
public final class LotteryDrawLimiter {
    private LotteryDrawLimiter() {
    }

    public static LotteryLimitResult evaluate(LotteryPool pool, LotteryDrawState state,
                                              long nowMillis, ZoneId zone) {
        LotteryLimitPolicy policy = pool.getLimitPolicy();
        int remaining = remainingDraws(pool, state, nowMillis, zone);
        if (remaining <= 0) {
            return new LotteryLimitResult(false, 0, 0);
        }
        if (pool.getCooldownSeconds() > 0 && state.getLastDrawEpochMillis() > 0) {
            long readyAt = state.getLastDrawEpochMillis() + pool.getCooldownSeconds() * 1000L;
            long retry = Math.max(0L, (readyAt - nowMillis + 999L) / 1000L);
            if (retry > 0L) {
                return new LotteryLimitResult(false, retry, remaining);
            }
        }
        return new LotteryLimitResult(true, 0, remaining);
    }

    private static int remainingDraws(LotteryPool pool, LotteryDrawState state,
                                      long nowMillis, ZoneId zone) {
        LotteryLimitPolicy policy = pool.getLimitPolicy();
        if (policy == LotteryLimitPolicy.UNLIMITED || policy == LotteryLimitPolicy.COOLDOWN) {
            return Integer.MAX_VALUE;
        }
        String currentPeriod = periodKey(policy, nowMillis, zone);
        boolean periodic = policy == LotteryLimitPolicy.DAILY
                || policy == LotteryLimitPolicy.WEEKLY
                || policy == LotteryLimitPolicy.MONTHLY;
        int used = periodic && !currentPeriod.equals(state.getPeriodKey())
                ? 0 : periodic ? state.getPeriodDraws() : state.getTotalDraws();
        return Math.max(0, pool.getMaxDraws() - used);
    }

    public static void recordSuccessfulDraw(LotteryPool pool, LotteryDrawState state,
                                            long nowMillis, ZoneId zone) {
        LotteryLimitPolicy policy = pool.getLimitPolicy();
        if (policy == LotteryLimitPolicy.DAILY || policy == LotteryLimitPolicy.WEEKLY
                || policy == LotteryLimitPolicy.MONTHLY) {
            String period = periodKey(policy, nowMillis, zone);
            if (!period.equals(state.getPeriodKey())) {
                state.setPeriodKey(period);
                state.setPeriodDraws(0);
            }
            state.setPeriodDraws(state.getPeriodDraws() + 1);
        }
        state.setTotalDraws(state.getTotalDraws() + 1);
        state.setLastDrawEpochMillis(nowMillis);
    }

    private static String periodKey(LotteryLimitPolicy policy, long nowMillis, ZoneId zone) {
        ZonedDateTime time = Instant.ofEpochMilli(nowMillis).atZone(zone);
        if (policy == LotteryLimitPolicy.WEEKLY) {
            WeekFields fields = WeekFields.ISO;
            return time.get(fields.weekBasedYear()) + "-W"
                    + time.get(fields.weekOfWeekBasedYear());
        }
        if (policy == LotteryLimitPolicy.MONTHLY) {
            return YearMonth.from(time).toString();
        }
        return time.toLocalDate().toString();
    }
}
