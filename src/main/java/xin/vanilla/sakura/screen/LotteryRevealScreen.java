package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.GLFWKey;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.enums.EnumOrientation;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.gui.widget.ScrollbarWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.lottery.LotteryAnimationFrame;
import xin.vanilla.sakura.client.gui.lottery.LotteryLayout;
import xin.vanilla.sakura.client.gui.lottery.LotteryMotion;
import xin.vanilla.sakura.client.gui.lottery.LotteryRevealLayout;
import xin.vanilla.sakura.client.ui.LotteryRevealUiSession;
import xin.vanilla.sakura.client.ui.SakuraUiPages;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryClaimPacket;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.reward.Reward;

import java.util.ArrayList;
import java.util.List;

public final class LotteryRevealScreen extends BaniraScreen implements UiPageSource {
    private final LotteryRevealPacket packet;
    private final LotteryAnimationStyle style;
    private final Object uiConnection = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getConnection();
    private final LotteryRevealUiSession revealSession;
    private UiPageState revealPageState;
    private LotteryRevealLayout layout;
    private ButtonWidget actionButton;
    private ScrollbarWidget resultScrollbar;
    private int resultScrollRows;

    public LotteryRevealScreen(Screen parent, LotteryRevealPacket packet) {
        super(SakuraComponent.get().transClient("word", "lottery_result"));
        this.packet = packet;
        this.style = (packet.getAnimationStyle() == null ? ClientConfig.get().display().lotteryAnimationStyle()
                : packet.getAnimationStyle()).presentationStyle();
        revealSession = new LotteryRevealUiSession(packet, style, () -> System.nanoTime() / 1_000_000,
                () -> uiConnection == Minecraft.getInstance().getConnection(),
                () -> Minecraft.getInstance().options.keyInventory.getKey().getValue(),
                () -> SakuraNetwork.sendToServer(new LotteryClaimPacket(packet.getOperationId(), packet.getToken())));
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    public String uiSurfaceId() {
        return SakuraSignIn.MODID + ":lottery-result";
    }

    @Override
    public UiPageSession pageSession() {
        return revealSession;
    }

    @Override
    public UiPageSpec uiPage() {
        return SakuraUiPages.page("lottery-result", this, revealSession);
    }

    @Override
    protected void onRemoved() {
        BaniraUi.screenRemoved(this);
        super.onRemoved();
    }

    @Override
    public void capturePageState(UiPageState state) {
        revealPageState = state;
        if (state != null && layout != null)
            state.rowPosition(resultScrollRows * Math.min(layout.columns(), Math.max(1, packet.getWinners().size())));
    }

    @Override
    public void preparePageState(UiPageState state) {
        revealPageState = state;
    }

    @Override
    protected void initWidgets() {
        layout = LotteryRevealLayout.calculate(width, height);
        if (revealPageState != null)
            resultScrollRows = (int) revealPageState.rowPosition() / Math.min(layout.columns(), Math.max(1, packet.getWinners().size()));
        actionButton = new ButtonWidget(this);
        actionButton.bounds(bounds(layout.action()));
        actionButton.onClick(button -> {
            if (revealed()) onClose();
            else revealSession.skip();
        });
        addWidget(actionButton);
        resultScrollbar = new ScrollbarWidget(this);
        resultScrollbar.orientation(EnumOrientation.VERTICAL).minValue(0).scrollStep(1).scrollOnFocus(false)
                .onValueChanged(value -> resultScrollRows = (int) Math.round(value));
        resultScrollbar.visible(false);
        addWidget(resultScrollbar);
    }

    @Override
    protected void onRender(PoseStack stack, float partialTicks) {
        int mouseX = (int) inputState.mouseX(), mouseY = (int) inputState.mouseY();
        LotteryLayout.Rect panel = layout.panel(), content = layout.content();
        shape(stack, panel.x(), panel.y(), panel.width(), panel.height(), getEffectiveTheme().panelBg(), 8, 0);
        fitted(stack, packet.getPoolName(), layout.title());
        long elapsed = revealSession.elapsed();
        boolean revealed = revealed();
        if (revealed) requestClaim();
        actionButton.text(SakuraComponent.get().transClient("word", revealed ? "confirm" : "lottery_skip_animation"));
        resultScrollbar.visible(false);
        AbstractGuiUtils.pushScissor(content.x(), content.y(), content.width(), content.height());
        try {
            if (revealed) renderResults(stack);
            else if (style.illustrated()) renderMotion(stack, elapsed);
            else switch (style) {
                    case CARDS:
                        renderCards(stack, elapsed);
                        break;
                    case ROULETTE:
                        renderRoulette(stack, elapsed);
                        break;
                    case INSTANT:
                        renderResults(stack);
                        break;
                    default:
                        renderStrip(stack, elapsed);
                        break;
                }
        } finally {
            AbstractGuiUtils.popScissor();
        }
        if (revealed) {
            List<Reward> winners = packet.getWinners();
            String label = winners.size() == 1 ? I18n.get("format.sakura_sign_in.lottery_won_s",
                    SakuraRewardClient.displayName(winners.get(0), Minecraft.getInstance().options.languageCode, true).toString())
                    : I18n.get("format.sakura_sign_in.lottery_won_count_s", winners.size());
            fitted(stack, label, layout.label());
        }
        renderWidgets(stack, partialTicks);
    }

    private void renderMotion(PoseStack stack, long elapsed) {
        var area = layout.content();
        var frame = LotteryMotion.frame(style, elapsed, packet.getWinners().size(), packet.getPreview().size(),
                packet.getOperationId().hashCode(), area.width(), area.height());
        double scale = Math.min(area.width() / 320D, area.height() / 180D);
        int cx = area.x() + area.width() / 2, base = area.y() + area.height() / 2;
        boolean shell = style == LotteryAnimationStyle.ENVELOPE;
        if (shell) {
            shape(stack, cx - (int) (55 * scale), base, (int) (110 * scale), (int) (65 * scale), getEffectiveTheme().buttonBg(), 4, 1);
        }
        var winners = packet.getWinners();
        var candidates = packet.getPreview();
        for (var card : frame.cards()) {
            int x = area.x() + (int) card.x(), y = area.y() + (int) card.y();
            shape(stack, x, y, Math.max(1, (int) card.width()), Math.max(1, (int) card.height()),
                    card.matched() ? getEffectiveTheme().buttonBgHover() : getEffectiveTheme().buttonBg(), 3, 1);
            if (!card.face()) {
                if (card.width() > 10)
                    xin.vanilla.sakura.client.LegacyGuiText.draw(stack, font, "?", x + (int) card.width() / 2 - 2, y + (int) card.height() / 2 - 4,
                            getEffectiveTheme().textPrimary(), false);
                continue;
            }
            Reward reward = card.reward() >= 0 ? winners.get(card.reward())
                    : candidates.isEmpty() ? null : candidates.get(Math.floorMod(-1 - card.reward(), candidates.size()));
            if (reward != null && (card.reward() >= 0 || packet.isPreviewVisible()))
                renderReward(stack, reward, x + Math.max(0, (int) (card.width() - 16) / 2), y + Math.max(0, (int) (card.height() - 16) / 2));
        }
        if (shell)
            shape(stack, cx - (int) (55 * scale), base + (int) (25 * scale), (int) (110 * scale), (int) (40 * scale), getEffectiveTheme().buttonBg(), 4, 1);
    }

    private void renderStrip(PoseStack stack, long elapsed) {
        LotteryLayout.Rect area = layout.content();
        int center = area.x() + area.width() / 2;
        List<Reward> sequence = sequence(48, 43);
        int y = area.y() + area.height() / 2 - 8;
        for (var slot : LotteryAnimationFrame.frame(LotteryAnimationStyle.STRIP, elapsed, area.width(), area.height(), packet.getPreview().size())) {
            int x = area.x() + (int) Math.round(slot.x());
            if (x >= area.x() && x + 16 <= area.right()) renderCandidate(stack, sequence.get(slot.index()), x, y);
        }
        shape(stack, center - 13, y - 8, 26, 32, getEffectiveTheme().buttonBorderHover(), 3, 2);
    }

    private void renderCards(PoseStack stack, long elapsed) {
        LotteryLayout.Rect area = layout.content();
        int count = Math.min(7, Math.max(1, area.width() / 42));
        List<Reward> values = sequence(count, count - 1);
        int startX = area.x() + (area.width() - count * 42 + 8) / 2, y = area.y() + area.height() / 2 - 27;
        int active = Math.min(count - 1, (int) (elapsed / 520L));
        for (int i = 0; i < count; i++) {
            int x = startX + i * 42;
            shape(stack, x, y, 34, 54, i <= active ? getEffectiveTheme().buttonBgHover() : getEffectiveTheme().buttonBg(), 5, 1);
            if (i <= active) renderCandidate(stack, values.get(i), x + 9, y + 19);
        }
    }

    private void renderRoulette(PoseStack stack, long elapsed) {
        LotteryLayout.Rect area = layout.content();
        var slots = LotteryAnimationFrame.frame(LotteryAnimationStyle.ROULETTE, elapsed, area.width(), area.height(), packet.getPreview().size());
        List<Reward> values = sequence(slots.size(), slots.size() - 1);
        for (var slot : slots) {
            int x = area.x() + (int) Math.round(slot.x()), y = area.y() + (int) Math.round(slot.y());
            if (slot.active()) shape(stack, x - 3, y - 3, 22, 22, getEffectiveTheme().buttonBorderHover(), 4, 2);
            renderCandidate(stack, values.get(slot.index()), x, y);
        }
    }

    private void renderResults(PoseStack stack) {
        List<Reward> winners = packet.getWinners();
        LotteryLayout.Rect area = layout.content();
        int columns = Math.min(layout.columns(), Math.max(1, winners.size())), max = layout.maxScrollRows(winners.size());
        resultScrollRows = Math.max(0, Math.min(max, resultScrollRows));
        resultScrollbar.bounds(new ScreenCoordinate(area.right() - 5, area.y(), 5, area.height()));
        resultScrollbar.maxValue(max).visibleSize(layout.visibleRows()).visible(max > 0);
        resultScrollbar.setValue(resultScrollRows);
        int start = resultScrollRows * columns, end = Math.min(winners.size(), start + layout.visibleRows() * columns);
        int startX = area.x() + (area.width() - 7 - columns * 22) / 2;
        for (int i = start; i < end; i++) {
            int local = i - start, x = startX + local % columns * 22, y = area.y() + local / columns * 22 + 2;
            renderReward(stack, winners.get(i), x, y);
            double mx = inputState.mouseX(), my = inputState.mouseY();
            if (area.contains(mx, my) && mx >= x && mx < x + 16 && my >= y && my < y + 16)
                tooltip(SakuraRewardClient.displayName(winners.get(i), Minecraft.getInstance().options.languageCode, true).toString());
        }
    }

    @Override
    protected void onMouseScrolled(MouseScrolledHandleArgs event) {
        if (revealed() && layout.content().contains(event.mouseX(), event.mouseY()) && resultScrollbar.visible()) {
            resultScrollRows = Math.max(0, Math.min(layout.maxScrollRows(packet.getWinners().size()), resultScrollRows - (int) Math.signum(event.delta())));
            event.consumed(true);
        }
    }

    private List<Reward> sequence(int size, int winnerIndex) {
        List<Reward> source = packet.getPreview(), result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add(source.isEmpty() ? null : source.get(i % source.size()));
        if (winnerIndex >= 0 && winnerIndex < result.size()) result.set(winnerIndex, packet.getWinner());
        return result;
    }

    private void renderCandidate(PoseStack stack, Reward reward, int x, int y) {
        if (!packet.isPreviewVisible() || reward == null) {
            shape(stack, x, y, 16, 16, getEffectiveTheme().buttonBgHover(), 3, 1);
            xin.vanilla.sakura.client.LegacyGuiText.draw(stack, font, "?", x + 5, y + 4, getEffectiveTheme().textPrimary(), false);
        } else renderReward(stack, reward, x, y);
    }

    private void renderReward(PoseStack stack, Reward reward, int x, int y) {
        RewardRenderer.renderCustomReward(stack, font, SakuraClientState.getThemeTexture(),
                SakuraClientState.getThemeTextureCoordinate(), reward, x, y, true, false);
    }

    private boolean revealed() {
        return revealSession.revealed();
    }

    @Override
    protected void onKeyPressed(KeyPressedHandleArgs event) {
        if (event.key() == GLFWKey.GLFW_KEY_ESCAPE || event.key() == Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            if (revealed()) onClose();
            else revealSession.skip();
            event.consumed(true);
            return;
        }
        super.onKeyPressed(event);
    }

    private void requestClaim() {
        revealSession.requestClaim();
    }

    @Override
    public void onClose() {
        if (!revealed()) {
            revealSession.skip();
            return;
        }
        if (BaniraUi.closePage(this, UiCloseReason.USER_CLOSE)) return;
        if (revealSession.requestClose()) return;
        requestClaim();
        super.onClose();
    }

    @Override
    protected ScreenCoordinate closeableWindowBounds() {
        return bounds(layout.panel());
    }

    private void fitted(PoseStack stack, String text, LotteryLayout.Rect area) {
        String fitted = font.plainSubstrByWidth(text, area.width());
        xin.vanilla.sakura.client.LegacyGuiText.draw(stack, font, fitted, (int) (area.x() + (area.width() - font.width(fitted)) / 2F), area.y(), getEffectiveTheme().textPrimary(), false);
        if (!fitted.equals(text) && area.contains(inputState.mouseX(), inputState.mouseY())) tooltip(text);
    }

    private void tooltip(String text) {
        int x = (int) inputState.mouseX(), y = (int) inputState.mouseY();
        addDeferredTooltipRender(stack -> TooltipWidget.drawPopupMessage(stack,
                FontDrawArgs.ofPopo(Text.literal(text).stack(stack)).x(x).y(y), getEffectiveTheme(), season()));
    }

    private static ScreenCoordinate bounds(LotteryLayout.Rect area) {
        return new ScreenCoordinate(area.x(), area.y(), area.width(), area.height());
    }

    private void shape(PoseStack stack, int x, int y, int width, int height, int color, int radius, int border) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(stack).type(ShapeDrawArgs.ShapeType.RECT).color(color)
                .rect(new ShapeDrawArgs.RectParams().x(x).y(y).width(width).height(height).radius(radius)
                        .cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE).border(border)));
    }
}
