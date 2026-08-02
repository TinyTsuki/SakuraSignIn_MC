package xin.vanilla.sakura.data.lottery;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

/** 控制普通玩家能看到的奖池身份与概率信息。 */
public enum LotteryPreviewMode implements IEnumDescribable {
    ALL,
    ITEMS_ONLY,
    PROBABILITY_ONLY,
    NONE;

    public boolean showsItems() {
        return this == ALL || this == ITEMS_ONLY;
    }

    public boolean showsProbabilities() {
        return this == ALL || this == PROBABILITY_ONLY;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }
}
