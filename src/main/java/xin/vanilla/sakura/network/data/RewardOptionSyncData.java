package xin.vanilla.sakura.network.data;

import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.rewards.Reward;

/**
 * @param rule   签到奖励规则
 * @param key    签到奖励规则参数
 * @param reward 签到奖励数据
 */
public record RewardOptionSyncData(ERewardRule rule, String key, Reward reward) {
}
