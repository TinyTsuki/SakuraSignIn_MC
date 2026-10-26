package xin.vanilla.sakura.client.gui.lottery;

public final class LotteryRevealLayout {
    public static final int ROW_HEIGHT = 60;
    private final LotteryLayout.Rect panel, title, content, label, action;
    private LotteryRevealLayout(int width, int height) {
        int w = Math.max(1, Math.min(540, width - 20)), h = Math.max(100, Math.min(325, height - 20));
        panel = new LotteryLayout.Rect((width-w)/2,(height-h)/2,w,h);
        title = new LotteryLayout.Rect(panel.x()+38,panel.y()+8,Math.max(1,w-78),14);
        content = new LotteryLayout.Rect(panel.x(),panel.y()+62,w,Math.max(1,h-97));
        label = new LotteryLayout.Rect(panel.x()+9,panel.bottom()-23,Math.max(1,w-131),12);
        action = new LotteryLayout.Rect(panel.right()-113,panel.bottom()-29,104,22);
    }
    public static LotteryRevealLayout calculate(int width, int height) { return new LotteryRevealLayout(width,height); }
    public LotteryLayout.Rect panel() { return panel; }
    public LotteryLayout.Rect title() { return title; }
    public LotteryLayout.Rect content() { return content; }
    public LotteryLayout.Rect label() { return label; }
    public LotteryLayout.Rect action() { return action; }
    public LotteryLayout.Rect brand() { return new LotteryLayout.Rect(panel.x()+9,panel.y()+9,22,22); }
    public LotteryLayout.Rect subtitle() { return new LotteryLayout.Rect(title.x(),panel.y()+26,title.width(),10); }
    public LotteryLayout.Rect close() { return new LotteryLayout.Rect(panel.right()-31,panel.y()+9,22,22); }
    public LotteryLayout.Rect section() { return new LotteryLayout.Rect(panel.x()+9,panel.y()+46,Math.max(1,panel.width()-128),12); }
    public LotteryLayout.Rect count() { return new LotteryLayout.Rect(panel.right()-113,panel.y()+46,104,12); }
    public LotteryLayout.Rect animation() { return new LotteryLayout.Rect(content.x()+8,content.y()+8,Math.max(1,content.width()-16),Math.max(1,content.height()-16)); }
    public LotteryLayout.Rect single() {
        int w=Math.min(260,Math.max(1,content.width()-16)),h=Math.min(72,Math.max(1,content.height()-16));
        return new LotteryLayout.Rect(content.x()+(content.width()-w)/2,content.y()+(content.height()-h)/2,w,h);
    }
    public LotteryLayout.Rect card(int index) {
        int step=Math.max(1,(content.width()-19)/columns());
        return new LotteryLayout.Rect(content.x()+8+index%columns()*step,content.y()+8+index/columns()*ROW_HEIGHT,
                Math.max(1,step-4),Math.min(ROW_HEIGHT-4,Math.max(1,content.height()-12)));
    }
    public int visibleRows() { return Math.max(1,(content.height()-12)/ROW_HEIGHT); }
    public static int resultColumns(double width, int count) { return count == 1 || width < 240 ? 1 : width < 360 ? 2 : 3; }
    public int columns() { return resultColumns(content.width(),0); }
    public int maxScrollRows(int count) { return Math.max(0,(count+columns()-1)/columns()-visibleRows()); }
}
