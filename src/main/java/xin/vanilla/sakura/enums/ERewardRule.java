package xin.vanilla.sakura.enums;

import lombok.Getter;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

import java.util.Arrays;

/**
 * 奖励规则
 */
@Getter
public enum ERewardRule implements IEnumDescribable {
    BASE_REWARD(1),
    CONTINUOUS_REWARD(2),
    CYCLE_REWARD(3),
    YEAR_REWARD(4),
    MONTH_REWARD(5),
    WEEK_REWARD(6),
    DATE_TIME_REWARD(7),
    CUMULATIVE_REWARD(8),
    RANDOM_REWARD(9),
    CDK_REWARD(10),
    PERSONAL_DATE_REWARD(11);

    private final int code;

    ERewardRule(int code) {
        this.code = code;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }

    public static ERewardRule valueOf(int code) {
        return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
    }
}
