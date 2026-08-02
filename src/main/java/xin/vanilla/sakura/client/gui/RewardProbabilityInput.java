package xin.vanilla.sakura.client.gui;

import xin.vanilla.banira.common.util.NumberUtils;

import java.math.BigDecimal;

/** 在界面百分比与配置内部 0~1 概率之间进行唯一的一处转换。 */
public final class RewardProbabilityInput {
    public static final String PERCENT_REGEX = "(100(?:\\.0{0,5})?|(?:\\d{1,2})(?:\\.\\d{0,5})?)?";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private RewardProbabilityInput() {
    }

    public static String display(BigDecimal probability) {
        BigDecimal safe = probability == null ? BigDecimal.ZERO : probability;
        return NumberUtils.toFixedEx(safe.multiply(HUNDRED), 5);
    }

    public static BigDecimal parse(String percent) {
        return NumberUtils.toBigDecimal(percent).divide(HUNDRED);
    }

    public static boolean isValidPercent(String percent) {
        if (percent == null || percent.trim().isEmpty()) {
            return false;
        }
        BigDecimal value = NumberUtils.toBigDecimal(percent);
        return value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(HUNDRED) <= 0;
    }
}
