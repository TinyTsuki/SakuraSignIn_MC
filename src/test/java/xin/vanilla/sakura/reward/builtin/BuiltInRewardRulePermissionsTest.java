package xin.vanilla.sakura.reward.builtin;

import org.junit.Test;
import xin.vanilla.sakura.enums.ERewardRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuiltInRewardRulePermissionsTest {
    @Test
    public void everyBuiltInRuleHasStableAddPermission() {
        for (ERewardRule rule : ERewardRule.values()) {
            String key = BuiltInRewardRulePermissions.permission(rule)
                    .getVirtualPermissionKey();
            assertTrue(key.startsWith("sakura_sign_in:reward.rule.add."));
        }
        assertEquals(3, BuiltInRewardRulePermissions.permission(
                ERewardRule.CDK_REWARD).getPermissionLevel());
    }
}
