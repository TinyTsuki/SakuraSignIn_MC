package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LotteryLayoutTest {
    @Test public void informationDescriptionPreviewAndControlsAreDisjointAtAllSupportedSizes() {
        for (int[] size : new int[][]{{320,240},{427,240},{854,480},{1280,720}}) {
            LotteryLayout layout = LotteryLayout.calculate(size[0], size[1]);
            List<LotteryLayout.Rect> areas = Arrays.asList(layout.title(), layout.pool(), layout.info(),
                    layout.description(), layout.preview(), layout.single(), layout.count(), layout.batch(), layout.animation(), layout.close());
            for (LotteryLayout.Rect area : areas) {
                assertTrue(area.width() > 0 && area.height() > 0);
                assertTrue(layout.panel().encloses(area));
            }
            for (int i = 0; i < areas.size(); i++) for (int j = i + 1; j < areas.size(); j++)
                assertFalse("Overlapping regions at " + Arrays.toString(size), areas.get(i).overlaps(areas.get(j)));
            assertTrue(layout.preview().height() >= 26);
            assertTrue(layout.single().width() >= 70); assertTrue(layout.batch().width() >= 70);
            assertTrue(layout.animation().width() >= 120);
        }
    }
    @Test public void narrowFooterMovesAnimationToItsOwnRow() {
        LotteryLayout small = LotteryLayout.calculate(320,240);
        assertTrue(small.animation().y() >= small.single().bottom() + 4);
        LotteryLayout large = LotteryLayout.calculate(854,480);
        assertEquals(large.single().y(), large.animation().y());
    }
    @Test public void viewportHitTestsExcludeRightBottomAndNeighboringTools() {
        LotteryLayout layout = LotteryLayout.calculate(320,240);
        LotteryLayout.Rect view = layout.description();
        assertTrue(view.contains(view.x(), view.y()));
        assertFalse(view.contains(view.right(), view.y())); assertFalse(view.contains(view.x(), view.bottom()));
        assertFalse(view.contains(layout.single().x(),layout.single().y()));
    }
}
