package xin.vanilla.sakura.data.lottery;

import lombok.Data;
import lombok.NoArgsConstructor;
import xin.vanilla.sakura.reward.RewardList;

/** 服务端权威抽奖池，奖励概率在此处作为相对权重。 */
@Data
@NoArgsConstructor
public class LotteryPool {
    private String id = "";
    private String displayName = "";
    private LotteryLimitPolicy limitPolicy = LotteryLimitPolicy.DAILY;
    private int maxDraws = 1;
    private int cooldownSeconds;
    private boolean showRewards = true;
    private RewardList rewards = new RewardList();

    public LotteryPool(String id, String displayName, LotteryLimitPolicy limitPolicy,
                       int maxDraws, int cooldownSeconds, RewardList rewards) {
        this.id = id == null ? "" : id;
        this.displayName = displayName == null ? "" : displayName;
        this.limitPolicy = limitPolicy == null ? LotteryLimitPolicy.DAILY : limitPolicy;
        this.maxDraws = maxDraws;
        this.cooldownSeconds = cooldownSeconds;
        this.rewards = rewards == null ? new RewardList() : rewards;
    }

    public LotteryPool(String id, String displayName, LotteryLimitPolicy limitPolicy,
                       int maxDraws, int cooldownSeconds, boolean showRewards,
                       RewardList rewards) {
        this(id, displayName, limitPolicy, maxDraws, cooldownSeconds, rewards);
        this.showRewards = showRewards;
    }
}
