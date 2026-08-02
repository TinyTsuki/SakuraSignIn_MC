package xin.vanilla.sakura.reward.lottery;

import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 领取限制计算保持纯函数；只有 recordSuccessfulDraw 会修改玩家状态。 */
public final class LotteryDrawLimiter {
    private LotteryDrawLimiter() {
    }

    public static LotteryLimitResult evaluate(LotteryPool pool, LotteryDrawState state,
                                              long nowMillis, ZoneId zone) {
        LotteryLimitPolicy policy = pool.getLimitPolicy();
        if (policy == LotteryLimitPolicy.UNLIMITED) {
            return new LotteryLimitResult(true, 0, Integer.MAX_VALUE);
        }
        if (policy == LotteryLimitPolicy.COOLDOWN) {
            long readyAt = state.getLastDrawEpochMillis() + pool.getCooldownSeconds() * 1000L;
            long retry = Math.max(0L, (readyAt - nowMillis + 999L) / 1000L);
            return new LotteryLimitResult(retry == 0L, retry, retry == 0L ? 1 : 0);
        }
        int used = policy == LotteryLimitPolicy.DAILY
                && !periodKey(nowMillis, zone).equals(state.getPeriodKey())
                ? 0 : policy == LotteryLimitPolicy.DAILY
                ? state.getPeriodDraws() : state.getTotalDraws();
        int remaining = Math.max(0, pool.getMaxDraws() - used);
        return new LotteryLimitResult(remaining > 0, 0, remaining);
    }

    public static void recordSuccessfulDraw(LotteryDrawState state, long nowMillis,
                                            ZoneId zone) {
        String period = periodKey(nowMillis, zone);
        if (!period.equals(state.getPeriodKey())) {
            state.setPeriodKey(period);
            state.setPeriodDraws(0);
        }
        state.setPeriodDraws(state.getPeriodDraws() + 1);
        state.setTotalDraws(state.getTotalDraws() + 1);
        state.setLastDrawEpochMillis(nowMillis);
    }

    private static String periodKey(long nowMillis, ZoneId zone) {
        return Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString();
    }
}
