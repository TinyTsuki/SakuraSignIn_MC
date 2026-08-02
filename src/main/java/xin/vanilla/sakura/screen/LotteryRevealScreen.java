package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.resources.I18n;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
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
import xin.vanilla.sakura.reward.Reward;

import java.util.ArrayList;
import java.util.List;

/** 用客户端偏好的动画展示服务端已确定的抽奖结果。 */
public final class LotteryRevealScreen extends BaniraScreen {
    private final LotteryRevealPacket packet;
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private final ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

    public LotteryRevealScreen(Screen parent, LotteryRevealPacket packet) {
        super(SakuraComponent.get().transClient("word", "lottery_result"));
        this.parent = parent;
        this.packet = packet;
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    protected void initWidgets() {
        ButtonWidget close = new ButtonWidget(this);
        close.bounds(new ScreenCoordinate(width / 2 - 70, height / 2 + 66, 140, 20));
        close.text(SakuraComponent.get().transClient("word", "cancel"));
        close.onClick(button -> onClose());
        addWidget(close);
    }

    @Override
    protected void onRender(MatrixStack stack, float partialTicks) {
        int panelWidth = Math.min(520, width - 40);
        int panelHeight = 190;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        shape(stack, panelX, panelY, panelWidth, panelHeight,
                getEffectiveTheme().panelBg(), 8, 0);
        centered(stack, packet.getPoolName(), panelY + 16,
                getEffectiveTheme().buttonText());

        long elapsed = System.currentTimeMillis() - openedAt;
        LotteryAnimationStyle style = ClientConfig.get().display().lotteryAnimationStyle();
        switch (style) {
            case CARDS:
                renderCards(stack, panelY, elapsed);
                break;
            case ROULETTE:
                renderRoulette(stack, panelY, elapsed);
                break;
            case INSTANT:
                renderWinner(stack, panelY + 62, true);
                break;
            case STRIP:
            default:
                renderStrip(stack, panelX, panelWidth, panelY, elapsed);
                break;
        }
        if (elapsed >= revealDelay(style)) {
            String name = SakuraRewardClient.displayName(packet.getWinner(),
                    Minecraft.getInstance().options.languageCode, true).toString();
            centered(stack, I18n.get("format.sakura_sign_in.lottery_won_s", name),
                    panelY + 128, getEffectiveTheme().buttonText());
        }
        renderWidgets(stack, partialTicks);
    }

    private void renderStrip(MatrixStack stack, int panelX, int panelWidth,
                             int panelY, long elapsed) {
        List<Reward> sequence = sequence(15);
        double progress = Math.min(1D, elapsed / 2600D);
        double eased = 1D - Math.pow(1D - progress, 4D);
        int spacing = 34;
        int stopIndex = sequence.size() - 3;
        double offset = eased * stopIndex * spacing;
        int center = panelX + panelWidth / 2;
        shape(stack, panelX + 20, panelY + 44, panelWidth - 40, 54,
                getEffectiveTheme().buttonBg(), 5, 0);
        for (int i = 0; i < sequence.size(); i++) {
            int x = (int) Math.round(center + i * spacing - offset - 8);
            if (x >= panelX + 24 && x <= panelX + panelWidth - 40) {
                renderReward(stack, sequence.get(i), x, panelY + 63);
            }
        }
        shape(stack, center - 12, panelY + 48, 24, 46,
                getEffectiveTheme().buttonBorderHover(), 3, 2);
    }

    private void renderCards(MatrixStack stack, int panelY, long elapsed) {
        List<Reward> values = sequence(5);
        int startX = width / 2 - 100;
        int active = Math.min(values.size() - 1, (int) (elapsed / 260L));
        for (int i = 0; i < values.size(); i++) {
            int x = startX + i * 42;
            shape(stack, x, panelY + 48, 34, 50,
                    i <= active ? getEffectiveTheme().buttonBgHover()
                            : getEffectiveTheme().buttonBg(), 5, 1);
            if (i <= active) {
                renderReward(stack, i == values.size() - 1
                        ? packet.getWinner() : values.get(i), x + 9, panelY + 65);
            }
        }
    }

    private void renderRoulette(MatrixStack stack, int panelY, long elapsed) {
        List<Reward> values = sequence(8);
        int active = elapsed >= 2400L ? values.size() - 1
                : (int) (elapsed / Math.max(65L, 220L - elapsed / 18L)) % values.size();
        int centerX = width / 2;
        int centerY = panelY + 76;
        for (int i = 0; i < values.size(); i++) {
            double angle = Math.PI * 2D * i / values.size() - Math.PI / 2D;
            int x = centerX + (int) Math.round(Math.cos(angle) * 54D) - 8;
            int y = centerY + (int) Math.round(Math.sin(angle) * 32D) - 8;
            if (i == active) {
                shape(stack, x - 4, y - 4, 24, 24,
                        getEffectiveTheme().buttonBorderHover(), 4, 2);
            }
            renderReward(stack, i == values.size() - 1
                    ? packet.getWinner() : values.get(i), x, y);
        }
    }

    private void renderWinner(MatrixStack stack, int y, boolean frame) {
        if (frame) {
            shape(stack, width / 2 - 22, y - 14, 44, 44,
                    getEffectiveTheme().buttonBgHover(), 6, 1);
        }
        renderReward(stack, packet.getWinner(), width / 2 - 8, y);
    }

    private List<Reward> sequence(int size) {
        List<Reward> source = packet.getPreview().isEmpty()
                ? java.util.Collections.singletonList(packet.getWinner()) : packet.getPreview();
        List<Reward> result = new ArrayList<>();
        for (int i = 0; i < Math.max(1, size - 1); i++) {
            result.add(source.get(i % source.size()));
        }
        result.add(packet.getWinner());
        return result;
    }

    private void renderReward(MatrixStack stack, Reward reward, int x, int y) {
        RewardRenderer.renderCustomReward(stack, itemRenderer, font,
                SakuraClientState.getThemeTexture(),
                SakuraClientState.getThemeTextureCoordinate(), reward, x, y, true, false);
    }

    private void shape(MatrixStack stack, int x, int y, int width, int height,
                       int color, int radius, int border) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(stack)
                .type(ShapeDrawArgs.ShapeType.RECT).color(color)
                .rect(new ShapeDrawArgs.RectParams().x(x).y(y).width(width).height(height)
                        .radius(radius).border(border)));
    }

    private void centered(MatrixStack stack, String text, int y, int color) {
        font.draw(stack, text, width / 2.0F - font.width(text) / 2.0F, y, color);
    }

    private static long revealDelay(LotteryAnimationStyle style) {
        return style == LotteryAnimationStyle.INSTANT ? 0L
                : style == LotteryAnimationStyle.CARDS ? 1300L : 2400L;
    }
}
