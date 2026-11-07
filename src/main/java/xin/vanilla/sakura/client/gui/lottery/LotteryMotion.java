package xin.vanilla.sakura.client.gui.lottery;

import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation only: nonnegative reward indices refer to the server's immutable winners.
 */
public final class LotteryMotion {
    public record Card(int slot, double x, double y, double width, double height, int reward,
                       boolean face, boolean matched) {
    }

    public record Frame(List<Card> cards, boolean open, boolean light) {
        public Frame {
            cards = List.copyOf(cards);
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
        return Math.max(35, Math.min(820, 5200 / Math.max(1, count)));
    }

    private static long flipStep(int count) {
        return count == 1 ? 3500 : Math.max(150, Math.min(1800, 9000 / Math.max(1, count)));
    }

    public static long duration(LotteryAnimationStyle style, int count, int seed) {
        return switch (style) {
            case ENVELOPE, CHEST -> 2200 + (Math.max(1, count) - 1) * extractStep(count);
            case FAN -> count == 1 ? 4100 : 1100 + count * fanStep(count);
            case BOUNCE -> 250 + 3 * 950 + 180 + index(seed, 37, 5) * 90 + 1300;
            case FLIP -> 900 + count * flipStep(count);
            case INSTANT -> 0;
            case CARDS -> 4800;
            default -> 6800;
        };
    }

    public static Frame frame(LotteryAnimationStyle style, long elapsed, int count, int candidates, int seed,
                              double width, double height) {
        if (count < 1 || !Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            return new Frame(List.of(), false, false);
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
            int active = count == 1 ? index(seed, 11, 5) : Math.min(count - 1, Math.max(0, (int) ((t - 460) / step)));
            int batch = count == 1 ? 0 : active / 5 * 5;
            for (int i = 0; i < 5; i++) {
                int winner = batch + i;
                if (count > 1 && winner >= count) continue;
                double local = count == 1 ? t - 2350 : t - 460 - winner * step;
                boolean drawn = count == 1 ? i == active && local >= 0 : local >= 0;
                double lift = drawn ? progress(local, 0, 350) : 0;
                boolean face = drawn && local >= (count == 1 ? 450 : Math.min(220, step * .32));
                double turn = face ? progress(local, count == 1 ? 450 : Math.min(220, step * .32), count == 1 ? 520 : Math.min(520, step * .52)) : 0;
                double cardWidth = 40 * Math.max(.06, Math.abs(1 - 2 * turn));
                double stored = count > 1 && drawn ? progress(local, Math.min(740, step * .92), 350) : 0;
                double consider = count == 1 && i == (int) ((t - 400) / 330) && t >= 400 && t < 2050
                        ? Math.sin(Math.PI * Math.min(1, (t - 400 - i * 330) / 270)) * 9 : 0;
                double arc = Math.abs(i - 2) * 5;
                double x = 160 + (i - 2) * (25 + 18 * stored) - cardWidth / 2;
                double y = 58 + arc - lift * 35 - consider + stored * 68;
                cards.add(new Card(i, x, y, cardWidth, 60, count == 1 ? 0 : winner, turn >= .5, false));
            }
        } else if (style == LotteryAnimationStyle.BOUNCE) {
            int round = Math.max(0, Math.min(3, (int) ((t - 250) / 950)));
            int chosen = index(seed, 37, 5);
            double local = t - 250 - round * 950;
            for (int i = 0; i < 5; i++) {
                boolean winner = round == 3 && i == chosen;
                double phase = local - 180 - i * 90;
                double hop = phase < 0 || phase > 420 || round == 3 && i > chosen ? 0 : Math.sin(Math.PI * phase / 420) * 12;
                double drop = winner ? progress(phase, 120, 530) : 0;
                double x = 48 + i * 46 + (140 - 48 - i * 46) * drop;
                double y = 45 - hop + drop * 72;
                cards.add(new Card(i, x, y, 40, 40, winner ? 0 : -1 - index(seed, round * 5 + i, candidates), true, false));
            }
        } else if (style == LotteryAnimationStyle.FLIP) {
            long span = flipStep(count);
            int group = Math.max(0, Math.min(count - 1, (int) ((t - 200) / span)));
            double local = t - 200 - group * span;
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
                c.width() * scale, c.height() * scale, c.reward(), c.face(), c.matched())).toList(), t >= 700, t >= 900);
    }
}
