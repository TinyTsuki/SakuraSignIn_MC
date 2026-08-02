package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 验证键盘选择按奖励的实际二维位置移动。
 */
public class RewardKeyboardNavigatorTest {
    private static final List<RewardKeyboardNavigator.Point> POINTS = Arrays.asList(
            new RewardKeyboardNavigator.Point("daily,0", 10, 10),
            new RewardKeyboardNavigator.Point("daily,1", 30, 10),
            new RewardKeyboardNavigator.Point("daily,2", 10, 30),
            new RewardKeyboardNavigator.Point("weekly,0", 32, 34),
            new RewardKeyboardNavigator.Point("weekly,1", 52, 34)
    );

    @Test
    public void horizontalNavigationUsesVisualNeighbours() {
        List<String> ordered = Arrays.asList("group1,0", "group1,13",
                "group2,0", "group2,6", "group2,7");
        assertEquals("group2,0", RewardKeyboardNavigator.findAdjacent(
                "group1,13", ordered, RewardKeyboardNavigator.Direction.RIGHT));
        assertEquals("group2,6", RewardKeyboardNavigator.findAdjacent(
                "group2,7", ordered, RewardKeyboardNavigator.Direction.LEFT));
    }

    @Test
    public void verticalNavigationUsesNearestCandidateAndCanCrossGroups() {
        assertEquals("weekly,0", RewardKeyboardNavigator.findNext(
                "daily,1", POINTS, RewardKeyboardNavigator.Direction.DOWN));
        assertEquals("daily,1", RewardKeyboardNavigator.findNext(
                "weekly,0", POINTS, RewardKeyboardNavigator.Direction.UP));
    }

    @Test
    public void missingCurrentOrDirectionalCandidateDoesNotMove() {
        assertNull(RewardKeyboardNavigator.findNext(
                "missing", POINTS, RewardKeyboardNavigator.Direction.RIGHT));
        assertNull(RewardKeyboardNavigator.findAdjacent("daily,0",
                Arrays.asList("daily,0", "daily,1"),
                RewardKeyboardNavigator.Direction.LEFT));
    }
}
