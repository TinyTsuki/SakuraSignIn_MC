package xin.vanilla.sakura.reward.lottery;

import net.minecraft.server.level.ServerPlayer;
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
    public static final int MAX_BATCH_DRAWS = 100;
    private static final Random RANDOM = new Random();

    private LotteryRewardService() {
    }

    public static LotteryDrawResult draw(ServerPlayer player, String poolId) {
        LotteryDrawBatchResult batch = drawMany(player, poolId, 1);
        Reward reward = batch.getRewards() == null || batch.getRewards().isEmpty()
                ? null : batch.getRewards().get(0);
        return result(batch.getStatus(), batch.getPool(), reward,
                batch.getRetryAfterSeconds(), batch.getRemainingDraws());
    }

    public static LotteryDrawBatchResult drawMany(ServerPlayer player, String poolId,
                                                   int requestedCount) {
        long now = System.currentTimeMillis();
        LotteryDrawBatchResult prepared = prepareMany(player, poolId, requestedCount,
                now, ZoneId.systemDefault(), RANDOM);
        return prepared.isSuccess() ? claimPrepared(player, prepared, now,
                ZoneId.systemDefault()) : prepared;
    }

    static LotteryDrawBatchResult drawMany(ServerPlayer player, String poolId,
                                           int requestedCount, long nowMillis,
                                           ZoneId zone, Random random) {
        LotteryDrawBatchResult prepared = prepareMany(player, poolId, requestedCount,
                nowMillis, zone, random);
        return prepared.isSuccess() ? claimPrepared(player, prepared, nowMillis, zone) : prepared;
    }

    public static LotteryDrawBatchResult prepareMany(ServerPlayer player, String poolId,
                                                      int requestedCount) {
        return prepareMany(player, poolId, requestedCount, System.currentTimeMillis(),
                ZoneId.systemDefault(), RANDOM);
    }

    static LotteryDrawBatchResult prepareMany(ServerPlayer player, String poolId,
                                               int requestedCount, long nowMillis,
                                               ZoneId zone, Random random) {
        LotteryPool pool = RewardConfigManager.lotteryPool(poolId);
        if (pool == null) {
            return batch(LotteryDrawStatus.POOL_NOT_FOUND, null,
                    java.util.Collections.emptyList(), 0, 0);
        }
        List<Reward> candidates = eligibleRewards(pool);
        if (candidates.isEmpty()) {
            return batch(LotteryDrawStatus.EMPTY_POOL, pool,
                    java.util.Collections.emptyList(), 0, 0);
        }
        IPlayerSignInData data = SakuraPlayerData.get(player);
        LotteryDrawState state = state(data, poolId);
        LotteryLimitResult limit = LotteryDrawLimiter.evaluate(pool, state, nowMillis, zone);
        if (!limit.isAllowed()) {
            return batch(LotteryDrawStatus.LIMIT_REACHED, pool,
                    java.util.Collections.emptyList(), limit.getRetryAfterSeconds(),
                    limit.getRemainingDraws());
        }
        int target = requestedCount < 0 ? allDrawCount(pool, limit)
                : Math.max(1, Math.min(MAX_BATCH_DRAWS, requestedCount));
        if (limit.getRemainingDraws() != Integer.MAX_VALUE) {
            target = Math.min(target, limit.getRemainingDraws());
        }
        List<Reward> selectedRewards = new ArrayList<>();
        for (int i = 0; i < target; i++) {
            selectedRewards.add(select(candidates, random).clone());
        }
        return batch(LotteryDrawStatus.SUCCESS, pool, selectedRewards,
                limit.getRetryAfterSeconds(), limit.getRemainingDraws());
    }

    public static LotteryDrawBatchResult claimPrepared(ServerPlayer player,
                                                         LotteryDrawBatchResult prepared) {
        return claimPrepared(player, prepared, System.currentTimeMillis(), ZoneId.systemDefault());
    }

    private static LotteryDrawBatchResult claimPrepared(ServerPlayer player,
                                                          LotteryDrawBatchResult prepared,
                                                          long nowMillis, ZoneId zone) {
        if (prepared == null || !prepared.isSuccess() || prepared.getPool() == null) {
            return prepared;
        }
        LotteryPool pool = prepared.getPool();
        IPlayerSignInData data = SakuraPlayerData.get(player);
        LotteryDrawState state = state(data, pool.getId());
        List<Reward> granted = new ArrayList<>();
        for (Reward reward : prepared.getRewards()) {
            Reward selected = reward.clone();
            if (!RewardManager.giveGuaranteedRewardToPlayer(player, data, selected,
                    new Date(nowMillis), "lottery:" + pool.getId())) {
                if (granted.isEmpty()) {
                    return batch(LotteryDrawStatus.GRANT_FAILED, pool,
                            java.util.Collections.singletonList(selected), 0, 0);
                }
                break;
            }
            granted.add(selected);
            LotteryDrawLimiter.recordSuccessfulDraw(pool, state, nowMillis, zone);
        }
        SakuraPlayerData.saveAndSync(player);
        LotteryLimitResult after = LotteryDrawLimiter.evaluate(pool, state, nowMillis, zone);
        return batch(granted.isEmpty() ? LotteryDrawStatus.GRANT_FAILED : LotteryDrawStatus.SUCCESS,
                pool, granted,
                after.getRetryAfterSeconds(), after.getRemainingDraws());
    }

    private static int allDrawCount(LotteryPool pool, LotteryLimitResult limit) {
        if (limit.getRemainingDraws() == Integer.MAX_VALUE) {
            return 1;
        }
        return Math.max(1, Math.min(MAX_BATCH_DRAWS, limit.getRemainingDraws()));
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

    private static LotteryDrawBatchResult batch(LotteryDrawStatus status, LotteryPool pool,
                                                List<Reward> rewards, long retry,
                                                int remaining) {
        return new LotteryDrawBatchResult(status, pool, rewards, retry, remaining);
    }
}
