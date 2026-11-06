package xin.vanilla.sakura.data.lottery;

import java.util.ArrayList;
import java.util.List;

/**
 * 奖池配置的公共边界校验。
 */
public final class LotteryPoolValidator {
    public static final int MAX_DESCRIPTION = 2048;
    public static final int MAX_VOUCHER_EXPRESSION = 4096;

    public static final int MAX_BATCH_SIZE = 100;
    public static final int MAX_ARTWORK_ID = 128;

    public static boolean isArtworkItem(String id) {
        return id != null && id.length() <= MAX_ARTWORK_ID
                && (id.isEmpty() || id.matches("[a-z0-9_.-]+:[a-z0-9_/-]+"));
    }

    public static boolean isArtworkTexture(String id) {
        return id != null && id.length() <= MAX_ARTWORK_ID && (id.isEmpty()
                || id.matches("[a-z0-9_.-]+:textures/lottery/(?:[a-z0-9_-]+/)*[a-z0-9_-]+[.]png"));
    }

    private LotteryPoolValidator() {
    }

    public static boolean isFree(LotteryPool pool) {
        if (pool == null || pool.getVoucherExpression() == null) return false;
        try {
            return xin.vanilla.banira.api.BaniraCosts.parse(pool.getVoucherExpression()).kind()
                    == xin.vanilla.banira.api.cost.CostRequirement.Kind.FREE;
        } catch (IllegalArgumentException failure) {
            return false;
        }
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
        if (pool.getDescription() == null || pool.getDescription().length() > MAX_DESCRIPTION) {
            errors.add("invalid_description");
        }
        if (pool.getVoucherExpression() == null
                || pool.getVoucherExpression().length() > MAX_VOUCHER_EXPRESSION) {
            errors.add("invalid_voucher_expression");
        } else {
            try {
                xin.vanilla.banira.api.BaniraCosts.parse(pool.getVoucherExpression());
            } catch (IllegalArgumentException failure) {
                errors.add("invalid_voucher_expression");
            }
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
        int batch = pool.getBatchSize();
        if (batch < 0 || batch == 1 || batch > MAX_BATCH_SIZE || !pool.isSingleDraw() && batch == 0
                || batch > pool.getMaxDraws() && pool.getLimitPolicy() != LotteryLimitPolicy.UNLIMITED
                && pool.getLimitPolicy() != LotteryLimitPolicy.COOLDOWN) {
            errors.add("invalid_draw_modes");
        }
        if (pool.getAnimationStyle() == null) errors.add("animation_required");
        if (!isArtworkItem(pool.getArtworkItem()) || !isArtworkTexture(pool.getArtworkTexture()))
            errors.add("invalid_artwork");
        if (pool.getRewards() == null || pool.getRewards().size() > 256) {
            errors.add("invalid_rewards");
        }
        return errors;
    }
}
