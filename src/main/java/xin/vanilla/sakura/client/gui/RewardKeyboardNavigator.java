package xin.vanilla.sakura.client.gui;

import lombok.Getter;

import java.util.Collection;
import java.util.Comparator;

/**
 * 根据奖励的实际二维位置寻找键盘导航目标。
 */
public final class RewardKeyboardNavigator {
    private RewardKeyboardNavigator() {
    }

    public static String findNext(String currentId, Collection<Point> points,
                                  Direction direction) {
        if (currentId == null || points == null || direction == null) {
            return null;
        }
        Point current = points.stream()
                .filter(point -> currentId.equals(point.id))
                .findFirst()
                .orElse(null);
        if (current == null) {
            return null;
        }
        return points.stream()
                .filter(point -> !currentId.equals(point.id))
                .filter(point -> direction.accepts(current, point))
                .min(Comparator
                        .comparingDouble((Point point) -> direction.perpendicularDistance(current, point))
                        .thenComparingDouble(point -> direction.forwardDistance(current, point))
                        .thenComparing(Point::getId))
                .map(Point::getId)
                .orElse(null);
    }

    public static final class Direction {
        public static final Direction LEFT = new Direction(-1, 0);
        public static final Direction RIGHT = new Direction(1, 0);
        public static final Direction UP = new Direction(0, -1);
        public static final Direction DOWN = new Direction(0, 1);

        private final int horizontal;
        private final int vertical;

        private Direction(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        private boolean accepts(Point current, Point candidate) {
            return horizontal != 0
                    ? (candidate.x - current.x) * horizontal > 0
                    : (candidate.y - current.y) * vertical > 0;
        }

        private double forwardDistance(Point current, Point candidate) {
            return horizontal != 0
                    ? Math.abs(candidate.x - current.x)
                    : Math.abs(candidate.y - current.y);
        }

        private double perpendicularDistance(Point current, Point candidate) {
            return horizontal != 0
                    ? Math.abs(candidate.y - current.y)
                    : Math.abs(candidate.x - current.x);
        }
    }

    @Getter
    public static final class Point {
        private final String id;
        private final double x;
        private final double y;

        public Point(String id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }
}
