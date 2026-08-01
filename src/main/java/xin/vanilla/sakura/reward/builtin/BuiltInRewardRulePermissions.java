package xin.vanilla.sakura.reward.builtin;

import xin.vanilla.banira.api.permission.BaniraVirtualPermission;
import xin.vanilla.banira.api.permission.BaniraVirtualPermissionRegistry;
import xin.vanilla.sakura.api.reward.RewardAddPermission;
import xin.vanilla.sakura.enums.ERewardRule;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** 内置奖励规则新增权限，查看权限仍由服务端配置控制。 */
public final class BuiltInRewardRulePermissions {
    private static final Map<ERewardRule, RewardAddPermission> PERMISSIONS =
            new EnumMap<>(ERewardRule.class);

    static {
        for (ERewardRule rule : ERewardRule.values()) {
            int level = rule == ERewardRule.CDK_REWARD ? 3 : 0;
            String path = rule.name().toLowerCase(Locale.ROOT)
                    .replace("_reward", "");
            PERMISSIONS.put(rule, RewardAddPermission.of(level,
                    "sakura_sign_in:reward.rule.add." + path));
        }
    }

    private BuiltInRewardRulePermissions() {
    }

    public static RewardAddPermission permission(ERewardRule rule) {
        RewardAddPermission permission = PERMISSIONS.get(rule);
        if (permission == null) {
            throw new IllegalArgumentException("Unknown reward rule: " + rule);
        }
        return permission;
    }

    public static void registerVirtualPermissions() {
        PERMISSIONS.values().forEach(permission -> {
            String key = permission.getVirtualPermissionKey();
            if (!BaniraVirtualPermissionRegistry.find(key).isPresent()) {
                int separator = key.indexOf(':');
                BaniraVirtualPermissionRegistry.register(new RulePermission(
                        key.substring(0, separator), key.substring(separator + 1)));
            }
        });
    }

    private static final class RulePermission implements BaniraVirtualPermission {
        private final String modId;
        private final String id;

        private RulePermission(String modId, String id) {
            this.modId = modId;
            this.id = id;
        }

        @Override
        public String modId() {
            return modId;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean op() {
            return true;
        }

        @Override
        public int sort() {
            return Integer.MAX_VALUE;
        }
    }
}
