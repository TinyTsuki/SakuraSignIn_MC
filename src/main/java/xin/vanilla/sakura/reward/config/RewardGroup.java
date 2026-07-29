package xin.vanilla.sakura.reward.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.rewards.RewardList;

/**
 * 一个有序奖励规则组。随机奖励允许多个组使用相同概率。
 */
@Data
@NoArgsConstructor
public class RewardGroup {
    private ERewardRule rule;
    private String key = "";
    private RewardList rewards = new RewardList();
    private String expirationDate = "";
    private int redemptionLimit = 1;

    public RewardGroup(ERewardRule rule, String key, RewardList rewards) {
        this.rule = rule;
        this.key = key == null ? "" : key;
        this.rewards = rewards == null ? new RewardList() : rewards;
    }

    public static RewardGroup cdk(String key, String expirationDate, int redemptionLimit, RewardList rewards) {
        RewardGroup group = new RewardGroup(ERewardRule.CDK_REWARD, key, rewards);
        group.setExpirationDate(expirationDate == null ? "" : expirationDate);
        group.setRedemptionLimit(redemptionLimit);
        return group;
    }
}
