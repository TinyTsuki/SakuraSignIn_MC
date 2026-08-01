package xin.vanilla.sakura.screen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RewardRuleListViewportTest {
    @Test
    public void shortListRemainsAtOrigin() {
        assertEquals(0.0, RewardRuleListViewport.clampOffset(-40, 80, 120), 0.001);
        assertEquals(0.0, RewardRuleListViewport.clampOffset(40, 80, 120), 0.001);
    }

    @Test
    public void longListClampsBetweenTopAndLastRow() {
        assertEquals(0.0, RewardRuleListViewport.clampOffset(20, 240, 100), 0.001);
        assertEquals(-60.0, RewardRuleListViewport.clampOffset(-60, 240, 100), 0.001);
        assertEquals(-140.0, RewardRuleListViewport.clampOffset(-500, 240, 100), 0.001);
    }
}
