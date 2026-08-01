package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 验证奖励列表符合桌面界面常见的 Ctrl 与 Shift 多选语义。
 */
public class RewardSelectionModelTest {
    private static final java.util.List<String> IDS =
            Arrays.asList("base,0", "base,1", "base,2", "base,3");

    @Test
    public void ctrlTogglesIndividualEntries() {
        RewardSelectionModel model = new RewardSelectionModel();
        model.select("base,0", IDS, false, false);
        model.select("base,2", IDS, true, false);

        assertTrue(model.isSelected("base,0"));
        assertTrue(model.isSelected("base,2"));

        model.select("base,0", IDS, true, false);
        assertFalse(model.isSelected("base,0"));
        assertEquals("base,2", model.primary());
    }

    @Test
    public void shiftSelectsAnInclusiveRangeAndCtrlShiftAddsIt() {
        RewardSelectionModel model = new RewardSelectionModel();
        model.select("base,1", IDS, false, false);
        model.select("base,3", IDS, false, true);
        assertEquals(Arrays.asList("base,1", "base,2", "base,3"), model.selectedIds());

        model.select("base,0", IDS, true, false);
        model.select("base,2", IDS, true, true);
        assertEquals(Arrays.asList("base,1", "base,2", "base,3", "base,0"),
                model.selectedIds());
    }

    @Test
    public void middleClickCyclesRewardGroupAndClearWithoutTouchingOtherGroups() {
        RewardSelectionModel model = new RewardSelectionModel();
        model.selectOnly("标题,other");

        model.cycleMiddleReward("base,1", "标题,base", IDS);
        assertEquals(Arrays.asList("标题,other", "base,1"), model.selectedIds());

        model.cycleMiddleReward("base,1", "标题,base", IDS);
        assertEquals(Arrays.asList("标题,other", "标题,base"), model.selectedIds());

        model.cycleMiddleReward("base,1", "标题,base", IDS);
        assertEquals(Arrays.asList("标题,other"), model.selectedIds());
    }

    @Test
    public void middleClickGroupTogglesItAndReplacesMembersFromThatGroup() {
        RewardSelectionModel model = new RewardSelectionModel();
        model.selectOnly("base,2");

        model.toggleGroup("标题,base", IDS);
        assertEquals(Arrays.asList("标题,base"), model.selectedIds());

        model.toggleGroup("标题,base", IDS);
        assertTrue(model.selectedIds().isEmpty());
    }

    @Test
    public void tripleClickReplacesSelectionWithEveryRewardInTheGroup() {
        RewardSelectionModel model = new RewardSelectionModel();
        model.selectOnly("标题,other");

        model.selectOnly(IDS);

        assertEquals(IDS, model.selectedIds());
        assertEquals("base,3", model.primary());
    }
}
