package xin.vanilla.sakura.network.data;

import lombok.Data;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.enums.EnumRewardRule;

@Data
public class RewardOptionSyncData {
    /**
     * 签到奖励规则
     */
    private final EnumRewardRule rule;
    /**
     * 签到奖励规则参数
     */
    private final String key;
    /**
     * 签到奖励数据
     */
    private final Reward reward;
}
