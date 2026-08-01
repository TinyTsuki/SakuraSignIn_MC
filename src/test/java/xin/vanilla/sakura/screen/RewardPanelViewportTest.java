package xin.vanilla.sakura.screen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 锁定奖励面板可自由调整 Y 轴偏移的边界。
 */
public class RewardPanelViewportTest {
    @Test
    public void shortContentCanStillMoveInBothDirections() {
        assertEquals(120.0,
                RewardPanelViewport.clampOffset(120.0, 80.0, 240.0), 0.001);
        assertEquals(-120.0,
                RewardPanelViewport.clampOffset(-120.0, 80.0, 240.0), 0.001);
    }

    @Test
    public void offsetKeepsTheOriginalOneViewportOverscrollRange() {
        assertEquals(240.0,
                RewardPanelViewport.clampOffset(400.0, 80.0, 240.0), 0.001);
        assertEquals(-320.0,
                RewardPanelViewport.clampOffset(-400.0, 80.0, 240.0), 0.001);
    }
}
