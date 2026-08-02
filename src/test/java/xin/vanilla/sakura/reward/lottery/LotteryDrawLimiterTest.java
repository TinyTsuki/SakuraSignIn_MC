package xin.vanilla.sakura.reward.lottery;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LotteryDrawLimiterTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    public void dailyLimitResetsOnNextLocalDay() {
        LotteryPool pool = pool(LotteryLimitPolicy.DAILY, 1, 0);
        LotteryDrawState state = new LotteryDrawState("daily");
        long first = millis(2026, 8, 2, 23, 59);

        assertTrue(LotteryDrawLimiter.evaluate(pool, state, first, ZONE).isAllowed());
        LotteryDrawLimiter.recordSuccessfulDraw(state, first, ZONE);
        assertFalse(LotteryDrawLimiter.evaluate(pool, state, first, ZONE).isAllowed());
        assertTrue(LotteryDrawLimiter.evaluate(pool, state,
                millis(2026, 8, 3, 0, 0), ZONE).isAllowed());
    }

    @Test
    public void cooldownReportsRoundedRemainingSeconds() {
        LotteryPool pool = pool(LotteryLimitPolicy.COOLDOWN, 1, 60);
        LotteryDrawState state = new LotteryDrawState("cooldown");
        state.setLastDrawEpochMillis(1_000L);

        LotteryLimitResult blocked = LotteryDrawLimiter.evaluate(pool, state, 1_001L, ZONE);
        assertFalse(blocked.isAllowed());
        assertEquals(60L, blocked.getRetryAfterSeconds());
        assertTrue(LotteryDrawLimiter.evaluate(pool, state, 61_000L, ZONE).isAllowed());
    }

    @Test
    public void lifetimeLimitNeverResetsAndSelectionUsesWeights() {
        LotteryPool pool = pool(LotteryLimitPolicy.LIFETIME, 2, 0);
        LotteryDrawState state = new LotteryDrawState("lifetime");
        LotteryDrawLimiter.recordSuccessfulDraw(state, 1_000L, ZONE);
        LotteryDrawLimiter.recordSuccessfulDraw(state, 2_000L, ZONE);
        assertFalse(LotteryDrawLimiter.evaluate(pool, state,
                millis(2036, 1, 1, 0, 0), ZONE).isAllowed());

        Reward first = Reward.getDefault().setProbability(new java.math.BigDecimal("0.1"));
        Reward second = Reward.getDefault().setProbability(new java.math.BigDecimal("0.9"));
        assertSame(second, LotteryRewardService.select(Arrays.asList(first, second),
                new Random() {
                    @Override public double nextDouble() { return 0.95D; }
                }));
    }

    private static LotteryPool pool(LotteryLimitPolicy policy, int max, int cooldown) {
        return new LotteryPool("pool", "Pool", policy, max, cooldown,
                new RewardList(java.util.Collections.singletonList(Reward.getDefault())));
    }

    private static long millis(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZONE).toInstant().toEpochMilli();
    }
}
