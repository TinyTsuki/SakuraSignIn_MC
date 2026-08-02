package xin.vanilla.sakura.reward.lottery;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardManager;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** 服务端权威执行一次抽奖并持久化玩家领取状态。 */
public final class LotteryRewardService {
    private static final Random RANDOM = new Random();

    private LotteryRewardService() {
    }

    public static LotteryDrawResult draw(ServerPlayerEntity player, String poolId) {
        return draw(player, poolId, System.currentTimeMillis(), ZoneId.systemDefault(), RANDOM);
    }

    static LotteryDrawResult draw(ServerPlayerEntity player, String poolId, long nowMillis,
                                  ZoneId zone, Random random) {
        LotteryPool pool = RewardConfigManager.lotteryPool(poolId);
        if (pool == null) {
            return result(LotteryDrawStatus.POOL_NOT_FOUND, null, null, 0, 0);
        }
        List<Reward> candidates = eligibleRewards(pool);
        if (candidates.isEmpty()) {
            return result(LotteryDrawStatus.EMPTY_POOL, pool, null, 0, 0);
        }
        IPlayerSignInData data = SakuraPlayerData.get(player);
        LotteryDrawState state = state(data, poolId);
        LotteryLimitResult limit = LotteryDrawLimiter.evaluate(pool, state, nowMillis, zone);
        if (!limit.isAllowed()) {
            return result(LotteryDrawStatus.LIMIT_REACHED, pool, null,
                    limit.getRetryAfterSeconds(), limit.getRemainingDraws());
        }
        Reward selected = select(candidates, random).clone();
        if (!RewardManager.giveGuaranteedRewardToPlayer(player, data, selected,
                new Date(nowMillis), "lottery:" + poolId)) {
            return result(LotteryDrawStatus.GRANT_FAILED, pool, selected, 0,
                    limit.getRemainingDraws());
        }
        LotteryDrawLimiter.recordSuccessfulDraw(state, nowMillis, zone);
        SakuraPlayerData.saveAndSync(player);
        LotteryLimitResult after = LotteryDrawLimiter.evaluate(pool, state, nowMillis, zone);
        return result(LotteryDrawStatus.SUCCESS, pool, selected, 0,
                after.getRemainingDraws());
    }

    /** 概率按相对权重解释，允许管理员写 1、0.5、0.01 等任意正数。 */
    static Reward select(List<Reward> candidates, Random random) {
        BigDecimal total = BigDecimal.ZERO;
        for (Reward reward : candidates) {
            total = total.add(weight(reward));
        }
        double point = random.nextDouble() * total.doubleValue();
        double cursor = 0D;
        for (Reward reward : candidates) {
            cursor += weight(reward).doubleValue();
            if (point < cursor) {
                return reward;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static List<Reward> eligibleRewards(LotteryPool pool) {
        List<Reward> result = new ArrayList<>();
        pool.getRewards().stream().filter(Objects::nonNull)
                .filter(reward -> !reward.isDisabled())
                .filter(reward -> weight(reward).compareTo(BigDecimal.ZERO) > 0)
                .forEach(result::add);
        return result;
    }

    private static BigDecimal weight(Reward reward) {
        BigDecimal value = reward.getProbability();
        return value == null || value.signum() <= 0 ? BigDecimal.ZERO : value;
    }

    private static LotteryDrawState state(IPlayerSignInData data, String poolId) {
        return data.getLotteryDrawStates().stream()
                .filter(value -> poolId.equals(value.getPoolId()))
                .findFirst().orElseGet(() -> {
                    LotteryDrawState value = new LotteryDrawState(poolId);
                    data.getLotteryDrawStates().add(value);
                    return value;
                });
    }

    private static LotteryDrawResult result(LotteryDrawStatus status, LotteryPool pool,
                                            Reward reward, long retry, int remaining) {
        return new LotteryDrawResult(status, pool, reward, retry, remaining);
    }
}
