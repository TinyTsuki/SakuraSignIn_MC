package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;
import static org.junit.Assert.*;

public class LotteryRevealLayoutTest {
    @Test public void resultGridAndActionRemainInsideSmallAndLargeScreens() {
        for (int[] size : new int[][]{{320,240},{427,240},{854,480},{1280,720}}) {
            LotteryRevealLayout layout = LotteryRevealLayout.calculate(size[0],size[1]);
            assertTrue(layout.panel().x() >= 0); assertTrue(layout.panel().y() >= 0);
            assertTrue(layout.panel().right() <= size[0]); assertTrue(layout.panel().bottom() <= size[1]);
            for (LotteryLayout.Rect rect : new LotteryLayout.Rect[]{layout.title(),layout.content(),layout.label(),layout.action()}) {
                assertTrue(layout.panel().encloses(rect)); assertTrue(rect.height() > 0);
            }
            assertFalse(layout.content().overlaps(layout.label())); assertFalse(layout.label().overlaps(layout.action()));
            assertFalse(layout.content().overlaps(layout.action()));
            assertTrue(layout.visibleRows() * 22 <= layout.content().height());
            assertTrue(layout.columns() * 22 <= layout.content().width());
        }
    }
    @Test public void hundredResultsCanScrollToLastRowWithoutMovingControls() {
        LotteryRevealLayout layout = LotteryRevealLayout.calculate(320,240);
        assertTrue(layout.maxScrollRows(100) > 0);
        assertEquals(0,layout.maxScrollRows(1));
        assertTrue((layout.maxScrollRows(100)+layout.visibleRows())*layout.columns() >= 100);
    }
}
