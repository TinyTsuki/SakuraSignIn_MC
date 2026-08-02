package xin.vanilla.sakura.data.lottery;

import java.util.ArrayList;
import java.util.List;

/** 奖池配置的公共边界校验。 */
public final class LotteryPoolValidator {
    private LotteryPoolValidator() {
    }

    public static List<String> validate(LotteryPool pool) {
        List<String> errors = new ArrayList<>();
        if (pool == null) {
            errors.add("pool_required");
            return errors;
        }
        if (pool.getId() == null || !pool.getId().matches("[a-z0-9_.-]{1,64}")) {
            errors.add("invalid_id");
        }
        if (pool.getDisplayName() == null || pool.getDisplayName().trim().isEmpty()
                || pool.getDisplayName().length() > 96) {
            errors.add("invalid_display_name");
        }
        if (pool.getLimitPolicy() == null) {
            errors.add("limit_policy_required");
        }
        if (pool.getMaxDraws() < 1 || pool.getMaxDraws() > 10_000) {
            errors.add("invalid_max_draws");
        }
        if (pool.getPreviewMode() == null) {
            errors.add("preview_mode_required");
        }
        if (pool.getCooldownSeconds() < 0 || pool.getCooldownSeconds() > 31_536_000) {
            errors.add("invalid_cooldown");
        }
        if (pool.getRewards() == null || pool.getRewards().size() > 256) {
            errors.add("invalid_rewards");
        }
        return errors;
    }
}
