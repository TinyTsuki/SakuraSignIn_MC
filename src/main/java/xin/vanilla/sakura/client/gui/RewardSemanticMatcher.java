package xin.vanilla.sakura.client.gui;

import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardOperations;

/**
 * 奖励选择只比较注册类型定义的语义身份，不比较数量和概率。
 */
public final class RewardSemanticMatcher {
    private RewardSemanticMatcher() {
    }

    public static boolean matches(Reward first, Reward second) {
        return RewardOperations.semanticallyMatches(first, second);
    }
}
