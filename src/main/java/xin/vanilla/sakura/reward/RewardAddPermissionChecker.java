package xin.vanilla.sakura.reward;

import xin.vanilla.banira.api.permission.BaniraPermissions;
import xin.vanilla.sakura.api.reward.RewardAddPermission;
import xin.vanilla.sakura.api.reward.RewardTypeDefinition;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewards;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.enums.ERewardRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端只对每种奖励数量的净增加检查新增权限。
 */
public final class RewardAddPermissionChecker {
    private RewardAddPermissionChecker() {
    }

    public static List<RewardTypeId> requiredAddedTypes(RewardConfig authoritative,
                                                         RewardConfig candidate) {
        Map<RewardTypeId, Integer> before = count(authoritative);
        Map<RewardTypeId, Integer> after = count(candidate);
        List<RewardTypeId> result = new ArrayList<>();
        for (Map.Entry<RewardTypeId, Integer> entry : after.entrySet()) {
            if (entry.getValue() > before.getOrDefault(entry.getKey(), 0)) {
                result.add(entry.getKey());
            }
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public static boolean canApply(Object player, RewardConfig authoritative,
                                   RewardConfig candidate) {
        for (RewardTypeId typeId : requiredAddedTypes(authoritative, candidate)) {
            RewardTypeDefinition<?> definition = SakuraRewards.find(typeId).orElse(null);
            if (definition == null) {
                return false;
            }
            RewardAddPermission permission = definition.getAddPermission();
            if (!BaniraPermissions.has(player, permission.getPermissionLevel(),
                    permission.getVirtualPermissionKey())) {
                return false;
            }
        }
        return true;
    }

    private static Map<RewardTypeId, Integer> count(RewardConfig config) {
        Map<RewardTypeId, Integer> result = new HashMap<>();
        if (config == null) {
            return result;
        }
        for (ERewardRule rule : ERewardRule.values()) {
            RewardConfigManager.getRewardMap(config, rule).values().forEach(rewards ->
                    rewards.forEach(reward -> {
                        if (reward != null && reward.getTypeId() != null) {
                            result.merge(reward.getTypeId(), 1, Integer::sum);
                        }
                    }));
        }
        return result;
    }
}
