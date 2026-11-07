package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class LotteryMotionTest {
    private static final List<LotteryAnimationStyle> STYLES = List.of(LotteryAnimationStyle.ENVELOPE,
            LotteryAnimationStyle.FAN, LotteryAnimationStyle.BOUNCE, LotteryAnimationStyle.CHEST, LotteryAnimationStyle.FLIP);

    @Test
    public void newStylesAppendWithoutChangingExistingWireOrdinals() {
        assertArrayEquals(new LotteryAnimationStyle[]{LotteryAnimationStyle.STRIP, LotteryAnimationStyle.CARDS,
                LotteryAnimationStyle.ROULETTE, LotteryAnimationStyle.INSTANT}, java.util.Arrays.copyOf(LotteryAnimationStyle.values(), 4));
        assertEquals(STYLES, LotteryAnimationStyle.editorChoices(null));
        assertTrue(LotteryAnimationStyle.editorChoices(LotteryAnimationStyle.STRIP).contains(LotteryAnimationStyle.STRIP));
    }

    @Test
    public void allFiveAnimationsKeepFiniteBoundedGeometryAndRealWinnerIndices() {
        for (var style : STYLES)
            for (int count : new int[]{1, 5, 10, 100})
                for (int seed = 0; seed < 12; seed++)
                    for (int time = 0; time <= LotteryMotion.duration(style, count, seed); time += 37) {
                        var frame = LotteryMotion.frame(style, time, count, 64, seed, 320, 180);
                        assertTrue(frame.cards().size() <= 5);
                        assertEquals(frame.cards().size(), new HashSet<>(frame.cards().stream().map(LotteryMotion.Card::slot).toList()).size());
                        for (var card : frame.cards()) {
                            assertTrue(Double.isFinite(card.x()) && Double.isFinite(card.y()));
                            assertTrue(card.x() >= 0 && card.y() >= 0);
                            assertTrue(card.x() + card.width() <= 321);
                            assertTrue(card.y() + card.height() <= 181);
                            assertTrue(card.reward() < count && card.reward() >= -64);
                        }
                    }
    }

    @Test
    public void envelopeAndChestExtractEveryWinnerInsteadOfOnlyTheFirst() {
        for (var style : List.of(LotteryAnimationStyle.ENVELOPE, LotteryAnimationStyle.CHEST)) {
            var extracted = new HashSet<Integer>();
            for (int time = 0; time < LotteryMotion.duration(style, 10, 17); time += 20)
                LotteryMotion.frame(style, time, 10, 5, 17, 320, 180).cards().stream()
                        .filter(LotteryMotion.Card::face).forEach(card -> extracted.add(card.reward()));
            assertEquals(10, extracted.size());
            assertTrue(LotteryMotion.frame(style, 1000, 10, 5, 17, 320, 180).open());
        }
    }

    @Test
    public void bounceStopsAtWinningPositionAndDoesNotWaitForAllFiveCandidates() {
        var ends = new HashSet<Long>();
        for (int seed = 0; seed < 50; seed++) {
            long end = LotteryMotion.duration(LotteryAnimationStyle.BOUNCE, 1, seed);
            ends.add(end);
            assertTrue(end <= 5000);
            var frame = LotteryMotion.frame(LotteryAnimationStyle.BOUNCE, end - 200, 1, 200, seed, 320, 180);
            assertEquals(5, frame.cards().size());
            assertEquals(1, frame.cards().stream().filter(c -> c.reward() == 0 && c.y() > 100).count());
        }
        assertTrue(ends.size() > 1);
    }

    @Test
    public void flipWaitsUntilTheLastCardIsOpenBeforeMarkingTwoOrThreeMatches() {
        for (int seed = 0; seed < 50; seed++) {
            var before = LotteryMotion.frame(LotteryAnimationStyle.FLIP, 2000, 1, 64, seed, 320, 180);
            assertTrue(before.cards().stream().noneMatch(LotteryMotion.Card::matched));
            var finalFrame = LotteryMotion.frame(LotteryAnimationStyle.FLIP, 4000, 1, 64, seed, 320, 180);
            long matches = finalFrame.cards().stream().filter(LotteryMotion.Card::matched).count();
            assertTrue(matches == 2 || matches == 3);
            assertEquals(0, finalFrame.cards().get(4).reward());
            assertTrue(finalFrame.cards().get(4).face());
        }
    }

    @Test
    public void cardsUseTheSlowerApprovedSingleAndBatchTimings() {
        assertEquals(4100, LotteryMotion.duration(LotteryAnimationStyle.FAN, 1, 0));
        assertEquals(5200, LotteryMotion.duration(LotteryAnimationStyle.FAN, 5, 0));
        assertEquals(6300, LotteryMotion.duration(LotteryAnimationStyle.FAN, 10, 0));
        for (var style : STYLES) {
            assertTrue(LotteryMotion.frame(style, 20, 1, 0, 1, Double.NaN, 180).cards().isEmpty());
            assertTrue(LotteryMotion.frame(style, 20, 1, 0, 1, 320, 0).cards().isEmpty());
        }
    }
}
