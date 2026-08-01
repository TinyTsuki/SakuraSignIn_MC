package xin.vanilla.sakura.api.reward;

import lombok.Value;

import java.util.Objects;

/**
 * 添加指定奖励类型时需要满足的权限策略。
 */
@Value(staticConstructor = "ofUnchecked")
public class RewardAddPermission {
    int permissionLevel;
    String virtualPermissionKey;

    public static RewardAddPermission of(int permissionLevel, String virtualPermissionKey) {
        if (permissionLevel < 0 || permissionLevel > 4) {
            throw new IllegalArgumentException("Permission level must be between 0 and 4: " + permissionLevel);
        }
        String key = Objects.requireNonNull(virtualPermissionKey, "virtualPermissionKey").trim();
        int separator = key.indexOf(':');
        if (separator <= 0 || separator != key.lastIndexOf(':') || separator == key.length() - 1) {
            throw new IllegalArgumentException("Virtual permission key must use modid:id: " + key);
        }
        return ofUnchecked(permissionLevel, key);
    }
}
