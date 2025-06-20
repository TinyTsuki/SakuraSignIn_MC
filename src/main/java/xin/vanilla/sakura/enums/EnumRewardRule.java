package xin.vanilla.sakura.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 奖励规则
 */
@Getter
public enum EnumRewardRule {
    BASE_REWARD(1),
    CONTINUOUS_REWARD(2),
    CYCLE_REWARD(3),
    YEAR_REWARD(4),
    MONTH_REWARD(5),
    WEEK_REWARD(6),
    DATE_TIME_REWARD(7),
    CUMULATIVE_REWARD(8),
    RANDOM_REWARD(9),
    CDK_REWARD(10);

    private final int code;

    EnumRewardRule(int code) {
        this.code = code;
    }

    public static EnumRewardRule valueOf(int code) {
        return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
    }
}
