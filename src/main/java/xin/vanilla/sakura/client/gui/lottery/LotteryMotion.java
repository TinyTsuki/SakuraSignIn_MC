package xin.vanilla.sakura.client.gui.lottery;

import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation only: nonnegative reward indices refer to the server's immutable winners.
 */
public final class LotteryMotion {
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    @lombok.AllArgsConstructor
    public static class Card {
        int slot;
        double x;
        double y;
        double width;
        double height;
        int reward;
        boolean face;
        boolean matched;
    }

    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class Frame {
        List<Card> cards;
        boolean open;
        boolean light;
        double opening;

        public Frame(List<Card> cards, boolean open, boolean light, double opening) {
            cards = com.google.common.collect.ImmutableList.copyOf(cards);
            this.cards = cards;
            this.open = open;
            this.light = light;
            this.opening = opening;
        }

    }

    private LotteryMotion() {
    }

    private static int index(int seed, int salt, int bound) {
        return Math.floorMod(Integer.rotateLeft(seed ^ salt * 0x45d9f3b, 13) * 31, Math.max(1, bound));
    }

    private static double progress(double value, double start, double length) {
        double p = Math.max(0, Math.min(1, (value - start) / length));
        return p * p * (3 - 2 * p);
    }

    private static long extractStep(int count) {
        return Math.min(700, 4000 / Math.max(1, count));
    }

    private static long fanStep(int count) {
        return Math.max(60, Math.min(1050, 6500 / Math.max(1, count)));
    }

    private static long flipStep(int count) {
        return count == 1 ? 3500 : Math.max(150, Math.min(1800, 9000 / Math.max(1, count)));
    }

    private static long flipRound(int count) {
        return flipStep(count) + (count == 1 ? 0 : 800);
    }

    public static long duration(LotteryAnimationStyle style, int count, int seed) {
        style = style.presentationStyle();
        switch (style) {
            case ENVELOPE:
            case CHEST:
                return 2200 + (Math.max(1, count) - 1) * extractStep(count);
            case FAN:
                return count == 1 ? 4600 : 1100 + count * fanStep(count);
            case BOUNCE:
                return 250 + 3 * 1110 + 340 + index(seed, 37, 5) * 135 + 900;
            case FLIP:
                return 900 + count * flipRound(count);
            case INSTANT:
                return 0;
            case CARDS:
                return 4800;
            default:
                return 6800;
        }
    }

    public static Frame frame(LotteryAnimationStyle style, long elapsed, int count, int candidates, int seed,
                              double width, double height) {
        if (count < 1 || !Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            return new Frame(com.google.common.collect.ImmutableList.of(), false, false, 0);
        style = style.presentationStyle();
        List<Card> cards = new ArrayList<>();
        double t = Math.max(0, elapsed);
        if (style == LotteryAnimationStyle.ENVELOPE || style == LotteryAnimationStyle.CHEST) {
            long step = extractStep(count);
            int current = Math.min(count - 1, Math.max(0, (int) ((t - 1200) / step)));
            int batch = current / 5 * 5, size = Math.min(5, count - batch);
            for (int i = batch; i <= current; i++) {
                double local = t - 1200 - i * step;
                if (local < 0) continue;
                double rise = progress(local, 0, 400);
                double store = count == 1 ? 0 : progress(local, Math.min(550, step * .85), 400);
                double w = 70 - store * 30, h = 65 - store * 13;
                double x = 160 - w / 2 + (i - batch - (size - 1) / 2D) * 44 * store;
                double y = 96 - rise * 78 - store * 5;
                cards.add(new Card(i - batch, x, y, w, h, i, rise > .85, false));
            }
        } else if (style == LotteryAnimationStyle.FAN) {
            long step = fanStep(count);
            int active = count == 1 ? index(seed, 11, 5) : Math.min(count - 1, Math.max(0, (int) ((t - 550) / step)));
            int batch = count == 1 ? 0 : active / 5 * 5;
            for (int i = 0; i < 5; i++) {
                int winner = batch + i;
                if (count > 1 && winner >= count) continue;
                double local = count == 1 ? t - 2700 : t - 550 - winner * step;
                boolean drawn = count == 1 ? i == active && local >= 0 : local >= 0;
                double liftLength = count == 1 ? 500 : Math.min(280, step * .3);
                double turnLength = count == 1 ? 650 : Math.min(560, step * .55);
                double lift = drawn ? progress(local, 0, liftLength) : 0;
                double turn = drawn ? progress(local, liftLength + 80, turnLength) : 0;
                double stored = count > 1 && drawn ? progress(local, liftLength + 80 + turnLength, Math.min(280, step * .25)) : 0;
                double cardWidth = (42 - stored * 6) * Math.max(.06, Math.abs(1 - 2 * turn));
                double considerPhase = t - 400 - i * 420;
                double consider = count == 1 && considerPhase >= 0 && considerPhase < 400
                        ? Math.sin(Math.PI * considerPhase / 400) * 5 : 0;
                int batchSize = count == 1 ? 5 : Math.min(5, count - batch);
                double offset = i - (batchSize - 1) / 2D;
                double baseX = 160 + offset * 25;
                double center = count == 1 ? baseX + (160 - baseX) * lift : baseX + offset * 18 * stored;
                double baseY = 65 + Math.abs(offset) * 5;
                double y = baseY + (4 - baseY) * lift - consider + stored * 85;
                cards.add(new Card(i, center - cardWidth / 2, y, cardWidth, 60 - stored * 8, count == 1 ? 0 : winner, turn >= .5, false));
            }
        } else if (style == LotteryAnimationStyle.BOUNCE) {
            int round = Math.max(0, Math.min(3, (int) ((t - 250) / 1110)));
            int chosen = index(seed, 37, 5);
            double local = t - 250 - round * 1110;
            for (int i = 0; i < 5; i++) {
                boolean winner = round == 3 && i == chosen;
                double phase = local - 340 - i * 135;
                double hop = phase < 0 || phase > 220 || round == 3 && i > chosen ? 0 : Math.sin(Math.PI * phase / 220) * 4.5;
                double fall = winner ? Math.max(0, Math.min(1, (phase - 110) / 500)) : 0;
                double drop = fall * fall;
                double turn = round == 0 ? 1 : progress(local, 0, 320);
                double w = 40 * Math.max(.06, Math.abs(1 - 2 * turn));
                double x = 48 + i * 46 + (40 - w) / 2;
                double y = 45 - hop + drop * 72;
                int candidate = candidates >= 5 ? Math.floorMod(index(seed, 73, candidates) + round * 5 + i, candidates)
                        : index(seed, round * 5 + i, candidates);
                cards.add(new Card(i, x, y, w, 40, winner ? 0 : -1 - candidate, turn >= .5, false));
            }
        } else if (style == LotteryAnimationStyle.FLIP) {
            long span = flipStep(count);
            long round = flipRound(count);
            int group = Math.max(0, Math.min(count - 1, (int) ((t - 200) / round)));
            double local = t - 200 - group * round;
            double interval = count == 1 ? 500 : span * .14, lead = count == 1 ? 260 : span * .15;
            double turnLength = count == 1 ? 520 : Math.min(440, span * .2);
            int first = index(seed, group + 53, 4), second = (first + 1 + index(seed, group + 63, 3)) % 4;
            boolean third = index(seed, group + 73, 2) == 0;
            boolean matched = local >= lead + 4 * interval + turnLength + 40;
            for (int i = 0; i < 5; i++) {
                double turn = progress(local, lead + i * interval, turnLength);
                double w = 40 * Math.max(.06, Math.abs(1 - 2 * turn));
                boolean same = i == 4 || i == first || third && i == second;
                cards.add(new Card(i, 48 + i * 46 + (40 - w) / 2, 48, w, 64,
                        same ? group : -1 - index(seed, group * 5 + i + 83, candidates), turn >= .5, matched && same));
            }
        }
        double scale = Math.min(width / 320, height / 180);
        double dx = (width - 320 * scale) / 2, dy = (height - 180 * scale) / 2;
        return new Frame(cards.stream().map(c -> new Card(c.slot(), dx + c.x() * scale, dy + c.y() * scale,
                c.width() * scale, c.height() * scale, c.reward(), c.face(), c.matched())).collect(java.util.stream.Collectors.toList()), t >= 1000, false, progress(t, 400, 600));
    }
}
