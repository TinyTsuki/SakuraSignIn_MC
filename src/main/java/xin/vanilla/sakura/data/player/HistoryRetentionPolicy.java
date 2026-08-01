package xin.vanilla.sakura.data.player;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

/**
 * 过期月度签到详情的处理方式。
 */
public enum HistoryRetentionPolicy implements IEnumDescribable {
    STRIP_REWARD_DETAILS,
    DELETE_MONTH_FILE;

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
    }
}
