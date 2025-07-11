package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.data.RewardCell;
import xin.vanilla.sakura.data.RewardList;
import xin.vanilla.sakura.enums.EnumSignInStatus;
import xin.vanilla.sakura.screen.component.KeyEventManager;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;

@Data
@Accessors(chain = true)
public class SignInCell {
    private final ResourceLocation BACKGROUND_TEXTURE;
    private final TextureCoordinate textureCoordinate;

    /**
     * 当前滚动偏移量
     */
    private int tooltipScrollOffset = 0;
    /**
     * 显示的最大项目数
     */
    public static final int TOOLTIP_MAX_VISIBLE_ITEMS = 5;

    // 物品图标的大小
    private final int itemIconSize = 16;
    public double x, y, x1, y1, width, height, scale;
    public int year, month, day;
    private boolean showIcon;
    private boolean showText;
    private boolean showHover;

    // 鼠标之前的位置坐标
    private double previousMouseX;
    private double previousMouseY;

    public SignInCell(ResourceLocation resourceLocation, TextureCoordinate textureCoordinate, double x, double y, double width, double height, double scale, int year, int month, int day) {
        BACKGROUND_TEXTURE = resourceLocation;
        this.textureCoordinate = textureCoordinate;
        this.x = x;
        this.y = y;
        this.x1 = x;
        this.y1 = y;
        this.width = width;
        this.height = height;
        this.year = year;
        this.month = month;
        this.day = day;
        this.scale = scale;
    }

    // 判断鼠标是否在当前格子内
    public boolean isMouseOver(KeyEventManager keyManager) {
        return keyManager.getMouseX() >= x && keyManager.getMouseX() <= x + width && keyManager.getMouseY() >= y && keyManager.getMouseY() <= y + height;
    }

    public RewardCell getRewardCell() {
        Date cellDate = DateUtils.getDate(this.year, this.month, this.day);
        String cellDateString = DateUtils.toString(cellDate);
        return SakuraSignIn.getRewardCellMap().getOrDefault(cellDateString, new RewardCell(EnumSignInStatus.NO_ACTION, cellDateString, new RewardList()));
    }

    public int getRewardCellStatus() {
        return getRewardCell().getStatus().getCode();
    }

    public int getRewardCount() {
        return getRewardCell().getRewards().size();
    }

    // 渲染格子
    public void render(MatrixStack matrixStack, FontRenderer font, KeyEventManager keyManager) {
        boolean isHovered = this.isMouseOver(keyManager);
        int status = this.getRewardCellStatus();
        if (showIcon) {
            AbstractGuiUtils.bindTexture(BACKGROUND_TEXTURE);
            if (status == EnumSignInStatus.REWARDED.getCode()) {
                // 绘制已领取图标
                Coordinate signedInUV = textureCoordinate.getRewardedUV();
                AbstractGuiUtils.blit(matrixStack, (int) x, (int) y, (int) width, (int) height, (float) signedInUV.getU0(), (float) signedInUV.getV0(), (int) signedInUV.getUWidth(), (int) signedInUV.getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
            } else {
                Coordinate rewardUV;
                // 绘制奖励图标
                if (status == EnumSignInStatus.SIGNED_IN.getCode() || ClientConfig.AUTO_REWARDED.get()) {
                    rewardUV = textureCoordinate.getSignedInUV();
                } else {
                    rewardUV = textureCoordinate.getNotSignedInUV();
                }
                Date clientDate = DateUtils.getClientDate();
                if (DateUtils.getHourOfDay(clientDate) < 12 && DateUtils.getDayOfMonth(clientDate) == 1 && DateUtils.getMonthOfDate(clientDate) == 4) {
                    // 逃离的距离
                    double escapeDistance = width + height;
                    // 计算当前鼠标位置与图标之间的距离
                    double dx = x1 + width / 2 - keyManager.getMouseX();
                    double dy = y1 + height / 2 - keyManager.getMouseY();
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    // 如果图标与鼠标距离小于指定的逃离距离
                    if (distance < escapeDistance) {
                        // 计算逃离方向的单位向量
                        double escapeX = dx / distance;
                        double escapeY = dy / distance;
                        // 更新图标位置，向逃离方向移动
                        x1 += escapeX;
                        y1 += escapeY;
                    }
                }
                // x 与 y 为内置主题特殊图标UV的偏移量
                float u0 = (float) (rewardUV.getU0() + rewardUV.getX());
                float v0 = (float) (rewardUV.getV0() + rewardUV.getY());
                if (isHovered) {
                    AbstractGuiUtils.blit(matrixStack, (int) x1 - 2, (int) y1 - 2, (int) width + 4, (int) height + 4, u0, v0, (int) rewardUV.getUWidth(), (int) rewardUV.getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
                } else {
                    AbstractGuiUtils.blit(matrixStack, (int) x1, (int) y1, (int) width, (int) height, u0, v0, (int) rewardUV.getUWidth(), (int) rewardUV.getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
                }
                previousMouseX = keyManager.getMouseX();
                previousMouseY = keyManager.getMouseY();
            }
        }

        if (showText) {
            // 绘制日期
            Date date = DateUtils.getClientDate();
            int color = textureCoordinate.getTextColorDefault();
            Component dayComponent = Component.literal(String.valueOf(day));
            if (year == DateUtils.getYearPart(date) && month == DateUtils.getMonthOfDate(date)) {
                if (day == DateUtils.getDayOfMonth(date)) {
                    color = textureCoordinate.getTextColorToday();
                    dayComponent.setUnderlined(true);
                } else {
                    color = textureCoordinate.getTextColorCurrent();
                }
            } else if (status == EnumSignInStatus.CAN_REPAIR.getCode()) {
                color = textureCoordinate.getTextColorCanRepair();
            }
            float dayWidth = font.width(dayComponent.toString());
            font.draw(matrixStack, dayComponent.setColorArgb(color).toTextComponent(), (float) (x + (width - dayWidth) / 2), (float) (y + textureCoordinate.getDateOffset() * this.scale + 0.1f), color);
        }
    }

    // 绘制奖励详情弹出层
    public void renderTooltip(MatrixStack matrixStack, FontRenderer font, ItemRenderer itemRenderer, KeyEventManager keyManager) {
        RewardCell rewardCell = this.getRewardCell();
        int rewardCount = rewardCell.getRewards().size();

        // 禁用深度测试
        RenderSystem.disableDepthTest();
        matrixStack.pushPose();
        // 提升Z坐标以确保弹出层在最上层
        matrixStack.translate(0, 0, 200.0F);

        Coordinate tooltipUV = textureCoordinate.getTooltipUV();
        Coordinate cellCoordinate = textureCoordinate.getTooltipCellCoordinate();
        // 物品图标之间的间距
        double margin = textureCoordinate.getTooltipCellHMargin();
        // 弹出层宽高
        double tooltipWidth = margin + (itemIconSize + margin) * TOOLTIP_MAX_VISIBLE_ITEMS;
        double tooltipHeight = (tooltipWidth * tooltipUV.getVHeight() / tooltipUV.getUWidth());
        // 弹出层缩放比例
        double tooltipScale = tooltipWidth / tooltipUV.getUWidth();

        // 开启 OpenGL 的混合模式，使得纹理的透明区域渲染生效
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 在鼠标位置左上角绘制弹出层背景
        AbstractGuiUtils.bindTexture(BACKGROUND_TEXTURE);
        double tooltipX0 = (x == x1 ? (keyManager.getMouseX()) : x1 + width / 2) - tooltipWidth / 2;
        double tooltipY0 = (y == y1 ? keyManager.getMouseY() : y1 - 2) - tooltipHeight - 1;
        AbstractGuiUtils.blit(matrixStack, (int) tooltipX0, (int) tooltipY0, (int) tooltipWidth, (int) tooltipHeight, (float) tooltipUV.getU0(), (float) tooltipUV.getV0(), (int) tooltipUV.getUWidth(), (int) tooltipUV.getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        // 关闭 OpenGL 的混合模式
        RenderSystem.disableBlend();

        // 绘制滚动条
        Coordinate scrollCoordinate = textureCoordinate.getTooltipScrollCoordinate();
        double outScrollX0 = tooltipX0 + scrollCoordinate.getX() * tooltipScale;
        double outScrollX1 = outScrollX0 + scrollCoordinate.getWidth() * tooltipScale;
        double outScrollY0 = tooltipY0 + scrollCoordinate.getY() * tooltipScale;
        double outScrollY1 = outScrollY0 + scrollCoordinate.getHeight() * tooltipScale;
        AbstractGui.fill(matrixStack, (int) outScrollX0, (int) outScrollY0, (int) outScrollX1, (int) outScrollY1, 0xCC232323);
        // 滚动条百分比
        double inScrollWidthScale = rewardCount > TOOLTIP_MAX_VISIBLE_ITEMS ? (double) TOOLTIP_MAX_VISIBLE_ITEMS / rewardCount : 1;
        // 多出来的格子数量
        int outCell = Math.max(rewardCount - TOOLTIP_MAX_VISIBLE_ITEMS, 0);
        // 多出来的每个格子所占的空余条长度
        double outCellWidth = outCell == 0 ? 0 : (1 - inScrollWidthScale) * scrollCoordinate.getWidth() * tooltipScale / outCell;
        // 滚动条左边距长度
        double inScrollLeftWidth = tooltipScrollOffset * outCellWidth;
        // 滚动条长度
        double inScrollWidth = scrollCoordinate.getWidth() * tooltipScale * inScrollWidthScale;

        double inScrollX0 = outScrollX0 + inScrollLeftWidth;
        double inScrollX1 = inScrollX0 + inScrollWidth;
        double inScrollY0 = outScrollY0;
        double inScrollY1 = outScrollY1;
        AbstractGui.fill(matrixStack, (int) inScrollX0 + 1, (int) inScrollY0, (int) inScrollX1 - 1, (int) inScrollY1, 0xCCCCCCCC);

        boolean showQuality = true;
        if (Minecraft.getInstance().player != null) {
            showQuality = Minecraft.getInstance().player.hasPermissions(ServerConfig.PERMISSION_REWARD_PROBABILITY.get());
        }
        for (int i = 0; i < TOOLTIP_MAX_VISIBLE_ITEMS; i++) {
            int index = i + (rewardCount > TOOLTIP_MAX_VISIBLE_ITEMS ? tooltipScrollOffset : 0);
            if (index >= 0 && index < rewardCount) {
                Reward reward = rewardCell.getRewards().get(index);
                // 物品图标在弹出层中的 x 位置
                double itemX = (tooltipX0 + cellCoordinate.getX() * tooltipScale) + i * (itemIconSize + margin);
                // 物品图标在弹出层中的 y 位置
                double itemY = tooltipY0 + cellCoordinate.getY() * tooltipScale;
                // 渲染物品图标
                AbstractGuiUtils.renderCustomReward(matrixStack, itemRenderer, font, BACKGROUND_TEXTURE, textureCoordinate, reward, (int) itemX, (int) itemY, true, showQuality);
            }
        }
        // 绘制文字
        String monthTitle = DateUtils.toLocalStringMonth(DateUtils.getDate(year, month, day), Minecraft.getInstance().options.languageCode);
        String dayTitle = DateUtils.toLocalStringDay(DateUtils.getDate(year, month, day), Minecraft.getInstance().options.languageCode);
        Component title = Component.literal(String.format("%s %s", monthTitle, dayTitle));
        double fontWidth = font.width(title.toString());
        Coordinate dateCoordinate = textureCoordinate.getTooltipDateCoordinate();
        double tooltipDateX = tooltipX0 + (tooltipWidth - fontWidth) / 2;
        double tooltipDateY = tooltipY0 + (dateCoordinate.getY() * tooltipScale);
        int color = 0xFFFFFFFF;
        font.draw(matrixStack, title.setColorArgb(color).toTextComponent(), (int) tooltipDateX, (int) tooltipDateY, color);

        // 恢复原来的矩阵状态
        matrixStack.popPose();
        // 恢复深度测试
        RenderSystem.enableDepthTest();
    }
}
