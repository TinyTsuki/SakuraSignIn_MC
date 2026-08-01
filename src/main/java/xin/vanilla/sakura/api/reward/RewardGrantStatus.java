package xin.vanilla.sakura.api.reward;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

public enum RewardGrantStatus implements IEnumDescribable {
    SUCCESS,
    PROBABILITY_MISSED,
    TYPE_UNAVAILABLE,
    INVALID_CONTENT,
    REJECTED,
    FAILED;

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }
}
