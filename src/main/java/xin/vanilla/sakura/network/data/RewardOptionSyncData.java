package xin.vanilla.sakura.network.data;

import lombok.Data;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.Reward;

@Data
public class RewardOptionSyncData {
    private final RewardOptionSyncKind kind;
    /**
     * 签到奖励规则
     */
    private final ERewardRule rule;
    /**
     * 签到奖励规则参数
     */
    private final String key;
    /**
     * 签到奖励数据
     */
    private final Reward reward;

    public RewardOptionSyncData(ERewardRule rule, String key, Reward reward) {
        this(RewardOptionSyncKind.REWARD, rule, key, reward);
    }

    public RewardOptionSyncData(RewardOptionSyncKind kind, ERewardRule rule,
                                String key, Reward reward) {
        this.kind = kind;
        this.rule = rule;
        this.key = key;
        this.reward = reward;
    }

    public static RewardOptionSyncData emptyGroup(ERewardRule rule, String key) {
        return new RewardOptionSyncData(RewardOptionSyncKind.EMPTY_GROUP, rule, key, null);
    }

    public static RewardOptionSyncData redactedRule(ERewardRule rule) {
        return new RewardOptionSyncData(RewardOptionSyncKind.REDACTED_RULE, rule, "", null);
    }
}
