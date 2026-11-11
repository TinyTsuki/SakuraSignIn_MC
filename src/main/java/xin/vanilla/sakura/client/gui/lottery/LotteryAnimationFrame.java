package xin.vanilla.sakura.client.gui.lottery;

import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LotteryAnimationFrame {
    private LotteryAnimationFrame() {
    }

    public static final class Slot {
        private final int index;
        private final double x, y;
        private final boolean active;

        private Slot(int index, double x, double y, boolean active) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.active = active;
        }

        public int index() {
            return index;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public boolean active() {
            return active;
        }
    }

    public static List<Slot> frame(LotteryAnimationStyle style, long elapsed, double width, double height, int previews) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            return Collections.emptyList();
        elapsed = Math.max(0, elapsed);
        List<Slot> result = new ArrayList<>();
        if (style == LotteryAnimationStyle.STRIP) {
            double offset = (1 - Math.pow(1 - Math.min(1, elapsed / 6500D), 5)) * 43 * 38;
            double period = 48 * 38;
            for (int i = 0; i < 48; i++) {
                double distance = i * 38 - offset;
                distance -= Math.floor((distance + period / 2) / period) * period;
                result.add(new Slot(i, Math.round(width / 2 + distance - 8), height / 2 - 8, i == 43));
            }
        } else if (style == LotteryAnimationStyle.ROULETTE) {
            if (width < 24 || height < 24) return Collections.emptyList();
            int desired = Math.max(6, Math.min(12, previews)), columns = 0, rows = 0;
            double spanX = width - 24, spanY = height - 24;
            for (int count = desired / 2 * 2; count >= 4 && columns == 0; count -= 2) {
                double best = Double.POSITIVE_INFINITY;
                for (int x = 2; x <= count / 2; x++) {
                    int y = (count + 4) / 2 - x;
                    if (y < 2 || (x - 1) * 32 > spanX || (y - 1) * 32 > spanY) continue;
                    double score = Math.abs(Math.log((spanX / (x - 1)) / (spanY / (y - 1))));
                    if (score < best) {
                        best = score;
                        columns = x;
                        rows = y;
                    }
                }
            }
            List<double[]> positions = new ArrayList<>();
            if (columns > 0) {
                double dx = spanX / (columns - 1), dy = spanY / (rows - 1);
                for (int x = 0; x < columns; x++) positions.add(new double[]{4 + x * dx, 4});
                for (int y = 1; y < rows; y++) positions.add(new double[]{4 + spanX, 4 + y * dy});
                for (int x = columns - 2; x >= 0; x--) positions.add(new double[]{4 + x * dx, 4 + spanY});
                for (int y = rows - 2; y >= 1; y--) positions.add(new double[]{4, 4 + y * dy});
            } else {
                boolean horizontal = spanX >= spanY;
                int count = Math.min(desired, 1 + (int) (Math.max(spanX, spanY) / 32));
                for (int i = 0; i < count; i++) {
                    double offset = (i - (count - 1) / 2D) * 32;
                    positions.add(new double[]{width / 2 - 8 + (horizontal ? offset : 0), height / 2 - 8 + (horizontal ? 0 : offset)});
                }
            }
            int count = positions.size(), winner = count - 1;
            double progress = Math.min(1, elapsed / 6500D), eased = 1 - Math.pow(1 - progress, 4);
            int active = progress >= 1 ? winner : (int) Math.floor(eased * (count * 7 + winner)) % count;
            for (int i = 0; i < count; i++) {
                double[] position = positions.get(i);
                result.add(new Slot(i, position[0], position[1], i == active));
            }
        }
        return Collections.unmodifiableList(result);
    }
}
