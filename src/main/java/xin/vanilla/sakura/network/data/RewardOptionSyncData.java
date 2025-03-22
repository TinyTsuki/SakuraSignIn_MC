package xin.vanilla.sakura.network.data;

import lombok.Data;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.rewards.Reward;

@Data
public class RewardOptionSyncData {
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
}
