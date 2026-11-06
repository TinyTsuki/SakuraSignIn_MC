package xin.vanilla.sakura.client.gui.lottery;

public final class LotteryLayout {
    public static final class Rect {
        private final int x, y, width, height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx < right() && my >= y && my < bottom();
        }

        public boolean encloses(Rect other) {
            return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    private final Rect panel, title, pool, heading, poster, info, description, preview, single, batch, close;

    private LotteryLayout(Rect panel, Rect title, Rect pool, Rect heading, Rect poster, Rect info, Rect description,
                          Rect preview, Rect single, Rect batch, Rect close) {
        this.panel = panel;
        this.title = title;
        this.pool = pool;
        this.heading = heading;
        this.poster = poster;
        this.info = info;
        this.description = description;
        this.preview = preview;
        this.single = single;
        this.batch = batch;
        this.close = close;
    }

    public static LotteryLayout calculate(int width, int height) {
        int panelWidth = Math.min(640, width - 16), top = height <= 300 ? 8 : 16;
        Rect panel = new Rect((width - panelWidth) / 2, top, panelWidth, height - top * 2);
        int x = panel.x() + 8, right = panel.right() - 8;
        int bodyY = top + 28, footerY = panel.bottom() - 30;
        int railWidth = Math.max(68, Math.min(115, panelWidth / 5));
        Rect title = new Rect(x, top + 10, panelWidth - 36, 12);
        Rect close = new Rect(right - 14, top + 7, 14, 14);
        Rect pool = new Rect(x, bodyY, railWidth, footerY - bodyY);
        int detailX = pool.right() + 8, detailWidth = right - detailX;
        int posterWidth = detailWidth / 2 - 3, textWidth = detailWidth - posterWidth - 6;
        Rect heading = new Rect(detailX, bodyY + 4, textWidth, 15);
        Rect description = new Rect(detailX, heading.bottom() + 2, textWidth, height < 300 ? 24 : 44);
        Rect poster = new Rect(right - posterWidth, bodyY, posterWidth, Math.max(1, footerY - bodyY - 5));
        int infoHeight = Math.min(28, Math.max(12, (footerY - description.bottom()) / 3));
        Rect info = new Rect(detailX, description.bottom() + 4, textWidth, infoHeight);
        Rect preview = new Rect(detailX, info.bottom() + 3, textWidth, Math.max(1, footerY - info.bottom() - 7));
        int buttonWidth = Math.min(96, (detailWidth - 6) / 2);
        Rect batch = new Rect(right - buttonWidth, footerY, buttonWidth, 22);
        Rect single = new Rect(batch.x() - 6 - buttonWidth, footerY, buttonWidth, 22);
        return new LotteryLayout(panel, title, pool, heading, poster, info, description, preview, single, batch, close);
    }

    public int poolRows() { return Math.max(0, pool.height() / 32); }

    public Rect poolRow(int row) { return new Rect(pool.x(), pool.y() + row * 32, pool.width() - 7, 28); }

    public Rect panel() { return panel; }
    public Rect title() { return title; }
    public Rect pool() { return pool; }
    public Rect heading() { return heading; }
    public Rect poster() { return poster; }
    public Rect info() { return info; }
    public Rect description() { return description; }
    public Rect preview() { return preview; }
    public Rect single() { return single; }
    public Rect batch() { return batch; }
    public Rect close() { return close; }
}
