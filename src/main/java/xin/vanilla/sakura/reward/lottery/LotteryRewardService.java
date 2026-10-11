package xin.vanilla.sakura.reward.lottery;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiPredicate;

/** Shared command entry; native platform selects the world-owned operation service. */
public final class LotteryRewardService {
    public static final int MAX_BATCH_DRAWS = 100;
    private static volatile Function<Object, LotteryOperationService> services = player -> null;
    private static volatile BiPredicate<Object, Object> origins = (player, origin) -> false;
    private LotteryRewardService() { }
    public static void install(Function<Object, LotteryOperationService> factory, BiPredicate<Object, Object> matcher) {
        services = Objects.requireNonNull(factory); origins = Objects.requireNonNull(matcher);
    }
    public static boolean isCurrent(Object player, Object origin) { return origins.test(player, origin); }
    public static LotteryOperationService operations(Object player) { return services.apply(player); }
    public static void recover(Object player) {
        LotteryOperationService service = player == null ? null : operations(player);
        if (service != null) service.recover(player);
    }
    public static LotteryDrawBatchResult drawMany(Object player, String poolId, int count) {
        LotteryOperationService service = player == null ? null : operations(player);
        return service == null ? new LotteryDrawBatchResult(LotteryDrawStatus.PAYMENT_UNAVAILABLE, null,
                Collections.emptyList(), 0, 0) : service.commandDraw(player, poolId, count);
    }
    public static LotteryDrawResult draw(Object player, String poolId) {
        LotteryDrawBatchResult result = drawMany(player, poolId, 1);
        return new LotteryDrawResult(result.getStatus(), result.getPool(), result.getRewards().isEmpty()
                ? null : result.getRewards().get(0), result.getRetryAfterSeconds(), result.getRemainingDraws());
    }
}
