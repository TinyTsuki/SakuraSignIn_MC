package xin.vanilla.sakura.data.time;

import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.IPlayerSignInData;

import java.time.LocalDate;
import java.util.Objects;

/** Loader-neutral facade for vanilla play-time statistics. */
public final class SakuraOnlineTime {
    private static volatile OnlineTimeProvider provider;

    private SakuraOnlineTime() {
    }

    public static void install(OnlineTimeProvider value) {
        provider = Objects.requireNonNull(value, "value");
    }

    public static OnlineTimeRequirementResult evaluate(Object player,
                                                       IPlayerSignInData data,
                                                       LocalDate today) {
        OnlineTimeProvider current = provider;
        if (current == null) {
            throw new IllegalStateException("Online time provider is not installed");
        }
        OnlineTimeRequirementResult result = OnlineTimeRequirement.evaluate(
                data.getOnlineTimeBaselineDate(), data.getOnlineTimeBaselineTicks(),
                today, current.playTicks(player),
                CommonConfig.get().server().requiredTotalOnlineSeconds(),
                CommonConfig.get().server().requiredTodayOnlineSeconds());
        if (result.isBaselineChanged()) {
            data.setOnlineTimeBaselineDate(result.getBaselineDate());
            data.setOnlineTimeBaselineTicks(result.getBaselineTicks());
        }
        return result;
    }
}
