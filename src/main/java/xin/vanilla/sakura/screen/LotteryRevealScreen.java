package xin.vanilla.sakura.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.GLFWKey;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.network.packet.LotteryClaimPacket;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.reward.Reward;

import java.util.ArrayList;
import java.util.List;

/** 动画完成前不展示服务端已确定的中奖内容。 */
public final class LotteryRevealScreen extends BaniraScreen {
    private final LotteryRevealPacket packet;
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private ButtonWidget actionButton;
    private boolean skipped;
    private boolean claimRequested;

    public LotteryRevealScreen(Screen parent, LotteryRevealPacket packet) {
        super(SakuraComponent.get().transClient("word", "lottery_result"));
        this.parent = parent;
        this.packet = packet;
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    protected void initWidgets() {
        actionButton = new ButtonWidget(this);
        actionButton.bounds(new ScreenCoordinate(width / 2 - 70, height / 2 + 92, 140, 20));
        actionButton.onClick(button -> {
            if (revealed()) {
                onClose();
            } else {
                skipped = true;
                requestClaim();
            }
        });
        addWidget(actionButton);
    }

    @Override
    protected void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelWidth = Math.min(560, width - 40);
        int panelHeight = 242;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        shape(graphics, panelX, panelY, panelWidth, panelHeight,
                getEffectiveTheme().panelBg(), 8, 0);
        centered(graphics, packet.getPoolName(), panelY + 14,
                getEffectiveTheme().textPrimary());

        LotteryAnimationStyle style = ClientConfig.get().display().lotteryAnimationStyle();
        long elapsed = System.currentTimeMillis() - openedAt;
        boolean revealed = revealed(style, elapsed);
        if (revealed) requestClaim();
        actionButton.text(SakuraComponent.get().transClient("word",
                revealed ? "confirm" : "lottery_skip_animation"));

        if (revealed) {
            renderResults(graphics, panelX, panelWidth, panelY);
        } else {
            switch (style) {
                case CARDS:
                    renderCards(graphics, panelY, elapsed);
                    break;
                case ROULETTE:
                    renderRoulette(graphics, panelY, elapsed);
                    break;
                case INSTANT:
                    renderResults(graphics, panelX, panelWidth, panelY);
                    break;
                case STRIP:
                default:
                    renderStrip(graphics, panelX, panelWidth, panelY, elapsed);
                    break;
            }
        }
        renderWidgets(graphics, partialTicks);
    }

    private void renderStrip(GuiGraphics graphics, int panelX, int panelWidth,
                             int panelY, long elapsed) {
        int winnerIndex = 43;
        List<Reward> sequence = sequence(48, winnerIndex);
        double progress = Math.min(1D, elapsed / 6500D);
        double eased = 1D - Math.pow(1D - progress, 5D);
        int spacing = 38;
        double offset = eased * winnerIndex * spacing;
        int center = panelX + panelWidth / 2;
        shape(graphics, panelX + 20, panelY + 58, panelWidth - 40, 64,
                getEffectiveTheme().buttonBg(), 5, 0);
        for (int i = 0; i < sequence.size(); i++) {
            int x = (int) Math.round(center + i * spacing - offset - 8);
            if (x >= panelX + 24 && x <= panelX + panelWidth - 40) {
                renderCandidate(graphics, sequence.get(i), x, panelY + 81);
            }
        }
        shape(graphics, center - 13, panelY + 62, 26, 56,
                getEffectiveTheme().buttonBorderHover(), 3, 2);
    }

    private void renderCards(GuiGraphics graphics, int panelY, long elapsed) {
        List<Reward> values = sequence(7, 6);
        int startX = width / 2 - 143;
        int active = Math.min(values.size() - 1, (int) (elapsed / 520L));
        for (int i = 0; i < values.size(); i++) {
            int x = startX + i * 42;
            shape(graphics, x, panelY + 62, 34, 54,
                    i <= active ? getEffectiveTheme().buttonBgHover()
                            : getEffectiveTheme().buttonBg(), 5, 1);
            if (i <= active) {
                renderCandidate(graphics, values.get(i), x + 9, panelY + 82);
            }
        }
    }

    private void renderRoulette(GuiGraphics graphics, int panelY, long elapsed) {
        int slotCount = Math.max(6, Math.min(12, packet.getPreview().size()));
        int winnerIndex = slotCount - 1;
        List<Reward> values = sequence(slotCount, winnerIndex);
        double progress = Math.min(1D, elapsed / 6500D);
        double eased = 1D - Math.pow(1D - progress, 4D);
        int active = (int) Math.floor(eased * (values.size() * 7 + winnerIndex)) % values.size();
        if (progress >= 1D) active = winnerIndex;
        int centerX = width / 2;
        int centerY = panelY + 92;
        for (int i = 0; i < values.size(); i++) {
            double angle = Math.PI * 2D * i / values.size() - Math.PI / 2D;
            int x = centerX + (int) Math.round(Math.cos(angle) * 78D) - 8;
            int y = centerY + (int) Math.round(Math.sin(angle) * 46D) - 8;
            if (i == active) {
                shape(graphics, x - 4, y - 4, 24, 24,
                        getEffectiveTheme().buttonBorderHover(), 4, 2);
            }
            renderCandidate(graphics, values.get(i), x, y);
        }
    }

    private void renderResults(GuiGraphics graphics, int panelX, int panelWidth, int panelY) {
        List<Reward> winners = packet.getWinners();
        int spacing = 18;
        int maxColumns = Math.max(1, (panelWidth - 40) / spacing);
        int columns = Math.min(maxColumns, Math.max(1, (winners.size() + 3) / 4));
        int rows = (winners.size() + columns - 1) / columns;
        int startX = width / 2 - columns * spacing / 2 + 2;
        int startY = panelY + 42 + Math.max(0, (4 - rows) * 8);
        for (int i = 0; i < winners.size(); i++) {
            int x = startX + i % columns * spacing;
            int y = startY + i / columns * spacing;
            renderReward(graphics, winners.get(i), x, y);
        }
        int labelY = Math.min(panelY + 192, startY + rows * spacing + 8);
        if (winners.size() == 1) {
            String name = SakuraRewardClient.displayName(winners.get(0),
                    Minecraft.getInstance().options.languageCode, true).toString();
            centered(graphics, I18n.get("format.sakura_sign_in.lottery_won_s", name),
                    labelY, getEffectiveTheme().textPrimary());
        } else {
            centered(graphics, I18n.get("format.sakura_sign_in.lottery_won_count_s",
                    winners.size()), labelY, getEffectiveTheme().textPrimary());
        }
    }

    private List<Reward> sequence(int size, int winnerIndex) {
        List<Reward> source = packet.getPreview();
        List<Reward> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(source.isEmpty() ? null : source.get(i % source.size()));
        }
        if (winnerIndex >= 0 && winnerIndex < result.size()) {
            result.set(winnerIndex, packet.getWinner());
        }
        return result;
    }

    private void renderCandidate(GuiGraphics graphics, Reward reward, int x, int y) {
        if (!packet.isPreviewVisible() || reward == null) {
            shape(graphics, x, y, 16, 16, getEffectiveTheme().buttonBgHover(), 3, 1);
            graphics.drawString(font, "?", x + 5, y + 4, getEffectiveTheme().textPrimary(), false);
            return;
        }
        renderReward(graphics, reward, x, y);
    }

    private void renderReward(GuiGraphics graphics, Reward reward, int x, int y) {
        RewardRenderer.renderCustomReward(graphics, font,
                SakuraClientState.getThemeTexture(),
                SakuraClientState.getThemeTextureCoordinate(), reward, x, y, true, false);
    }

    private boolean revealed() {
        LotteryAnimationStyle style = ClientConfig.get().display().lotteryAnimationStyle();
        return revealed(style, System.currentTimeMillis() - openedAt);
    }

    private boolean revealed(LotteryAnimationStyle style, long elapsed) {
        return skipped || elapsed >= revealDelay(style);
    }

    private static long revealDelay(LotteryAnimationStyle style) {
        if (style == LotteryAnimationStyle.INSTANT) {
            return 0L;
        }
        return style == LotteryAnimationStyle.CARDS ? 4800L : 6800L;
    }

    @Override
    protected void onKeyPressed(KeyPressedHandleArgs eventArgs) {
        int key = eventArgs.key();
        if (key == GLFWKey.GLFW_KEY_ESCAPE
                || Minecraft.getInstance().options.keyInventory.matches(
                        key, eventArgs.scanCode())) {
            if (revealed()) {
                onClose();
            } else {
                skipped = true;
                requestClaim();
            }
            eventArgs.consumed(true);
            return;
        }
        super.onKeyPressed(eventArgs);
    }

    private void requestClaim() {
        if (claimRequested) return;
        claimRequested = true;
        SakuraNetwork.sendToServer(new LotteryClaimPacket(packet.getToken()));
    }

    @Override
    public void onClose() {
        if (!revealed()) {
            skipped = true;
            requestClaim();
            return;
        }
        requestClaim();
        super.onClose();
    }

    private void shape(GuiGraphics graphics, int x, int y, int width, int height,
                       int color, int radius, int border) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(graphics.pose())
                .type(ShapeDrawArgs.ShapeType.RECT).color(color)
                .rect(new ShapeDrawArgs.RectParams().x(x).y(y).width(width).height(height)
                        .radius(radius).cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE)
                        .border(border)));
    }

    private void centered(GuiGraphics graphics, String text, int y, int color) {
        graphics.drawString(font, text, (int) (width / 2.0F - font.width(text) / 2.0F), y, color, false);
    }
}
