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
        assertEquals(List.of(LotteryAnimationStyle.ENVELOPE, LotteryAnimationStyle.FAN, LotteryAnimationStyle.BOUNCE, LotteryAnimationStyle.FLIP), LotteryAnimationStyle.editorChoices(null));
        assertFalse(LotteryAnimationStyle.editorChoices(LotteryAnimationStyle.CHEST).contains(LotteryAnimationStyle.CHEST));
        assertEquals(LotteryAnimationStyle.ENVELOPE, LotteryAnimationStyle.CHEST.presentationStyle());
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
        assertEquals(4600, LotteryMotion.duration(LotteryAnimationStyle.FAN, 1, 0));
        assertEquals(6350, LotteryMotion.duration(LotteryAnimationStyle.FAN, 5, 0));
        assertEquals(7600, LotteryMotion.duration(LotteryAnimationStyle.FAN, 10, 0));
        for (var style : STYLES) {
            assertTrue(LotteryMotion.frame(style, 20, 1, 0, 1, Double.NaN, 180).cards().isEmpty());
            assertTrue(LotteryMotion.frame(style, 20, 1, 0, 1, 320, 0).cards().isEmpty());
        }
    }

    @Test
    public void flapOpensContinuouslyAndLegacyChestUsesTheSamePresentation() {
        double previous = 0;
        for (int time = 400; time <= 1000; time += 25) {
            var frame = LotteryMotion.frame(LotteryAnimationStyle.ENVELOPE, time, 1, 12, 1, 320, 180);
            assertTrue(frame.opening() >= previous);
            assertTrue(frame.opening() - previous < .1);
            assertEquals(frame, LotteryMotion.frame(LotteryAnimationStyle.CHEST, time, 1, 12, 1, 320, 180));
            previous = frame.opening();
        }
        assertEquals(1, previous, 0);
    }

    @Test
    public void aSingleCardIsLiftedBeforeFlippingAndOtherCardsStayInPlace() {
        var base = LotteryMotion.frame(LotteryAnimationStyle.FAN, 2600, 1, 12, 1, 320, 180);
        var lifted = LotteryMotion.frame(LotteryAnimationStyle.FAN, 3200, 1, 12, 1, 320, 180);
        int moved = 0;
        for (int i = 0; i < 5; i++) {
            var before = base.cards().get(i);
            var after = lifted.cards().get(i);
            assertFalse(after.face());
            if (after.y() < before.y() - 1) {
                moved++;
                assertEquals(42, after.width(), 0);
            } else {
                assertEquals(before.x(), after.x(), 0);
                assertEquals(before.y(), after.y(), 0);
            }
        }
        assertEquals(1, moved);
    }

    @Test
    public void bounceUsesSmallHopsAndWinningDropDoesNotSlideSideways() {
        for (int seed = 0; seed < 12; seed++) {
            for (int time = 0; time < LotteryMotion.duration(LotteryAnimationStyle.BOUNCE, 1, seed); time += 20) {
                var frame = LotteryMotion.frame(LotteryAnimationStyle.BOUNCE, time, 1, 30, seed, 320, 180);
                for (var card : frame.cards()) {
                    assertTrue(card.y() >= 40.5 - .001);
                    assertEquals(68 + card.slot() * 46, card.x() + card.width() / 2, .001);
                }
            }
        }
    }
}
