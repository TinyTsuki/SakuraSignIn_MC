package xin.vanilla.sakura.client.gui.lottery;

public final class LotteryRevealLayout {
    private final LotteryLayout.Rect panel, title, content, label, action;
    private LotteryRevealLayout(int width, int height) {
        int w = Math.min(560, width - 24), h = Math.min(280, height - 16);
        panel = new LotteryLayout.Rect((width-w)/2,(height-h)/2,w,h);
        title = new LotteryLayout.Rect(panel.x()+12,panel.y()+12,w-24,14);
        content = new LotteryLayout.Rect(panel.x()+12,panel.y()+36,w-24,h-92);
        label = new LotteryLayout.Rect(content.x(),content.bottom()+6,content.width(),12);
        action = new LotteryLayout.Rect(width/2-70,panel.bottom()-30,140,20);
    }
    public static LotteryRevealLayout calculate(int width, int height) { return new LotteryRevealLayout(width,height); }
    public LotteryLayout.Rect panel() { return panel; }
    public LotteryLayout.Rect title() { return title; }
    public LotteryLayout.Rect content() { return content; }
    public LotteryLayout.Rect label() { return label; }
    public LotteryLayout.Rect action() { return action; }
    public int visibleRows() { return Math.max(1,content.height()/22); }
    public int columns() { return Math.max(1,(content.width()-7)/22); }
    public int maxScrollRows(int count) { return Math.max(0,(count+columns()-1)/columns()-visibleRows()); }
}
