package xin.vanilla.sakura.client.gui.lottery;

public final class LotteryLayout {
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class DrawControls {
        Rect billing;
        Rect single;
        Rect picker;
        Rect batch;
    }

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

    public static final int REWARD_STRIDE = 26;
    private static final int POOL_STRIDE = 44;
    private final Rect panel, title, pool, stage, policy, poster, info, description, previewHeading, preview, footer, billing, single, batch, close;

    private LotteryLayout(Rect panel, Rect title, Rect pool, Rect stage, Rect policy, Rect poster, Rect info, Rect description,
                          Rect previewHeading, Rect preview, Rect footer, Rect billing, Rect single, Rect batch, Rect close) {
        this.panel = panel;
        this.title = title;
        this.pool = pool;
        this.stage = stage;
        this.policy = policy;
        this.poster = poster;
        this.info = info;
        this.description = description;
        this.previewHeading = previewHeading;
        this.preview = preview;
        this.footer = footer;
        this.billing = billing;
        this.single = single;
        this.batch = batch;
        this.close = close;
    }

    public static LotteryLayout calculate(int width, int height) {
        return calculate(width, height, true);
    }

    public static LotteryLayout calculate(int width, int height, boolean showInfo) {
        int panelWidth = Math.min(640, width - 16), panelHeight = Math.min(350, height - 16);
        int top = (height - panelHeight) / 2;
        Rect panel = new Rect((width - panelWidth) / 2, top, panelWidth, panelHeight);
        int x = panel.x() + 8, right = panel.right() - 8;
        int bodyY = top + 53, footerY = panel.bottom() - 36, bodyBottom = footerY - 5;
        int railWidth = Math.max(58, Math.min(106, panelWidth / 5));
        Rect title = new Rect(x, top + 6, panelWidth - 36, 32);
        Rect policy = new Rect(x, title.bottom() + 1, title.width(), 10);
        Rect close = new Rect(right - 14, top + 7, 14, 14);
        Rect pool = new Rect(x, bodyY, railWidth, bodyBottom - bodyY);
        Rect stage = new Rect(pool.right() + 6, bodyY, right - pool.right() - 6, bodyBottom - bodyY);
        int copyX = stage.x() + 8, textWidth = stage.width() * 43 / 100;
        Rect info = new Rect(copyX, bodyY + 6, textWidth, showInfo ? 21 : 0);
        Rect description = new Rect(copyX, info.bottom() + (showInfo ? 3 : 0), textWidth, Math.min(44, Math.max(10, stage.height() / 6)));
        Rect previewHeading = new Rect(copyX, description.bottom() + 4, textWidth, 10);
        Rect preview = new Rect(copyX, previewHeading.bottom() + 2, textWidth, Math.max(0, bodyBottom - previewHeading.bottom() - 6));
        Rect poster = new Rect(copyX + textWidth + 5, bodyY + 5, right - copyX - textWidth - 10, stage.height() - 10);
        Rect footer = new Rect(x, footerY, right - x, panel.bottom() - footerY - 4);
        int buttonWidth = Math.min(96, Math.max(45, footer.width() / 4));
        Rect batch = new Rect(right - buttonWidth, footerY + 5, buttonWidth, 22);
        Rect single = new Rect(batch.x() - 5 - buttonWidth, batch.y(), buttonWidth, 22);
        Rect billing = new Rect(x + 3, footerY + 3, single.x() - x - 9, 29);
        return new LotteryLayout(panel, title, pool, stage, policy, poster, info, description, previewHeading, preview, footer, billing, single, batch, close);
    }

    public int poolRows() {
        return Math.max(0, (pool.height() + 3) / POOL_STRIDE);
    }

    public Rect poolRow(int row) {
        return new Rect(pool.x(), pool.y() + row * POOL_STRIDE, pool.width() - 7, POOL_STRIDE - 3);
    }

    public int rewardColumns() {
        return Math.max(1, (preview.width() - 8) / REWARD_STRIDE);
    }

    public Rect panel() {
        return panel;
    }

    public Rect title() {
        return title;
    }

    public Rect pool() {
        return pool;
    }

    public Rect stage() {
        return stage;
    }

    public Rect policy() {
        return policy;
    }

    public Rect poster() {
        return poster;
    }

    public Rect info() {
        return info;
    }

    public Rect description() {
        return description;
    }

    public Rect previewHeading() {
        return previewHeading;
    }

    public Rect preview() {
        return preview;
    }

    public Rect footer() {
        return footer;
    }

    public Rect billing() {
        return billing;
    }

    public DrawControls drawControls(boolean showPicker) {
        if (!showPicker)
            return new DrawControls(billing, single, new Rect(batch.x(), batch.y(), 0, batch.height()), batch);
        int gap = 5;
        int buttonWidth = Math.max(45, batch.width() - 12);
        Rect draw = new Rect(batch.right() - buttonWidth, batch.y(), buttonWidth, batch.height());
        // The picker reserves 33 pixels for the arrow and clear button before drawing its value.
        Rect picker = new Rect(draw.x() - gap - 58, draw.y(), 58, draw.height());
        Rect once = new Rect(picker.x() - gap - buttonWidth, draw.y(), buttonWidth, draw.height());
        Rect cost = new Rect(billing.x(), billing.y(), Math.max(0, once.x() - billing.x() - 6), billing.height());
        return new DrawControls(cost, once, picker, draw);
    }

    public Rect single() {
        return single;
    }

    public Rect batch() {
        return batch;
    }

    public Rect close() {
        return close;
    }
}
