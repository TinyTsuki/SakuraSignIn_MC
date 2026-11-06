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
                    layout.description(), layout.previewHeading(), layout.preview(), layout.policy(), layout.poster(),
                    layout.billing(), layout.single(), layout.batch(), layout.close());
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
            assertTrue(layout.stage().encloses(layout.poster()));
            assertTrue(layout.stage().encloses(layout.preview()));
            assertTrue(layout.footer().encloses(layout.billing()));
            assertTrue(layout.footer().encloses(layout.batch()));
            assertTrue(layout.billing().right() < layout.single().x());
            assertTrue(layout.title().width() > layout.stage().width());
            assertTrue(layout.title().bottom() < layout.policy().y());
            assertTrue(layout.policy().bottom() < layout.stage().y());
        }
    }

    @Test
    public void railAndPosterRemainBesidePoolInformationAtSmallSizes() {
        LotteryLayout layout = LotteryLayout.calculate(320, 240);
        assertTrue(layout.pool().right() < layout.info().x());
        assertTrue(layout.poster().x() > layout.info().right());
        assertEquals(layout.single().y(), layout.batch().y());
        for (int row = 0; row < layout.poolRows(); row++) assertTrue(layout.pool().encloses(layout.poolRow(row)));
    }

    @Test
    public void hiddenCountdownReleasesItsSpaceWithoutMovingTheRailOrDrawControls() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {854, 480}}) {
            var scheduled = LotteryLayout.calculate(size[0], size[1], true);
            var unscheduled = LotteryLayout.calculate(size[0], size[1], false);
            assertEquals(0, unscheduled.info().height());
            assertEquals(unscheduled.stage().y() + 6, unscheduled.description().y());
            assertEquals(24, scheduled.description().y() - unscheduled.description().y());
            assertEquals(24, unscheduled.preview().height() - scheduled.preview().height());
            assertEquals(scheduled.pool().y(), unscheduled.pool().y());
            assertEquals(scheduled.single().y(), unscheduled.single().y());
            assertEquals(scheduled.batch().y(), unscheduled.batch().y());
        }
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
