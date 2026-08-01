package xin.vanilla.sakura.client.gui;

import org.junit.Test;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * 验证多组编辑目标保持选择顺序并去重。
 */
public class RewardEditTargetsTest {
    @Test
    public void selectedGroupKeysIgnoreRewardsAndRemoveDuplicates() {
        assertEquals(Arrays.asList("1", "8"), RewardSelectionIds.groupKeys(Arrays.asList(
                "标题,1", "1,0", "标题,8", "标题,1", "8,2"), "标题,"));
    }

    @Test
    public void rewardIdsIncludeCollapsedGroupsFromTheFullRuleMap() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("1", Arrays.asList("a", "b"));
        groups.put("8", Arrays.asList("c"));

        assertEquals(Arrays.asList("1,0", "1,1", "8,0"),
                RewardSelectionIds.rewardIds(groups));
        assertEquals(Arrays.asList("标题,1", "1,0", "1,1", "标题,8", "8,0"),
                RewardSelectionIds.selectionIds(groups, "标题,"));
    }
}
