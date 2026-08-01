package xin.vanilla.sakura.network;

import org.junit.Test;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.reward.RewardRuleAddPermissionChecker;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RewardRuleAddPermissionEnforcementTest {
    @Test
    public void requiresOnlyRulesWithPositiveGroupDeltas() {
        RewardConfig before = new RewardConfig();
        before.getContinuousRewards().put("1", new RewardList());
        RewardConfig after = new RewardConfig();
        after.getContinuousRewards().put("1", new RewardList());
        after.getContinuousRewards().put("2", new RewardList());
        after.getMonthRewards().put("8", new RewardList());

        assertEquals(Arrays.asList(ERewardRule.CONTINUOUS_REWARD,
                        ERewardRule.MONTH_REWARD),
                RewardRuleAddPermissionChecker.requiredAddedRules(before, after));
        assertTrue(RewardRuleAddPermissionChecker.requiredAddedRules(after, before).isEmpty());
    }

    @Test
    public void editingRewardsInsideExistingGroupDoesNotRequireRulePermission() {
        RewardConfig before = new RewardConfig();
        before.getWeekRewards().put("1", new RewardList());
        RewardConfig after = new RewardConfig();
        RewardList changed = new RewardList();
        changed.add(null);
        after.getWeekRewards().put("1", changed);

        assertEquals(Collections.emptyList(),
                RewardRuleAddPermissionChecker.requiredAddedRules(before, after));
    }

    @Test
    public void movingGroupToAnotherRuleRequiresOnlyTargetRule() {
        RewardConfig before = new RewardConfig();
        before.getContinuousRewards().put("1", new RewardList());
        RewardConfig after = new RewardConfig();
        after.getCycleRewards().put("1", new RewardList());

        assertEquals(Collections.singletonList(ERewardRule.CYCLE_REWARD),
                RewardRuleAddPermissionChecker.requiredAddedRules(before, after));
    }
}
