package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class LotteryMotionTest {
    private static final List<LotteryAnimationStyle> STYLES = com.google.common.collect.ImmutableList.of(LotteryAnimationStyle.ENVELOPE,
            LotteryAnimationStyle.FAN, LotteryAnimationStyle.BOUNCE, LotteryAnimationStyle.CHEST, LotteryAnimationStyle.FLIP);

    @Test
    public void newStylesAppendWithoutChangingExistingWireOrdinals() {
        assertArrayEquals(new LotteryAnimationStyle[]{LotteryAnimationStyle.STRIP, LotteryAnimationStyle.CARDS,
                LotteryAnimationStyle.ROULETTE, LotteryAnimationStyle.INSTANT}, java.util.Arrays.copyOf(LotteryAnimationStyle.values(), 4));
        assertEquals(com.google.common.collect.ImmutableList.of(LotteryAnimationStyle.ENVELOPE, LotteryAnimationStyle.FAN, LotteryAnimationStyle.BOUNCE, LotteryAnimationStyle.FLIP), LotteryAnimationStyle.editorChoices(null));
        assertFalse(LotteryAnimationStyle.editorChoices(LotteryAnimationStyle.CHEST).contains(LotteryAnimationStyle.CHEST));
        assertEquals(LotteryAnimationStyle.ENVELOPE, LotteryAnimationStyle.CHEST.presentationStyle());
        assertTrue(LotteryAnimationStyle.editorChoices(LotteryAnimationStyle.STRIP).contains(LotteryAnimationStyle.STRIP));
    }

    @Test
    public void allFiveAnimationsKeepFiniteBoundedGeometryAndRealWinnerIndices() {
        for (lombok.var style : STYLES)
            for (int count : new int[]{1, 5, 10, 100})
                for (int seed = 0; seed < 12; seed++)
                    for (int time = 0; time <= LotteryMotion.duration(style, count, seed); time += 37) {
                        lombok.var frame = LotteryMotion.frame(style, time, count, 64, seed, 320, 180);
                        assertTrue(frame.cards().size() <= 5);
                        assertEquals(frame.cards().size(), new HashSet<>(frame.cards().stream().map(LotteryMotion.Card::slot).collect(java.util.stream.Collectors.toList())).size());
                        for (lombok.var card : frame.cards()) {
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
        for (lombok.var style : com.google.common.collect.ImmutableList.of(LotteryAnimationStyle.ENVELOPE, LotteryAnimationStyle.CHEST)) {
            lombok.var extracted = new HashSet<Integer>();
            for (int time = 0; time < LotteryMotion.duration(style, 10, 17); time += 20)
                LotteryMotion.frame(style, time, 10, 5, 17, 320, 180).cards().stream()
                        .filter(LotteryMotion.Card::face).forEach(card -> extracted.add(card.reward()));
            assertEquals(10, extracted.size());
            assertTrue(LotteryMotion.frame(style, 1000, 10, 5, 17, 320, 180).open());
        }
    }

    @Test
    public void bounceStopsAtWinningPositionAndDoesNotWaitForAllFiveCandidates() {
        lombok.var ends = new HashSet<Long>();
        for (int seed = 0; seed < 50; seed++) {
            long end = LotteryMotion.duration(LotteryAnimationStyle.BOUNCE, 1, seed);
            ends.add(end);
            assertTrue(end <= 5360);
            lombok.var frame = LotteryMotion.frame(LotteryAnimationStyle.BOUNCE, end - 200, 1, 200, seed, 320, 180);
            assertEquals(5, frame.cards().size());
            assertEquals(1, frame.cards().stream().filter(c -> c.reward() == 0 && c.y() > 100).count());
        }
        assertTrue(ends.size() > 1);
    }

    @Test
    public void flipWaitsUntilTheLastCardIsOpenBeforeMarkingTwoOrThreeMatches() {
        for (int seed = 0; seed < 50; seed++) {
            lombok.var before = LotteryMotion.frame(LotteryAnimationStyle.FLIP, 2000, 1, 64, seed, 320, 180);
            assertTrue(before.cards().stream().noneMatch(LotteryMotion.Card::matched));
            lombok.var finalFrame = LotteryMotion.frame(LotteryAnimationStyle.FLIP, 4000, 1, 64, seed, 320, 180);
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
        for (lombok.var style : STYLES) {
            assertTrue(LotteryMotion.frame(style, 20, 1, 0, 1, Double.NaN, 180).cards().isEmpty());
            assertTrue(LotteryMotion.frame(style, 20, 1, 0, 1, 320, 0).cards().isEmpty());
        }
    }

    @Test
    public void flapOpensContinuouslyAndLegacyChestUsesTheSamePresentation() {
        double previous = 0;
        for (int time = 400; time <= 1000; time += 25) {
            lombok.var frame = LotteryMotion.frame(LotteryAnimationStyle.ENVELOPE, time, 1, 12, 1, 320, 180);
            assertTrue(frame.opening() >= previous);
            assertTrue(frame.opening() - previous < .1);
            assertEquals(frame, LotteryMotion.frame(LotteryAnimationStyle.CHEST, time, 1, 12, 1, 320, 180));
            previous = frame.opening();
        }
        assertEquals(1, previous, 0);
    }

    @Test
    public void aSingleCardIsLiftedBeforeFlippingAndOtherCardsStayInPlace() {
        lombok.var base = LotteryMotion.frame(LotteryAnimationStyle.FAN, 2600, 1, 12, 1, 320, 180);
        lombok.var lifted = LotteryMotion.frame(LotteryAnimationStyle.FAN, 3200, 1, 12, 1, 320, 180);
        int moved = 0;
        for (int i = 0; i < 5; i++) {
            lombok.var before = base.cards().get(i);
            lombok.var after = lifted.cards().get(i);
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
                lombok.var frame = LotteryMotion.frame(LotteryAnimationStyle.BOUNCE, time, 1, 30, seed, 320, 180);
                for (lombok.var card : frame.cards()) {
                    assertTrue(card.y() >= 40.5 - .001);
                    assertEquals(68 + card.slot() * 46, card.x() + card.width() / 2, .001);
                }
            }
        }
    }

    @Test
    public void fanUsesOverlappingCardsAndLiftClearsTheDeck() {
        lombok.var rest = LotteryMotion.frame(LotteryAnimationStyle.FAN, 0, 1, 12, 1, 320, 180);
        assertEquals(25, rest.cards().get(1).x() - rest.cards().get(0).x(), 0);
        assertTrue(rest.cards().get(0).x() + rest.cards().get(0).width() > rest.cards().get(1).x());
        lombok.var lifted = LotteryMotion.frame(LotteryAnimationStyle.FAN, 3200, 1, 12, 1, 320, 180);
        lombok.var selected = lifted.cards().stream().min(java.util.Comparator.comparingDouble(LotteryMotion.Card::y)).orElseThrow(java.util.NoSuchElementException::new);
        assertTrue(selected.y() + selected.height() < 65);
    }

    @Test
    public void batchFlipHoldsTheCompletedRoundBeforeDealingAgain() {
        lombok.var first = LotteryMotion.frame(LotteryAnimationStyle.FLIP, 2000, 5, 12, 1, 320, 180);
        lombok.var held = LotteryMotion.frame(LotteryAnimationStyle.FLIP, 2700, 5, 12, 1, 320, 180);
        assertEquals(first, held);
        assertTrue(held.cards().get(4).face());
        assertEquals(0, held.cards().get(4).reward());
        lombok.var next = LotteryMotion.frame(LotteryAnimationStyle.FLIP, 2810, 5, 12, 1, 320, 180);
        assertEquals(1, next.cards().get(4).reward());
        assertFalse(next.cards().get(4).face());
        assertEquals(13900, LotteryMotion.duration(LotteryAnimationStyle.FLIP, 5, 1));
    }

    @Test
    public void bounceRoundFlipTakes320MillisecondsAndFinishesBeforeHopping() {
        lombok.var halfway = LotteryMotion.frame(LotteryAnimationStyle.BOUNCE, 250 + 1110 + 160, 1, 12, 1, 320, 180);
        assertTrue(halfway.cards().stream().allMatch(card -> card.width() < 3));
        lombok.var open = LotteryMotion.frame(LotteryAnimationStyle.BOUNCE, 250 + 1110 + 320, 1, 12, 1, 320, 180);
        for (lombok.var card : open.cards()) {
            assertEquals(40, card.width(), 0);
            assertEquals(45, card.y(), 0);
        }
    }
}
