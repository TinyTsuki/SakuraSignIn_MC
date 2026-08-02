package xin.vanilla.sakura.data.lottery;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

/** 奖池可按不同时间尺度限制领取次数。 */
public enum LotteryLimitPolicy implements IEnumDescribable {
    UNLIMITED,
    DAILY,
    WEEKLY,
    MONTHLY,
    COOLDOWN,
    LIFETIME;

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }
}
