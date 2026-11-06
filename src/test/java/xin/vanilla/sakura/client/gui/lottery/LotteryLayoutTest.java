package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class LotteryLayoutTest {
    @Test
    public void informationDescriptionPreviewAndControlsAreDisjointAtAllSupportedSizes() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {854, 480}, {1280, 720}}) {
            LotteryLayout layout = LotteryLayout.calculate(size[0], size[1]);
            List<LotteryLayout.Rect> areas = Arrays.asList(layout.title(), layout.pool(), layout.info(),
                    layout.description(), layout.preview(), layout.heading(), layout.poster(), layout.single(), layout.batch(), layout.close());
            for (LotteryLayout.Rect area : areas) {
                assertTrue(area.width() > 0 && area.height() > 0);
                assertTrue(layout.panel().encloses(area));
            }
            for (int i = 0; i < areas.size(); i++)
                for (int j = i + 1; j < areas.size(); j++)
                    assertFalse("Overlapping regions at " + Arrays.toString(size), areas.get(i).overlaps(areas.get(j)));
            assertTrue(layout.preview().height() >= 26);
            assertTrue(layout.single().width() >= 70);
            assertTrue(layout.batch().width() >= 70);
            assertTrue(layout.poolRows() >= 3);
        }
    }

    @Test
    public void railAndPosterRemainBesidePoolInformationAtSmallSizes() {
        LotteryLayout layout = LotteryLayout.calculate(320, 240);
        assertTrue(layout.pool().right() < layout.heading().x());
        assertTrue(layout.poster().x() > layout.heading().right());
        assertEquals(layout.single().y(), layout.batch().y());
        for (int row = 0; row < layout.poolRows(); row++) assertTrue(layout.pool().encloses(layout.poolRow(row)));
    }

    @Test
    public void viewportHitTestsExcludeRightBottomAndNeighboringTools() {
        LotteryLayout layout = LotteryLayout.calculate(320, 240);
        LotteryLayout.Rect view = layout.description();
        assertTrue(view.contains(view.x(), view.y()));
        assertFalse(view.contains(view.right(), view.y()));
        assertFalse(view.contains(view.x(), view.bottom()));
        assertFalse(view.contains(layout.single().x(), layout.single().y()));
    }
}
