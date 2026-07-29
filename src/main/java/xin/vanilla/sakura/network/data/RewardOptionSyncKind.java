package xin.vanilla.sakura.network.data;

/**
 * 区分真实奖励、空组占位和无权限规则占位。
 */
public enum RewardOptionSyncKind {
    REWARD,
    EMPTY_GROUP,
    REDACTED_RULE;

    public static RewardOptionSyncKind valueOf(int ordinal) {
        RewardOptionSyncKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : REWARD;
    }
}
