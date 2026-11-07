package xin.vanilla.sakura.screen;

import xin.vanilla.sakura.util.SakuraUtils;
import org.joml.Matrix3x2fStack;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import xin.vanilla.banira.client.data.GLFWKey;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.event.MouseScrollEvent;
import xin.vanilla.banira.client.gui.widget.BaseWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.theme.SakuraThemeTextures;
import xin.vanilla.sakura.client.theme.ThemeTexture;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.data.time.SakuraClock;
import xin.vanilla.sakura.enums.ESignInStatus;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

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

    public Component uiDay() {
        Date now = SakuraClock.clientNow();
        int color = textureCoordinate.getTextColorDefault();
        boolean currentMonth = year == DateUtils.getYearPart(now) && month == DateUtils.getMonthOfDate(now);
        boolean today = currentMonth && day == DateUtils.getDayOfMonth(now);
        if (today) color = textureCoordinate.getTextColorToday();
        else if (currentMonth) color = textureCoordinate.getTextColorCurrent();
        else if (status == ESignInStatus.CAN_REPAIR.getCode()) color = textureCoordinate.getTextColorCanRepair();
        return SakuraComponent.get().literal(showText ? Integer.toString(day) : "").color(color).underlined(today);
    }

    public xin.vanilla.banira.client.data.Texture uiStatusIcon() {
        if (!showIcon || !hasStatusMark()) return null;
        return statusTexture().texture();
    }

    public Component uiTooltip() {
        Component result = SakuraComponent.get().literal(DateUtils.toString(DateUtils.getDate(year, month, day)));
        if (!showHover) return result;
        LocalPlayerPermission permissions = tooltipPermissions();
        if (!permissions.details) return result;
        String language = Minecraft.getInstance().options.languageCode;
        for (Reward reward : rewardList) {
            result.append("\n").append(reward.getName(language, true));
            if (permissions.probability)
                result.append(" (" + reward.getProbability().stripTrailingZeros().toPlainString() + ")");
        }
        return result;
    }

    public boolean uiDetailsVisible() {
        return showHover && tooltipPermissions().details;
    }

    public java.util.List<xin.vanilla.sakura.client.ui.SignInUiSession.RewardLine> uiRewardLines() {
        if (!uiDetailsVisible()) return java.util.List.of();
        boolean probability = tooltipPermissions().probability;
        String language = Minecraft.getInstance().options.languageCode;
        return rewardList.stream().map(reward -> new xin.vanilla.sakura.client.ui.SignInUiSession.RewardLine(
                reward.getName(language, true), probability ? SakuraComponent.get().transClient("format", "aui_probability_s",
                reward.getProbability().multiply(java.math.BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString())
                : SakuraComponent.get().literal(""), RewardRenderer.uiIcon(reward))).toList();
    }

    private LocalPlayerPermission tooltipPermissions() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        return new LocalPlayerPermission(player == null || SakuraUtils.hasNativePermission(player.permissions(), CommonConfig.get().permission().permissionRewardDetail()),
                player == null || SakuraUtils.hasNativePermission(player.permissions(), CommonConfig.get().permission().permissionRewardProbability()));
    }

    private record LocalPlayerPermission(boolean details, boolean probability) {
    }

    private final Identifier backgroundTexture;
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

    public SignInCell(BaniraScreen screen, Identifier resourceLocation,
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
    public void render(GuiGraphicsExtractor graphics, float partialTicks) {
        Matrix3x2fStack stack = graphics.pose();
        double x = absoluteX();
        double y = absoluteY();
        double width = bounds().width();
        double height = bounds().height();
        Font font = screen.getFont();

        if (showIcon && hasStatusMark()) {
            renderStatusIcon(stack, x, y, width, height);
        }
        if (showText) {
            renderDay(graphics, font, x, y, width);
        }
    }

    private void renderStatusIcon(Matrix3x2fStack stack, double x, double y, double width, double height) {
        if (status == ESignInStatus.REWARDED.getCode()) {
            statusTexture().draw(stack, (int) x, (int) y, (int) width, (int) height);
            return;
        }

        updateAprilFoolsPosition(x, y, width, height);
        int hoverPadding = mouseInside ? 2 : 0;
        statusTexture().draw(stack,
                (int) iconX - hoverPadding, (int) iconY - hoverPadding,
                (int) width + hoverPadding * 2, (int) height + hoverPadding * 2);
    }

    private boolean hasStatusMark() {
        return status == ESignInStatus.SIGNED_IN.getCode() || status == ESignInStatus.REWARDED.getCode();
    }

    private ThemeTexture statusTexture() {
        return SakuraThemeTextures.status(status == ESignInStatus.REWARDED.getCode() ? "rewarded"
                : "signed_in");
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

    private void renderDay(GuiGraphicsExtractor graphics, Font font, double x, double y, double width) {
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
        graphics.text(font, dayComponent.color(color).toVanilla(),
                (int) (x + (width - dayWidth) / 2),
                (int) (y + textureCoordinate.getDateOffset() * scale + 0.1f), color, false);
    }

    /**
     * 在 Screen 的延迟提示阶段绘制奖励详情，避免被弹出菜单或裁剪区域覆盖。
     */
    public void renderTooltip(GuiGraphicsExtractor graphics, Font font) {
        Matrix3x2fStack stack = graphics.pose();
        double x = absoluteX();
        double y = absoluteY();
        double width = bounds().width();
        ThemeTexture tooltipTexture = SakuraThemeTextures.get("background.tooltip");
        Coordinate cellCoordinate = textureCoordinate.getTooltipCellCoordinate();
        double margin = textureCoordinate.getTooltipCellHMargin();
        double tooltipWidth = margin + (itemIconSize + margin) * TOOLTIP_MAX_VISIBLE_ITEMS;
        double tooltipHeight = tooltipWidth * textureCoordinate.getTooltipHeight() / textureCoordinate.getTooltipWidth();
        double tooltipScale = tooltipWidth / textureCoordinate.getTooltipWidth();
        double tooltipX = (x == iconX ? screen.inputState().mouseX() : iconX + width / 2) - tooltipWidth / 2;
        double tooltipY = (y == iconY ? screen.inputState().mouseY() : iconY - 2) - tooltipHeight - 1;

        graphics.nextStratum();
        tooltipTexture.draw(stack, (int) tooltipX, (int) tooltipY, (int) tooltipWidth, (int) tooltipHeight);

        renderTooltipScrollBar(stack, tooltipX, tooltipY, tooltipScale);
        renderTooltipRewards(graphics, font, tooltipX, tooltipY, tooltipScale, margin, cellCoordinate);
        renderTooltipDate(graphics, font, tooltipX, tooltipY, tooltipWidth, tooltipScale);

    }

    private void renderTooltipScrollBar(Matrix3x2fStack stack, double tooltipX, double tooltipY, double tooltipScale) {
        Coordinate scroll = textureCoordinate.getTooltipScrollCoordinate();
        double trackX = tooltipX + scroll.getX() * tooltipScale;
        double trackY = tooltipY + scroll.getY() * tooltipScale;
        double trackWidth = scroll.getWidth() * tooltipScale;
        double trackHeight = scroll.getHeight() * tooltipScale;
        AbstractGuiUtils.fill(stack, (int) trackX, (int) trackY,
                (int) trackWidth, (int) trackHeight, 0xCC232323);

        double visibleScale = rewardList.size() > TOOLTIP_MAX_VISIBLE_ITEMS
                ? (double) TOOLTIP_MAX_VISIBLE_ITEMS / rewardList.size() : 1;
        int hiddenItems = Math.max(rewardList.size() - TOOLTIP_MAX_VISIBLE_ITEMS, 0);
        double offsetWidth = hiddenItems == 0 ? 0
                : (1 - visibleScale) * trackWidth / hiddenItems;
        double thumbX = trackX + tooltipScrollOffset * offsetWidth;
        double thumbWidth = trackWidth * visibleScale;
        AbstractGuiUtils.fill(stack, (int) thumbX + 1, (int) trackY,
                Math.max(0, (int) thumbWidth - 2), (int) trackHeight, 0xCCCCCCCC);
    }

    private void renderTooltipRewards(GuiGraphicsExtractor graphics, Font font,
                                      double tooltipX, double tooltipY, double tooltipScale,
                                      double margin, Coordinate cellCoordinate) {
        boolean showProbability = Minecraft.getInstance().player == null
                || SakuraUtils.hasNativePermission(Minecraft.getInstance().player.permissions(),
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
            RewardRenderer.renderCustomReward(graphics, font,
                    backgroundTexture, textureCoordinate, reward,
                    (int) itemX, (int) itemY, true, showProbability);
        }
    }

    private void renderTooltipDate(GuiGraphicsExtractor graphics, Font font, double tooltipX,
                                   double tooltipY, double tooltipWidth, double tooltipScale) {
        Date date = DateUtils.getDate(year, month, day);
        String monthTitle = DateUtils.toLocalStringMonth(date, Minecraft.getInstance().options.languageCode);
        String dayTitle = DateUtils.toLocalStringDay(date, Minecraft.getInstance().options.languageCode);
        Component title = SakuraComponent.get().literal(monthTitle + " " + dayTitle);
        double titleX = tooltipX + (tooltipWidth - font.width(title.toString())) / 2;
        double titleY = tooltipY + textureCoordinate.getTooltipDateCoordinate().getY() * tooltipScale;
        graphics.text(font, title.color(0xFFFFFFFF).toVanilla(),
                (int) titleX, (int) titleY, 0xFFFFFFFF, false);
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
