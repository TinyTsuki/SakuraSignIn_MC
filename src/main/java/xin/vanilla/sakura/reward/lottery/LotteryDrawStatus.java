package xin.vanilla.sakura.reward.lottery;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

public enum LotteryDrawStatus implements IEnumDescribable {
    SUCCESS,
    POOL_NOT_FOUND,
    EMPTY_POOL,
    LIMIT_REACHED,
    GRANT_FAILED;

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }
}
