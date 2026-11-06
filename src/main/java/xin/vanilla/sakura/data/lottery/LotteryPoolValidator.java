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

    public static final int MAX_DRAW_OPTIONS = 12;

    public static List<Integer> parseDrawCounts(String text) {
        if (text == null || text.length() > 64) throw new IllegalArgumentException("Invalid draw counts");
        List<Integer> counts = new ArrayList<>();
        for (String part : text.split(",", -1)) {
            String value = part.trim();
            if (!value.matches("[1-9][0-9]{0,2}")) throw new IllegalArgumentException("Invalid draw count");
            counts.add(Integer.parseInt(value));
        }
        validateCounts(counts);
        return List.copyOf(counts);
    }

    public static void validateCounts(List<Integer> counts) {
        if (counts.isEmpty() || counts.size() > MAX_DRAW_OPTIONS
                || counts.stream().anyMatch(count -> count < 1 || count > MAX_BATCH_SIZE)
                || new java.util.HashSet<>(counts).size() != counts.size())
            throw new IllegalArgumentException("Invalid draw counts");
    }

    public static boolean validDate(String value) {
        if (value == null) return false;
        if (value.isEmpty()) return true;
        try {
            return value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}( [0-9]{2}:[0-9]{2})?")
                    && LotteryWindow.boundary(value, false).getYear() > 0;
        } catch (java.time.DateTimeException ignored) {
            return false;
        }
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
        try {
            validateCounts(pool.getDrawCounts());
            if (pool.getLimitPolicy() != LotteryLimitPolicy.UNLIMITED && pool.getLimitPolicy() != LotteryLimitPolicy.COOLDOWN
                    && pool.getDrawCounts().stream().anyMatch(count -> count > pool.getMaxDraws()))
                errors.add("invalid_draw_modes");
        } catch (IllegalArgumentException failure) {
            errors.add("invalid_draw_modes");
        }
        if (!validDate(pool.getStartDate()) || !validDate(pool.getEndDate())
                || !pool.getStartDate().isEmpty() && !pool.getEndDate().isEmpty()
                && !LotteryWindow.boundary(pool.getStartDate(), false).isBefore(LotteryWindow.boundary(pool.getEndDate(), true))) errors.add("invalid_dates");
        try {
            LotteryPng.inspect(pool.getThumbnailPng());
            LotteryPng.inspect(pool.getPosterPng());
        } catch (IllegalArgumentException failure) {
            errors.add("invalid_artwork");
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
