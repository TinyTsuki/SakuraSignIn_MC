package xin.vanilla.sakura.network.data;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.sakura.SakuraComponent;

/**
 * 区分真实奖励、空组占位和无权限规则占位。
 */
public enum RewardOptionSyncKind implements IEnumDescribable {
    REWARD,
    EMPTY_GROUP,
    REDACTED_RULE;

    @Override
    public Component enumDescription() {
        return SakuraComponent.get().literal(name());
    }

    public static RewardOptionSyncKind valueOf(int ordinal) {
        RewardOptionSyncKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : REWARD;
    }
}
