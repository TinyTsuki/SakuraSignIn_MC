package xin.vanilla.sakura.data.lottery;

import xin.vanilla.banira.api.cost.CostPaymentPlan;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigCodec;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardJsonCodec;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.time.ZoneId;

/** Immutable server-owned draw snapshot and write-ahead grant progress. */
public final class LotteryOperation {
    public enum Stage { PREPARED, PAYING, PAID, GRANTING, COMPLETED, ROLLED_BACK, UNCERTAIN }

    private final UUID playerId;
    private final LotteryDrawRequest request;
    private final String poolSnapshot;
    private final int count;
    private final long seed;
    private final long createdAt;
    private final String displayToken;
    private final Stage stage;
    private final int grantedCount;
    private final List<String> rewards;
    private final CostPaymentPlan receipt;
    private final LotteryDrawState quotaBefore;
    private final String zoneId;
    private final boolean acknowledged;
    private final boolean rollbackPending;

    LotteryOperation(UUID playerId, LotteryDrawRequest request, String poolSnapshot, int count,
                     long seed, long createdAt, String displayToken, Stage stage, int grantedCount,
                     List<String> rewards, CostPaymentPlan receipt, LotteryDrawState quotaBefore,
                     String zoneId, boolean acknowledged, boolean rollbackPending) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.request = Objects.requireNonNull(request, "request");
        this.poolSnapshot = Objects.requireNonNull(poolSnapshot, "poolSnapshot");
        this.count = count;
        this.seed = seed;
        this.createdAt = createdAt;
        this.displayToken = LotteryProtocolLimits.token(displayToken);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.grantedCount = grantedCount;
        this.rewards = Collections.unmodifiableList(new ArrayList<>(rewards));
        this.receipt = receipt;
        this.quotaBefore = copyState(Objects.requireNonNull(quotaBefore, "quotaBefore"));
        this.zoneId = ZoneId.of(LotteryProtocolLimits.text(zoneId, 64)).getId();
        this.acknowledged = acknowledged;
        this.rollbackPending = rollbackPending;
        validate();
    }

    public static LotteryOperation prepare(UUID playerId, LotteryDrawRequest request, LotteryPool pool,
                                           int count, long seed, long createdAt, String displayToken) {
        return prepare(playerId, request, pool, count, seed, createdAt, displayToken,
                new LotteryDrawState(pool.getId()), ZoneId.systemDefault());
    }

    public static LotteryOperation prepare(UUID playerId, LotteryDrawRequest request, LotteryPool pool,
                                           int count, long seed, long createdAt, String displayToken,
                                           LotteryDrawState quotaBefore, ZoneId zone) {
        try {
            RewardConfig config = new RewardConfig();
            config.getLotteryPools().add(LotteryPools.copy(pool));
            return new LotteryOperation(playerId, request, new RewardConfigCodec().encode(config),
                    count, seed, createdAt, displayToken, Stage.PREPARED, 0,
                    Collections.emptyList(), null, quotaBefore, zone.getId(), false, false);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid lottery snapshot", failure);
        }
    }

    private void validate() {
        LotteryPool pool = getPool();
        if (!pool.getId().equals(quotaBefore.getPoolId()) || quotaBefore.getPeriodDraws() < 0
                || quotaBefore.getTotalDraws() < 0 || quotaBefore.getLastDrawEpochMillis() < 0
                || quotaBefore.getTotalDraws() > Integer.MAX_VALUE - count
                || quotaBefore.getPeriodDraws() > Integer.MAX_VALUE - count
                || (acknowledged && stage != Stage.COMPLETED)) invalidStage();
        if (rollbackPending && (grantedCount != 0 || (stage != Stage.PAID
                && stage != Stage.ROLLED_BACK && stage != Stage.UNCERTAIN))) invalidStage();
        if (count < 1 || count > 100 || createdAt < 0 || !pool.getId().equals(request.getRequest().getPoolId())
                || (request.getRequest().getCount() != -1 && request.getRequest().getCount() != count)
                || ((pool.getLimitPolicy() == LotteryLimitPolicy.COOLDOWN || pool.getCooldownSeconds() > 0)
                    && count != 1) || eligible(pool).isEmpty()) {
            throw new IllegalArgumentException("Invalid lottery draw snapshot");
        }
        if (grantedCount < 0 || grantedCount > count || (!rewards.isEmpty() && rewards.size() != count)
                || (grantedCount > 0 && rewards.isEmpty())) {
            throw new IllegalArgumentException("Invalid lottery grant prefix");
        }
        if (!rewards.isEmpty() && !rewards.equals(selected(pool))) {
            throw new IllegalArgumentException("Lottery results differ from stored seed");
        }
        if (stage == Stage.PREPARED || stage == Stage.PAYING) {
            if (receipt != null || !rewards.isEmpty() || grantedCount != 0) invalidStage();
        } else if (stage == Stage.PAID || stage == Stage.GRANTING || stage == Stage.COMPLETED) {
            if (!confirmed(receipt) || receipt.units() != count) invalidStage();
            if (stage == Stage.PAID && grantedCount > count) invalidStage();
            if (stage == Stage.GRANTING && (rewards.isEmpty() || grantedCount >= count)) invalidStage();
            if (stage == Stage.COMPLETED && grantedCount != count) invalidStage();
        } else if (stage == Stage.ROLLED_BACK && (grantedCount != 0 || (receipt != null && !confirmed(receipt)))) {
            invalidStage();
        }
        if (receipt != null && (!receipt.isCommitted() || receipt.units() != count)) invalidStage();
        if (receipt != null && !LotteryPoolValidator.isFree(pool) && receipt.requirements().isEmpty()) invalidStage();
        if (receipt != null && receipt.hasConfirmedSnapshots()) {
            java.util.Set<String> required = new java.util.HashSet<>();
            for (xin.vanilla.banira.api.cost.ResourceCost cost : receipt.requirements()) {
                if (cost.amount() <= 0) invalidStage();
                required.add(cost.resourceId());
            }
            java.util.Set<String> snapshots = new java.util.HashSet<>();
            for (CostPaymentPlan.ResourceSnapshot snapshot : receipt.snapshots()) {
                if (!snapshots.add(snapshot.resourceId())) invalidStage();
            }
            if (!required.equals(snapshots)) invalidStage();
        }
        if (receipt == null && (!rewards.isEmpty() || grantedCount != 0)) invalidStage();
    }

    private static void invalidStage() { throw new IllegalArgumentException("Invalid lottery operation stage"); }
    private static boolean confirmed(CostPaymentPlan receipt) {
        return receipt != null && receipt.isSuccess() && receipt.isCommitted()
                && receipt.hasConfirmedSnapshots() && !receipt.recoveryFailure().isFailure();
    }

    public LotteryOperation paying() { require(Stage.PREPARED); return next(Stage.PAYING, 0, rewards, null); }

    public LotteryOperation paid(CostPaymentPlan receipt) {
        require(Stage.PAYING);
        Objects.requireNonNull(receipt, "receipt");
        if (!receipt.isCommitted() || receipt.units() != count) invalidStage();
        if (receipt.recoveryFailure().isFailure()) return next(Stage.UNCERTAIN, 0, rewards, receipt);
        if (!confirmed(receipt)) invalidStage();
        return next(Stage.PAID, 0, rewards, receipt);
    }

    public LotteryOperation selectRewards() {
        require(Stage.PAID);
        if (rollbackPending) throw new IllegalStateException("Payment restoration pending");
        return rewards.isEmpty() ? next(Stage.PAID, grantedCount, selected(getPool()), receipt) : this;
    }

    public LotteryOperation granting() {
        require(Stage.PAID);
        if (rollbackPending || rewards.isEmpty() || grantedCount == count) throw new IllegalStateException("Lottery grant is not pending");
        return next(Stage.GRANTING, grantedCount, rewards, receipt);
    }

    public LotteryOperation grantSucceeded() {
        require(Stage.GRANTING);
        int granted = grantedCount + 1;
        return next(Stage.PAID, granted, rewards, receipt);
    }

    public LotteryOperation complete() {
        require(Stage.PAID);
        if (grantedCount != count) throw new IllegalStateException("Lottery rewards are incomplete");
        return next(Stage.COMPLETED, grantedCount, rewards, receipt);
    }

    public LotteryOperation acknowledge() {
        require(Stage.COMPLETED);
        return new LotteryOperation(playerId, request, poolSnapshot, count, seed, createdAt, displayToken,
                stage, grantedCount, rewards, receipt, quotaBefore, zoneId, true, false);
    }

    public LotteryOperation rolledBack() {
        if (grantedCount != 0 || (stage != Stage.PREPARED && stage != Stage.PAYING && stage != Stage.PAID))
            throw new IllegalStateException("Lottery operation cannot roll back");
        if (stage == Stage.PAID && !rollbackPending) throw new IllegalStateException("Restoration intent not persisted");
        return next(Stage.ROLLED_BACK, 0, rewards, receipt);
    }

    public LotteryOperation beginRollback() {
        require(Stage.PAID);
        if (grantedCount != 0 || rollbackPending) throw new IllegalStateException("Restoration cannot start");
        return new LotteryOperation(playerId, request, poolSnapshot, count, seed, createdAt, displayToken,
                stage, grantedCount, rewards, receipt, quotaBefore, zoneId, false, true);
    }

    public LotteryOperation uncertain() {
        if (isTerminal()) throw new IllegalStateException("Terminal lottery operation");
        return next(Stage.UNCERTAIN, grantedCount, rewards, receipt);
    }

    public LotteryOperation recover() {
        if (isTerminal()) return this;
        if (stage == Stage.PREPARED) return rolledBack();
        if (stage == Stage.PAYING || stage == Stage.GRANTING || rollbackPending) return uncertain();
        return this;
    }

    private void require(Stage expected) {
        if (stage != expected) throw new IllegalStateException("Expected lottery stage " + expected);
    }
    private LotteryOperation next(Stage next, int granted, List<String> selected, CostPaymentPlan payment) {
        return new LotteryOperation(playerId, request, poolSnapshot, count, seed, createdAt, displayToken,
                next, granted, selected, payment, quotaBefore, zoneId, acknowledged, rollbackPending);
    }

    private static List<Reward> eligible(LotteryPool pool) {
        List<Reward> result = new ArrayList<>();
        for (Reward reward : pool.getRewards()) {
            if (reward != null && !reward.isDisabled() && reward.getProbability().signum() > 0) {
                BigDecimal weight = reward.getProbability();
                if (weight.precision() > 1024 || Math.abs((long) weight.scale()) > 1024)
                    throw new IllegalArgumentException("Lottery weight exceeds arithmetic budget");
                result.add(reward);
            }
        }
        return result;
    }
    private List<String> selected(LotteryPool pool) {
        List<Reward> candidates = eligible(pool);
        BigDecimal total = BigDecimal.ZERO;
        for (Reward reward : candidates) total = total.add(reward.getProbability());
        Random random = new Random(seed);
        List<String> result = new ArrayList<>();
        for (int draw = 0; draw < count; draw++) {
            BigDecimal point = BigDecimal.valueOf(random.nextDouble()).multiply(total);
            Reward winner = candidates.get(candidates.size() - 1);
            BigDecimal cursor = BigDecimal.ZERO;
            for (Reward candidate : candidates) {
                cursor = cursor.add(candidate.getProbability());
                if (point.compareTo(cursor) < 0) { winner = candidate; break; }
            }
            Reward selected = RewardJsonCodec.decode(RewardJsonCodec.encode(winner));
            selected.setProbability(BigDecimal.ONE).setRewarded(false);
            result.add(RewardJsonCodec.encode(selected).toString());
        }
        return result;
    }

    public UUID getPlayerId() { return playerId; }
    public LotteryDrawRequest getRequest() { return request; }
    public String getOperationId() { return request.getOperationId(); }
    public int getCount() { return count; }
    public long getSeed() { return seed; }
    public long getCreatedAt() { return createdAt; }
    public String getDisplayToken() { return displayToken; }
    public Stage getStage() { return stage; }
    public int getGrantedCount() { return grantedCount; }
    public CostPaymentPlan getReceipt() { return receipt; }
    public boolean isAcknowledged() { return acknowledged; }
    public boolean isRollbackPending() { return rollbackPending; }
    public String getZoneId() { return zoneId; }
    public LotteryDrawState getQuotaBefore() { return copyState(quotaBefore); }
    public LotteryDrawState getQuotaAfter() {
        LotteryDrawState result = getQuotaBefore();
        LotteryPool pool = getPool();
        for (int i = 0; i < grantedCount; i++)
            xin.vanilla.sakura.reward.lottery.LotteryDrawLimiter.recordSuccessfulDraw(
                    pool, result, createdAt, ZoneId.of(zoneId));
        return result;
    }
    private static LotteryDrawState copyState(LotteryDrawState source) {
        LotteryDrawState result = new LotteryDrawState(source.getPoolId());
        result.setPeriodKey(source.getPeriodKey()); result.setPeriodDraws(source.getPeriodDraws());
        result.setTotalDraws(source.getTotalDraws()); result.setLastDrawEpochMillis(source.getLastDrawEpochMillis());
        return result;
    }
    public boolean isTerminal() { return stage == Stage.COMPLETED || stage == Stage.ROLLED_BACK; }
    String poolSnapshot() { return poolSnapshot; }
    List<String> rewardSnapshots() { return rewards; }

    public LotteryPool getPool() {
        try {
            List<LotteryPool> pools = new RewardConfigCodec().decode(poolSnapshot).getLotteryPools();
            if (pools.size() != 1) throw new IllegalArgumentException("One lottery snapshot required");
            return pools.get(0);
        } catch (IOException failure) { throw new IllegalArgumentException("Invalid lottery snapshot", failure); }
    }
    public List<Reward> getRewards() {
        List<Reward> result = new ArrayList<>();
        for (String reward : rewards) result.add(RewardJsonCodec.decode(new com.google.gson.JsonParser().parse(reward)));
        return result;
    }
}
