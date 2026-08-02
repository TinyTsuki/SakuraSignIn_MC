package xin.vanilla.sakura.reward;

import xin.vanilla.banira.api.permission.BaniraPermissions;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardRulePermissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 服务端只对奖励规则组数量的净增加检查新增权限。 */
public final class RewardRuleAddPermissionChecker {
    private RewardRuleAddPermissionChecker() {
    }

    public static List<ERewardRule> requiredAddedRules(RewardConfig authoritative,
                                                        RewardConfig candidate) {
        List<ERewardRule> result = new ArrayList<>();
        for (ERewardRule rule : ERewardRule.values()) {
            int before = groupCount(authoritative, rule);
            int after = groupCount(candidate, rule);
            if (after > before) {
                result.add(rule);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean canApply(Object player, RewardConfig authoritative,
                                   RewardConfig candidate) {
        for (ERewardRule rule : requiredAddedRules(authoritative, candidate)) {
            xin.vanilla.sakura.api.reward.RewardAddPermission permission =
                    BuiltInRewardRulePermissions.permission(rule);
            if (!BaniraPermissions.has(player, permission.getPermissionLevel(),
                    permission.getVirtualPermissionKey())) {
                return false;
            }
        }
        return true;
    }

    private static int groupCount(RewardConfig config, ERewardRule rule) {
        if (config == null) {
            return 0;
        }
        if (rule == ERewardRule.PERSONAL_DATE_REWARD) {
            return config.getPersonalDatePresets().size();
        }
        if (rule == ERewardRule.LOTTERY_REWARD) {
            return config.getLotteryPools().size();
        }
        return RewardConfigManager.getRewardMap(config, rule).size();
    }
}
