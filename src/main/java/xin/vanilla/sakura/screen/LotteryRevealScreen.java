package xin.vanilla.sakura.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.*;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.*;
import xin.vanilla.banira.client.enums.EnumOrientation;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.lottery.LotteryLayout;
import xin.vanilla.sakura.client.gui.lottery.LotteryRevealLayout;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.network.packet.LotteryClaimPacket;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.reward.Reward;

import java.util.ArrayList;
import java.util.List;

public final class LotteryRevealScreen extends BaniraScreen {
    private final LotteryRevealPacket packet;
    private final long openedAt = System.nanoTime();
    private final LotteryAnimationStyle style = ClientConfig.get().display().lotteryAnimationStyle();
    private LotteryRevealLayout layout;
    private ButtonWidget actionButton;
    private ScrollbarWidget resultScrollbar;
    private int resultScrollRows;
    private boolean skipped, claimRequested;

    public LotteryRevealScreen(Screen parent, LotteryRevealPacket packet) {
        super(SakuraComponent.get().transClient("word", "lottery_result"));
        this.packet = packet;
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    protected void initWidgets() {
        layout = LotteryRevealLayout.calculate(width,height);
        actionButton = new ButtonWidget(this);
        actionButton.bounds(bounds(layout.action()));
        actionButton.onClick(button -> {
            if (revealed()) onClose();
            else { skipped = true; requestClaim(); }
        });
        addWidget(actionButton);
        resultScrollbar = new ScrollbarWidget(this);
        resultScrollbar.orientation(EnumOrientation.VERTICAL).minValue(0).scrollStep(1).scrollOnFocus(false)
                .onValueChanged(value -> resultScrollRows = (int) Math.round(value));
        resultScrollbar.visible(false);
        addWidget(resultScrollbar);
    }

    @Override
    protected void onRender(GuiGraphics stack, int mouseX, int mouseY, float partialTicks) {
        LotteryLayout.Rect panel = layout.panel(), content = layout.content();
        shape(stack,panel.x(),panel.y(),panel.width(),panel.height(),getEffectiveTheme().panelBg(),8,0);
        fitted(stack,packet.getPoolName(),layout.title());
        long elapsed = (System.nanoTime()-openedAt)/1_000_000;
        boolean revealed = revealed(elapsed);
        if (revealed) requestClaim();
        actionButton.text(SakuraComponent.get().transClient("word",revealed ? "confirm" : "lottery_skip_animation"));
        resultScrollbar.visible(false);
        AbstractGuiUtils.pushScissor(content.x(),content.y(),content.width(),content.height());
        try {
            if (revealed) renderResults(stack);
            else switch (style) {
                case CARDS: renderCards(stack,elapsed); break;
                case ROULETTE: renderRoulette(stack,elapsed); break;
                case INSTANT: renderResults(stack); break;
                default: renderStrip(stack,elapsed); break;
            }
        } finally { AbstractGuiUtils.popScissor(); }
        if (revealed) {
            List<Reward> winners = packet.getWinners();
            String label = winners.size()==1 ? I18n.get("format.sakura_sign_in.lottery_won_s",
                    SakuraRewardClient.displayName(winners.get(0),Minecraft.getInstance().options.languageCode,true).toString())
                    : I18n.get("format.sakura_sign_in.lottery_won_count_s",winners.size());
            fitted(stack,label,layout.label());
        }
        renderWidgets(stack,partialTicks);
    }

    private void renderStrip(GuiGraphics stack,long elapsed) {
        LotteryLayout.Rect area=layout.content();
        int winnerIndex=43,spacing=38,center=area.x()+area.width()/2;
        List<Reward> sequence=sequence(48,winnerIndex);
        double eased=1D-Math.pow(1D-Math.min(1D,elapsed/6500D),5D);
        double offset=eased*winnerIndex*spacing;
        int y=area.y()+area.height()/2-8;
        for(int i=0;i<sequence.size();i++) {
            int x=(int)Math.round(center+i*spacing-offset-8);
            if(x>=area.x() && x+16<=area.right()) renderCandidate(stack,sequence.get(i),x,y);
        }
        shape(stack,center-13,y-8,26,32,getEffectiveTheme().buttonBorderHover(),3,2);
    }

    private void renderCards(GuiGraphics stack,long elapsed) {
        LotteryLayout.Rect area=layout.content();
        int count=Math.min(7,Math.max(1,area.width()/42));
        List<Reward> values=sequence(count,count-1);
        int startX=area.x()+(area.width()-count*42+8)/2, y=area.y()+area.height()/2-27;
        int active=Math.min(count-1,(int)(elapsed/520L));
        for(int i=0;i<count;i++) {
            int x=startX+i*42;
            shape(stack,x,y,34,54,i<=active ? getEffectiveTheme().buttonBgHover():getEffectiveTheme().buttonBg(),5,1);
            if(i<=active) renderCandidate(stack,values.get(i),x+9,y+19);
        }
    }

    private void renderRoulette(GuiGraphics stack,long elapsed) {
        LotteryLayout.Rect area=layout.content();
        int count=Math.max(6,Math.min(12,packet.getPreview().size())),winner=count-1;
        List<Reward> values=sequence(count,winner);
        double progress=Math.min(1D,elapsed/6500D),eased=1D-Math.pow(1D-progress,4D);
        int active=(int)Math.floor(eased*(count*7+winner))%count;
        if(progress>=1D) active=winner;
        int centerX=area.x()+area.width()/2,centerY=area.y()+area.height()/2;
        double radiusX=Math.min(78,(area.width()-24)/2D),radiusY=Math.min(46,(area.height()-24)/2D);
        for(int i=0;i<count;i++) {
            double angle=Math.PI*2D*i/count-Math.PI/2D;
            int x=centerX+(int)Math.round(Math.cos(angle)*radiusX)-8,y=centerY+(int)Math.round(Math.sin(angle)*radiusY)-8;
            if(i==active) shape(stack,x-4,y-4,24,24,getEffectiveTheme().buttonBorderHover(),4,2);
            renderCandidate(stack,values.get(i),x,y);
        }
    }

    private void renderResults(GuiGraphics stack) {
        List<Reward> winners=packet.getWinners();
        LotteryLayout.Rect area=layout.content();
        int columns=Math.min(layout.columns(),Math.max(1,winners.size())),max=layout.maxScrollRows(winners.size());
        resultScrollRows=Math.max(0,Math.min(max,resultScrollRows));
        resultScrollbar.bounds(new ScreenCoordinate(area.right()-5,area.y(),5,area.height()));
        resultScrollbar.maxValue(max).visibleSize(layout.visibleRows()).visible(max>0);
        resultScrollbar.setValue(resultScrollRows);
        int start=resultScrollRows*columns,end=Math.min(winners.size(),start+layout.visibleRows()*columns);
        int startX=area.x()+(area.width()-7-columns*22)/2;
        for(int i=start;i<end;i++) {
            int local=i-start,x=startX+local%columns*22,y=area.y()+local/columns*22+2;
            renderReward(stack,winners.get(i),x,y);
            double mx=inputState.mouseX(),my=inputState.mouseY();
            if(area.contains(mx,my) && mx>=x && mx<x+16 && my>=y && my<y+16)
                tooltip(SakuraRewardClient.displayName(winners.get(i),Minecraft.getInstance().options.languageCode,true).toString());
        }
    }

    @Override protected void onMouseScrolled(MouseScrolledHandleArgs event) {
        if(revealed() && layout.content().contains(event.mouseX(),event.mouseY()) && resultScrollbar.visible()) {
            resultScrollRows=Math.max(0,Math.min(layout.maxScrollRows(packet.getWinners().size()),resultScrollRows-(int)Math.signum(event.delta())));
            event.consumed(true);
        }
    }
    private List<Reward> sequence(int size,int winnerIndex) {
        List<Reward> source=packet.getPreview(),result=new ArrayList<>();
        for(int i=0;i<size;i++) result.add(source.isEmpty()?null:source.get(i%source.size()));
        if(winnerIndex>=0 && winnerIndex<result.size()) result.set(winnerIndex,packet.getWinner());
        return result;
    }
    private void renderCandidate(GuiGraphics stack,Reward reward,int x,int y) {
        if(!packet.isPreviewVisible() || reward==null) {
            shape(stack,x,y,16,16,getEffectiveTheme().buttonBgHover(),3,1);
            stack.drawString(font,"?",x+5,y+4,getEffectiveTheme().textPrimary(),false);
        } else renderReward(stack,reward,x,y);
    }
    private void renderReward(GuiGraphics stack,Reward reward,int x,int y) {
        RewardRenderer.renderCustomReward(stack,font,SakuraClientState.getThemeTexture(),
                SakuraClientState.getThemeTextureCoordinate(),reward,x,y,true,false);
    }
    private boolean revealed() { return revealed((System.nanoTime()-openedAt)/1_000_000); }
    private boolean revealed(long elapsed) { return skipped || elapsed >= (style==LotteryAnimationStyle.INSTANT?0:style==LotteryAnimationStyle.CARDS?4800:6800); }
    @Override protected void onKeyPressed(KeyPressedHandleArgs event) {
        if(event.key()==GLFWKey.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(event.key(), event.scanCode())) {
            if(revealed()) onClose(); else { skipped=true;requestClaim(); }
            event.consumed(true);return;
        }
        super.onKeyPressed(event);
    }
    private void requestClaim() {
        if(claimRequested) return;
        claimRequested=true;
        SakuraNetwork.sendToServer(new LotteryClaimPacket(packet.getOperationId(),packet.getToken()));
    }
    @Override public void onClose() {
        if(!revealed()) { skipped=true;requestClaim();return; }
        requestClaim();super.onClose();
    }
    @Override protected ScreenCoordinate closeableWindowBounds() { return bounds(layout.panel()); }
    private void fitted(GuiGraphics stack,String text,LotteryLayout.Rect area) {
        String fitted=font.plainSubstrByWidth(text,area.width());
        stack.drawString(font,fitted,(int)(area.x()+(area.width()-font.width(fitted))/2F),area.y(),getEffectiveTheme().textPrimary(),false);
        if(!fitted.equals(text) && area.contains(inputState.mouseX(),inputState.mouseY())) tooltip(text);
    }
    private void tooltip(String text) {
        int x=(int)inputState.mouseX(),y=(int)inputState.mouseY();
        addDeferredTooltipRender(stack -> TooltipWidget.drawPopupMessage(stack.pose(),
                FontDrawArgs.ofPopo(Text.literal(text).stack(stack.pose())).x(x).y(y),getEffectiveTheme(),season()));
    }
    private static ScreenCoordinate bounds(LotteryLayout.Rect area) { return new ScreenCoordinate(area.x(),area.y(),area.width(),area.height()); }
    private void shape(GuiGraphics stack,int x,int y,int width,int height,int color,int radius,int border) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(stack.pose()).type(ShapeDrawArgs.ShapeType.RECT).color(color)
                .rect(new ShapeDrawArgs.RectParams().x(x).y(y).width(width).height(height).radius(radius)
                        .cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE).border(border)));
    }
}
