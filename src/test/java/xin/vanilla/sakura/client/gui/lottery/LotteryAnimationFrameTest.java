package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class LotteryAnimationFrameTest {
    @Test public void stripRetainsCandidatesOnBothSidesThroughoutDeceleration() {
        for (double width : new double[]{110,300,536,1200}) for (long time : new long[]{0,2000,6400,6500,6799}) {
            List<LotteryAnimationFrame.Slot> visible = LotteryAnimationFrame.frame(LotteryAnimationStyle.STRIP,time,width,140,12).stream()
                    .filter(s -> s.x() >= 0 && s.x()+16 <= width).sorted(java.util.Comparator.comparingDouble(LotteryAnimationFrame.Slot::x)).collect(Collectors.toList());
            assertTrue(visible.get(0).x() <= 38);
            assertTrue(visible.get(visible.size()-1).x()+16 >= width-38);
            for (int i=1;i<visible.size();i++) assertEquals(38,visible.get(i).x()-visible.get(i-1).x(),1);
        }
    }
    @Test public void stripKeepsTheAuthoritativeWinnerAtTheCenter() {
        List<LotteryAnimationFrame.Slot> slots=LotteryAnimationFrame.frame(LotteryAnimationStyle.STRIP,6500,300,140,12);
        assertEquals(48,slots.size());assertEquals(142,slots.get(43).x(),0);assertTrue(slots.get(43).active());
    }
    @Test public void wheelUsesSeparatedRectangularPositionsAndEndsOnTheSameWinner() {
        for(double width:new double[]{80,110,180,300,536}) for(double height:new double[]{60,90,140,188}) {
            List<LotteryAnimationFrame.Slot> slots=LotteryAnimationFrame.frame(LotteryAnimationStyle.ROULETTE,6500,width,height,12);
            assertFalse(slots.isEmpty());assertTrue(slots.size()<=12);assertTrue(slots.get(slots.size()-1).active());
            for(LotteryAnimationFrame.Slot s:slots){
                assertTrue(s.x()>=4 && s.y()>=4 && s.x()+20<=width && s.y()+20<=height);
                for(LotteryAnimationFrame.Slot other:slots) if(s!=other) assertTrue(Math.abs(s.x()-other.x())>=28 || Math.abs(s.y()-other.y())>=28);
            }
        }
        List<LotteryAnimationFrame.Slot> full=LotteryAnimationFrame.frame(LotteryAnimationStyle.ROULETTE,6500,300,140,12);
        assertEquals(12,full.size());assertEquals(4,full.get(11).x(),0);assertEquals(62,full.get(11).y(),0);
    }
    @Test public void invalidAreasAndUnrelatedModesDoNotProduceFrames() {
        assertTrue(LotteryAnimationFrame.frame(LotteryAnimationStyle.STRIP,0,Double.NaN,140,12).isEmpty());
        assertTrue(LotteryAnimationFrame.frame(LotteryAnimationStyle.ROULETTE,0,100,Double.POSITIVE_INFINITY,12).isEmpty());
        assertTrue(LotteryAnimationFrame.frame(LotteryAnimationStyle.CARDS,0,300,140,12).isEmpty());
        assertTrue(LotteryAnimationFrame.frame(LotteryAnimationStyle.INSTANT,0,300,140,12).isEmpty());
    }
    @Test public void nativeRenderingKeepsItsOriginalCardsAndResults() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/sakura/screen/LotteryRevealScreen.java")),StandardCharsets.UTF_8);
        assertTrue(source.contains("LotteryAnimationFrame.frame(LotteryAnimationStyle.STRIP"));
        assertTrue(source.contains("LotteryAnimationFrame.frame(LotteryAnimationStyle.ROULETTE"));
        String compact = source.replaceAll("\\s+", "");
        assertTrue(compact.contains("34,54"));assertTrue(compact.contains("local%columns*22"));
        assertFalse(source.contains("Math.cos(angle)"));assertFalse(source.contains("closeButton"));
    }
}
