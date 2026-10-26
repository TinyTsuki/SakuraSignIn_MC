package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;
import static org.junit.Assert.*;

public class LotteryRevealLayoutTest {
    @Test public void nativeUsesTheSameResultDensityAsAui() {
        for (int[] size : new int[][]{{260,180},{320,240},{427,240},{640,450}}) {
            LotteryRevealLayout layout = LotteryRevealLayout.calculate(size[0],size[1]);
            assertTrue(layout.columns() <= 3);
            assertEquals(layout.content().width() < 240 ? 1 : layout.content().width() < 360 ? 2 : 3, layout.columns());
            assertTrue(layout.visibleRows() * 60 <= layout.content().height());
        }
    }
    @Test public void nativeExposesFullResultCardsAndHeaderControls() {
        var methods = java.util.Arrays.stream(LotteryRevealLayout.class.getMethods()).map(java.lang.reflect.Method::getName).toList();
        for (String method : new String[]{"brand","subtitle","close","section","count","single"})
            assertTrue(method, methods.contains(method));
        assertTrue(methods.contains("card"));
    }
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
    @Test public void fullCardsAndSingleResultFitInsideViewport() {
        for(int[] size:new int[][]{{260,180},{320,240},{427,240},{640,450}}){
            var layout=LotteryRevealLayout.calculate(size[0],size[1]);
            assertTrue(layout.content().encloses(layout.single()));
            assertFalse(layout.title().overlaps(layout.close()));
            assertFalse(layout.brand().overlaps(layout.title()));
            for(int i=0;i<layout.columns()*layout.visibleRows();i++) assertTrue(layout.content().encloses(layout.card(i)));
            if(layout.columns()>1) assertFalse(layout.card(0).overlaps(layout.card(1)));
        }
    }
    @Test public void bothBackendsShareColumnThresholdsAndRowHeight() {
        assertEquals(60,LotteryRevealLayout.ROW_HEIGHT);
        for(int width:new int[]{110,239,240,359,360,540}){
            assertEquals(1,LotteryRevealLayout.resultColumns(width,1));
            assertEquals(width<240?1:width<360?2:3,LotteryRevealLayout.resultColumns(width,100));
        }
    }
}
