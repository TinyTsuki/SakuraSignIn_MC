package xin.vanilla.sakura.client.gui.lottery;

public final class LotteryLayout {
    public static final class Rect {
        private final int x, y, width, height;
        public Rect(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double mx, double my) { return mx >= x && mx < right() && my >= y && my < bottom(); }
        public boolean encloses(Rect other) { return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom(); }
        public boolean overlaps(Rect other) { return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y; }
    }
    private final Rect panel, title, pool, info, description, preview, single, count, batch, animation, close;
    private LotteryLayout(Rect panel, Rect title, Rect pool, Rect info, Rect description, Rect preview,
                          Rect single, Rect count, Rect batch, Rect animation, Rect close) {
        this.panel=panel; this.title=title; this.pool=pool; this.info=info; this.description=description;
        this.preview=preview; this.single=single; this.count=count; this.batch=batch; this.animation=animation; this.close=close;
    }
    public static LotteryLayout calculate(int width, int height) {
        int panelWidth = Math.min(640, width - 24);
        int top = height <= 300 ? 8 : 16;
        Rect panel = new Rect((width - panelWidth) / 2, top, panelWidth, height - top * 2);
        int x = panel.x() + 12, innerWidth = panelWidth - 24;
        boolean narrow = innerWidth < 520;
        int footerY = panel.bottom() - 10 - (narrow ? 50 : 22);
        Rect title = new Rect(x, top + 8, innerWidth - 22, 12);
        Rect close = new Rect(panel.right() - 22, top + 8, 10, 10);
        Rect pool = new Rect(x, title.bottom() + 6, innerWidth, 22);
        Rect info = new Rect(x, pool.bottom() + 6, innerWidth, height <= 300 ? 30 : 40);
        Rect description = new Rect(x, info.bottom() + 6, innerWidth, height <= 300 ? 27 : 56);
        Rect preview = new Rect(x, description.bottom() + 6, innerWidth, footerY - 6 - description.bottom() - 6);
        int singleWidth = narrow ? 70 : 96, batchWidth = singleWidth;
        int animationWidth = narrow ? innerWidth : 164;
        int countWidth = innerWidth - singleWidth - batchWidth - (narrow ? 12 : animationWidth + 18);
        Rect single = new Rect(x, footerY, singleWidth, 22);
        Rect count = new Rect(single.right() + 6, footerY, countWidth, 22);
        Rect batch = new Rect(count.right() + 6, footerY, batchWidth, 22);
        Rect animation = new Rect(narrow ? x : batch.right() + 6, narrow ? footerY + 28 : footerY, animationWidth, 22);
        return new LotteryLayout(panel,title,pool,info,description,preview,single,count,batch,animation,close);
    }
    public Rect panel() { return panel; }
    public Rect title() { return title; }
    public Rect pool() { return pool; }
    public Rect info() { return info; }
    public Rect description() { return description; }
    public Rect preview() { return preview; }
    public Rect single() { return single; }
    public Rect count() { return count; }
    public Rect batch() { return batch; }
    public Rect animation() { return animation; }
    public Rect close() { return close; }
}
