package xin.vanilla.sakura.screen;

import xin.vanilla.sakura.data.time.SakuraClock;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.event.MouseScrollEvent;
import xin.vanilla.banira.client.gui.widget.BaseWidget;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.enums.ESignInStatus;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.banira.client.data.GLFWKey;

import java.util.Date;
import java.util.function.Consumer;

/**
 * 签到日历格子。命中、点击和滚轮统一交给 Banira Widget 生命周期。
 */
@Getter
@Setter
@Accessors(chain = true)
public final class SignInCell extends BaseWidget {
    public static final int TOOLTIP_MAX_VISIBLE_ITEMS = 5;

    private final ResourceLocation backgroundTexture;
    private final TextureCoordinate textureCoordinate;
    private final double scale;
    private final RewardList rewardList;
    private final int year;
    private final int month;
    private final int day;
    private final int itemIconSize = 16;

    private int tooltipScrollOffset;
    private int status;
    private boolean showIcon;
    private boolean showText;
    private boolean showHover;
    private double iconX;
    private double iconY;
    private Consumer<MouseEvent> releaseHandler;

    public SignInCell(BaniraScreen screen, ResourceLocation resourceLocation,
                      TextureCoordinate textureCoordinate, double x, double y,
                      double width, double height, double scale,
                      @NonNull RewardList rewardList,
                      int year, int month, int day, int status) {
        super(screen, new ScreenCoordinate(x, y, width, height));
        this.backgroundTexture = resourceLocation;
        this.textureCoordinate = textureCoordinate;
        this.scale = scale;
        this.rewardList = rewardList;
        this.year = year;
        this.month = month;
        this.day = day;
        this.status = status;
        this.iconX = x;
        this.iconY = y;
    }

    @Override
    public void render(MatrixStack stack, float partialTicks) {
        double x = absoluteX();
        double y = absoluteY();
        double width = bounds().width();
        double height = bounds().height();
        FontRenderer font = screen.getFont();

        if (showIcon) {
            Minecraft.getInstance().getTextureManager().bind(backgroundTexture);
            renderStatusIcon(stack, x, y, width, height);
        }
        if (showText) {
            renderDay(stack, font, x, y, width);
        }
    }

    private void renderStatusIcon(MatrixStack stack, double x, double y, double width, double height) {
        if (status == ESignInStatus.REWARDED.getCode()) {
            Coordinate uv = textureCoordinate.getRewardedUV();
            AbstractGuiUtils.blit(stack, backgroundTexture, (int) x, (int) y, (int) width, (int) height,
                    (float) uv.getU0(), (float) uv.getV0(),
                    (int) uv.getUWidth(), (int) uv.getVHeight(),
                    textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
            return;
        }

        Coordinate uv = status == ESignInStatus.SIGNED_IN.getCode()
                || ClientConfig.get().display().autoRewarded()
                ? textureCoordinate.getSignedInUV()
                : textureCoordinate.getNotSignedInUV();
        updateAprilFoolsPosition(x, y, width, height);
        float u0 = (float) (uv.getU0() + uv.getX());
        float v0 = (float) (uv.getV0() + uv.getY());
        int hoverPadding = mouseInside ? 2 : 0;
        AbstractGuiUtils.blit(stack, backgroundTexture,
                (int) iconX - hoverPadding, (int) iconY - hoverPadding,
                (int) width + hoverPadding * 2, (int) height + hoverPadding * 2,
                u0, v0, (int) uv.getUWidth(), (int) uv.getVHeight(),
                textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
    }

    private void updateAprilFoolsPosition(double x, double y, double width, double height) {
        Date clientDate = SakuraClock.clientNow();
        if (DateUtils.getHourOfDay(clientDate) >= 12
                || DateUtils.getDayOfMonth(clientDate) != 1
                || DateUtils.getMonthOfDate(clientDate) != 4) {
            iconX = x;
            iconY = y;
            return;
        }
        double dx = iconX + width / 2 - screen.inputState().mouseX();
        double dy = iconY + height / 2 - screen.inputState().mouseY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > 0.001 && distance < width + height) {
            iconX += dx / distance;
            iconY += dy / distance;
        }
    }

    private void renderDay(MatrixStack stack, FontRenderer font, double x, double y, double width) {
        Date date = SakuraClock.clientNow();
        int color = textureCoordinate.getTextColorDefault();
        Component dayComponent = SakuraComponent.get().literal(String.valueOf(day));
        if (year == DateUtils.getYearPart(date) && month == DateUtils.getMonthOfDate(date)) {
            if (day == DateUtils.getDayOfMonth(date)) {
                color = textureCoordinate.getTextColorToday();
                dayComponent.underlined(true);
            } else {
                color = textureCoordinate.getTextColorCurrent();
            }
        } else if (status == ESignInStatus.CAN_REPAIR.getCode()) {
            color = textureCoordinate.getTextColorCanRepair();
        }
        float dayWidth = font.width(dayComponent.toString());
        font.draw(stack, dayComponent.color(color).toVanilla(),
                (float) (x + (width - dayWidth) / 2),
                (float) (y + textureCoordinate.getDateOffset() * scale + 0.1f), color);
    }

    /**
     * 在 Screen 的延迟提示阶段绘制奖励详情，避免被弹出菜单或裁剪区域覆盖。
     */
    public void renderTooltip(MatrixStack stack, FontRenderer font, ItemRenderer itemRenderer) {
        double x = absoluteX();
        double y = absoluteY();
        double width = bounds().width();
        Coordinate tooltipUV = textureCoordinate.getTooltipUV();
        Coordinate cellCoordinate = textureCoordinate.getTooltipCellCoordinate();
        double margin = textureCoordinate.getTooltipCellHMargin();
        double tooltipWidth = margin + (itemIconSize + margin) * TOOLTIP_MAX_VISIBLE_ITEMS;
        double tooltipHeight = tooltipWidth * tooltipUV.getVHeight() / tooltipUV.getUWidth();
        double tooltipScale = tooltipWidth / tooltipUV.getUWidth();
        double tooltipX = (x == iconX ? screen.inputState().mouseX() : iconX + width / 2) - tooltipWidth / 2;
        double tooltipY = (y == iconY ? screen.inputState().mouseY() : iconY - 2) - tooltipHeight - 1;

        RenderSystem.disableDepthTest();
        stack.pushPose();
        stack.translate(0, 0, 200);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Minecraft.getInstance().getTextureManager().bind(backgroundTexture);
        AbstractGuiUtils.blit(stack, backgroundTexture, (int) tooltipX, (int) tooltipY,
                (int) tooltipWidth, (int) tooltipHeight,
                (float) tooltipUV.getU0(), (float) tooltipUV.getV0(),
                (int) tooltipUV.getUWidth(), (int) tooltipUV.getVHeight(),
                textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        RenderSystem.disableBlend();

        renderTooltipScrollBar(stack, tooltipX, tooltipY, tooltipScale);
        renderTooltipRewards(stack, font, itemRenderer, tooltipX, tooltipY, tooltipScale, margin, cellCoordinate);
        renderTooltipDate(stack, font, tooltipX, tooltipY, tooltipWidth, tooltipScale);

        stack.popPose();
        RenderSystem.enableDepthTest();
    }

    private void renderTooltipScrollBar(MatrixStack stack, double tooltipX, double tooltipY, double tooltipScale) {
        Coordinate scroll = textureCoordinate.getTooltipScrollCoordinate();
        double trackX = tooltipX + scroll.getX() * tooltipScale;
        double trackY = tooltipY + scroll.getY() * tooltipScale;
        double trackWidth = scroll.getWidth() * tooltipScale;
        double trackHeight = scroll.getHeight() * tooltipScale;
        AbstractGui.fill(stack, (int) trackX, (int) trackY,
                (int) (trackX + trackWidth), (int) (trackY + trackHeight), 0xCC232323);

        double visibleScale = rewardList.size() > TOOLTIP_MAX_VISIBLE_ITEMS
                ? (double) TOOLTIP_MAX_VISIBLE_ITEMS / rewardList.size() : 1;
        int hiddenItems = Math.max(rewardList.size() - TOOLTIP_MAX_VISIBLE_ITEMS, 0);
        double offsetWidth = hiddenItems == 0 ? 0
                : (1 - visibleScale) * trackWidth / hiddenItems;
        double thumbX = trackX + tooltipScrollOffset * offsetWidth;
        double thumbWidth = trackWidth * visibleScale;
        AbstractGui.fill(stack, (int) thumbX + 1, (int) trackY,
                (int) (thumbX + thumbWidth) - 1, (int) (trackY + trackHeight), 0xCCCCCCCC);
    }

    private void renderTooltipRewards(MatrixStack stack, FontRenderer font, ItemRenderer itemRenderer,
                                      double tooltipX, double tooltipY, double tooltipScale,
                                      double margin, Coordinate cellCoordinate) {
        boolean showProbability = Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.hasPermissions(
                CommonConfig.get().permission().permissionRewardProbability());
        for (int i = 0; i < TOOLTIP_MAX_VISIBLE_ITEMS; i++) {
            int index = i + (rewardList.size() > TOOLTIP_MAX_VISIBLE_ITEMS ? tooltipScrollOffset : 0);
            if (index < 0 || index >= rewardList.size()) {
                continue;
            }
            Reward reward = rewardList.get(index);
            double itemX = tooltipX + cellCoordinate.getX() * tooltipScale
                    + i * (itemIconSize + margin);
            double itemY = tooltipY + cellCoordinate.getY() * tooltipScale;
            RewardRenderer.renderCustomReward(stack, itemRenderer, font,
                    backgroundTexture, textureCoordinate, reward,
                    (int) itemX, (int) itemY, true, showProbability);
        }
    }

    private void renderTooltipDate(MatrixStack stack, FontRenderer font, double tooltipX,
                                   double tooltipY, double tooltipWidth, double tooltipScale) {
        Date date = DateUtils.getDate(year, month, day);
        String monthTitle = DateUtils.toLocalStringMonth(date, Minecraft.getInstance().options.languageCode);
        String dayTitle = DateUtils.toLocalStringDay(date, Minecraft.getInstance().options.languageCode);
        Component title = SakuraComponent.get().literal(monthTitle + " " + dayTitle);
        double titleX = tooltipX + (tooltipWidth - font.width(title.toString())) / 2;
        double titleY = tooltipY + textureCoordinate.getTooltipDateCoordinate().getY() * tooltipScale;
        font.draw(stack, title.color(0xFFFFFFFF).toVanilla(), (int) titleX, (int) titleY, 0xFFFFFFFF);
    }

    @Override
    protected boolean onMouseClick(MouseEvent event) {
        return showIcon && event.button() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT;
    }

    @Override
    protected boolean onMouseRelease(MouseEvent event, boolean inside) {
        if (inside && showIcon && releaseHandler != null) {
            releaseHandler.accept(event);
        }
        return true;
    }

    @Override
    protected boolean onMouseScroll(MouseScrollEvent event) {
        if (!showHover || rewardList.size() <= TOOLTIP_MAX_VISIBLE_ITEMS) {
            return false;
        }
        int maxOffset = Math.max(rewardList.size() - TOOLTIP_MAX_VISIBLE_ITEMS, 0);
        tooltipScrollOffset = event.delta() > 0
                ? Math.max(tooltipScrollOffset - 1, 0)
                : Math.min(tooltipScrollOffset + 1, maxOffset);
        return true;
    }
}
