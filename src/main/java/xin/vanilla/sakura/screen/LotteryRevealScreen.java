package xin.vanilla.sakura.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.sakura.client.ui.LotteryRevealUiSession;
import xin.vanilla.sakura.client.ui.SakuraUiPages;
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

public final class LotteryRevealScreen extends BaniraScreen implements UiPageSource {
    private final LotteryRevealPacket packet;
    private final LotteryAnimationStyle style = ClientConfig.get().display().lotteryAnimationStyle();
    private final Object uiConnection = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getConnection();
    private final LotteryRevealUiSession revealSession;
    private UiPageState revealPageState;
    private LotteryRevealLayout layout;
    private ButtonWidget actionButton, closeButton;
    private ScrollbarWidget resultScrollbar;
    private int resultScrollRows;

    public LotteryRevealScreen(Screen parent, LotteryRevealPacket packet) {
        super(SakuraComponent.get().transClient("word", "lottery_result"));
        this.packet = packet;
        revealSession = new LotteryRevealUiSession(packet, style, () -> System.nanoTime() / 1_000_000,
                () -> uiConnection == Minecraft.getInstance().getConnection(),
                () -> Minecraft.getInstance().options.keyInventory.getKey().getValue(),
                () -> SakuraNetwork.sendToServer(new LotteryClaimPacket(packet.getOperationId(), packet.getToken())));
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }
    @Override public String uiSurfaceId() { return SakuraSignIn.MODID + ":lottery-result"; }
    @Override public UiPageSession pageSession() { return revealSession; }
    @Override public UiPageSpec uiPage() { return SakuraUiPages.page("lottery-result", this, revealSession); }
    @Override protected void onRemoved() { BaniraUi.screenRemoved(this); super.onRemoved(); }
    @Override public void capturePageState(UiPageState state) {
        revealPageState = state;
        if (state != null && layout != null) state.rowPosition(resultScrollRows * Math.min(layout.columns(), Math.max(1, packet.getWinners().size())));
    }
    @Override public void preparePageState(UiPageState state) { revealPageState = state; }

    @Override
    protected void initWidgets() {
        layout = LotteryRevealLayout.calculate(width,height);
        if (revealPageState != null) resultScrollRows = (int) revealPageState.rowPosition() / Math.min(layout.columns(), Math.max(1, packet.getWinners().size()));
        actionButton = new ButtonWidget(this);
        actionButton.bounds(bounds(layout.action()));
        actionButton.onClick(button -> {
            if (revealed()) onClose();
            else revealSession.skip();
        });
        addWidget(actionButton);
        closeButton = new ButtonWidget(this);
        closeButton.bounds(bounds(layout.close()));
        closeButton.presetStyle(ButtonWidget.PresetStyle.CLOSE);
        closeButton.onClick(button -> finish());
        addWidget(closeButton);
        resultScrollbar = new ScrollbarWidget(this);
        resultScrollbar.orientation(EnumOrientation.VERTICAL).minValue(0).scrollStep(1).scrollOnFocus(false)
                .onValueChanged(value -> resultScrollRows = (int) Math.round(value));
        resultScrollbar.visible(false);
        addWidget(resultScrollbar);
    }

    @Override
    protected void onRender(GuiGraphics stack, int mouseX, int mouseY, float partialTicks) {
        LotteryLayout.Rect panel = layout.panel(), content = layout.content();
        shape(stack,panel.x(),panel.y(),panel.width(),panel.height(),getEffectiveTheme().panelBg(),4,0);
        var brand = layout.brand();
        xin.vanilla.sakura.client.theme.SakuraThemeTextures.get("icon.sign_in").draw(stack.pose(),brand.x(),brand.y(),brand.width(),brand.height());
        fitted(stack,packet.getPoolName(),layout.title());
        fitted(stack,"SAKURA SIGN-IN",layout.subtitle());
        long elapsed = revealSession.elapsed();
        boolean revealed = revealed();
        if (revealed) requestClaim();
        actionButton.enabled(revealSession.connected()); closeButton.enabled(revealSession.connected());
        actionButton.text(SakuraComponent.get().transClient("word",revealed ? "confirm" : "lottery_skip_animation"));
        fitted(stack,I18n.get("word.sakura_sign_in."+(revealed ? "aui_lottery_rewards" : "lottery_drawing")),layout.section());
        fitted(stack,I18n.get("format.sakura_sign_in.aui_lottery_result_count_s",packet.getWinners().size()),layout.count());
        if (revealSession.connected() && layout.close().contains(mouseX,mouseY)) tooltip(xin.vanilla.banira.BaniraComponent.get().transClientAuto("config_editor_close").toString());
        resultScrollbar.visible(false);
        AbstractGuiUtils.pushScissor(content.x(),content.y(),content.width(),content.height());
        try {
            if (revealed) renderResults(stack);
            else renderAnimation(stack,elapsed);
            if (!revealSession.connected()) fitted(stack,I18n.get("word.sakura_sign_in.aui_lottery_disconnected"),
                    new LotteryLayout.Rect(content.x()+8,content.y(),Math.max(1,content.width()-16),12));
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

    private void renderAnimation(GuiGraphics stack,long elapsed) {
        LotteryLayout.Rect area=layout.animation();
        var slots=LotteryRevealUiSession.presentationFrame(style,elapsed,area.width(),area.height(),packet.getPreview().size());
        List<Reward> values=sequence(slots.size(),style==LotteryAnimationStyle.STRIP?43:slots.size()-1);
        for(var slot:slots) {
            int x=area.x()+(int)Math.round(slot.x()),y=area.y()+(int)Math.round(slot.y());
            if(x<area.x() || y<area.y() || x+16>area.right() || y+16>area.bottom()) continue;
            surface(stack,new LotteryLayout.Rect(x-4,y-8,24,32),slot.face()?getEffectiveTheme().buttonBg():getEffectiveTheme().bgTertiary());
            if(slot.active()) shape(stack,x-5,y-9,26,34,getEffectiveTheme().buttonBorderHover(),3,1);
            if(slot.face()) renderCandidate(stack,values.get(slot.index()),x,y);
            else stack.drawString(font,"?",x+5,y+4,getEffectiveTheme().textPrimary(),false);
        }
        if(style==LotteryAnimationStyle.STRIP) shape(stack,area.x()+area.width()/2-16,area.y()+area.height()/2-20,32,40,getEffectiveTheme().buttonBorderHover(),3,1);
    }

    private void renderResults(GuiGraphics stack) {
        List<Reward> winners=packet.getWinners();
        LotteryLayout.Rect area=layout.content();
        if(winners.size()==1) {
            resultScrollRows=0;
            var card=layout.single();surface(stack,card,getEffectiveTheme().buttonBg());
            int size=Math.min(32,Math.max(16,card.height()-24));
            renderIcon(stack,winners.get(0),card.x()+12,card.y()+(card.height()-size)/2,size);
            rewardText(stack,winners.get(0),card,card.x()+size+22);
            return;
        }
        int columns=layout.columns(),max=layout.maxScrollRows(winners.size());
        resultScrollRows=Math.max(0,Math.min(max,resultScrollRows));
        resultScrollbar.bounds(new ScreenCoordinate(area.right()-5,area.y(),5,area.height()));
        resultScrollbar.maxValue(max).visibleSize(layout.visibleRows()).visible(max>0);
        resultScrollbar.setValue(resultScrollRows);
        int start=resultScrollRows*columns,end=Math.min(winners.size(),start+(layout.visibleRows()+1)*columns);
        for(int i=start;i<end;i++) {
            var card=layout.card(i-start);Reward reward=winners.get(i);
            surface(stack,card,getEffectiveTheme().buttonBg());
            renderIcon(stack,reward,card.x()+6,card.y()+18,20);
            rewardText(stack,reward,card,card.x()+34);
            String number=String.format(java.util.Locale.ROOT,"%02d",i+1);
            stack.drawString(font,number,card.right()-6-font.width(number),card.y()+4,getEffectiveTheme().textSecondary(),false);
        }
    }

    private void surface(GuiGraphics stack,LotteryLayout.Rect rect,int color) {
        shape(stack,rect.x(),rect.y(),rect.width(),rect.height(),color,3,0);
        shape(stack,rect.x(),rect.y(),rect.width(),rect.height(),getEffectiveTheme().border(),3,1);
    }
    private void renderIcon(GuiGraphics stack,Reward reward,int x,int y,int size) {
        stack.pose().pushPose();
        try { stack.pose().translate(x,y,0);stack.pose().scale(size/16F,size/16F,1);renderReward(stack,reward,0,0); }
        finally { stack.pose().popPose(); }
    }
    private void rewardText(GuiGraphics stack,Reward reward,LotteryLayout.Rect card,int x) {
        String name=SakuraRewardClient.displayName(reward,Minecraft.getInstance().options.languageCode,true).toString();
        int width=Math.max(1,card.right()-x-22);
        var lines=font.split(net.minecraft.network.chat.Component.literal(name),width);
        for(int i=0;i<Math.min(2,lines.size());i++) stack.drawString(font,lines.get(i),x,card.y()+12+i*font.lineHeight,getEffectiveTheme().textPrimary(),false);
        String type=LotteryRevealUiSession.typeName(reward);
        stack.drawString(font,font.plainSubstrByWidth(type,Math.max(1,card.right()-x-6)),x,card.bottom()-14,getEffectiveTheme().textSecondary(),false);
        if(layout.content().contains(inputState.mouseX(),inputState.mouseY()) && card.contains(inputState.mouseX(),inputState.mouseY())) tooltip(name+"\n"+type);
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
                SakuraClientState.getThemeTextureCoordinate(),reward,x,y,false,false);
    }
    private boolean revealed() { return revealSession.revealed(); }
    @Override protected void onKeyPressed(KeyPressedHandleArgs event) {
        if(!revealSession.connected()) return;
        if(event.key()==GLFWKey.GLFW_KEY_ESCAPE || event.key()==Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            if(revealed()) onClose(); else revealSession.skip();
            event.consumed(true);return;
        }
        super.onKeyPressed(event);
    }
    private void requestClaim() {
        revealSession.requestClaim();
    }
    @Override public void onClose() {
        if(!revealSession.connected()) return;
        if(!revealed()) { revealSession.skip();return; }
        finish();
    }
    private void finish() {
        if(!revealSession.connected()) return;
        if(BaniraUi.closePage(this, UiCloseReason.USER_CLOSE)) return;
        if(revealSession.requestClose()) return;
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
