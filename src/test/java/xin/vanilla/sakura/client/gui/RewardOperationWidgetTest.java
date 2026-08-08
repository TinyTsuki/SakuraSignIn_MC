package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardOperationWidgetTest {
    @Test
    public void tinyPointerJitterRemainsAClick() {
        assertFalse(RewardOperationWidget.isDragActivated(1, 1));
        assertFalse(RewardOperationWidget.isDragActivated(2, 2));
        assertTrue(RewardOperationWidget.isDragActivated(4, 0));
    }
}
