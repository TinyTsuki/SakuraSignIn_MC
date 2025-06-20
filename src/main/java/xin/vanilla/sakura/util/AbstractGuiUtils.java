package xin.vanilla.sakura.util;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.enums.EnumEllipsisPosition;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AbstractGui工具类
 */
@OnlyIn(Dist.CLIENT)
public class AbstractGuiUtils {

    public static final int ITEM_ICON_SIZE = 16;

    private static final Random random = new Random();

    // region 设置深度

    @Getter
    public enum EDepth {
        BACKGROUND(1),
        FOREGROUND(250),
        OVERLAY(500),
        TOOLTIP(750),
        POPUP_TIPS(900),
        MOUSE(1000);

        private final int depth;

        EDepth(int depth) {
            this.depth = depth;
        }
    }

    /**
     * 以默认深度绘制
     */
    public static void renderByDepth(MatrixStack matrixStack, Consumer<MatrixStack> drawFunc) {
        AbstractGuiUtils.renderByDepth(matrixStack, EDepth.FOREGROUND, drawFunc);
    }

    /**
     * 以指定深度绘制
     *
     * @param depth 深度
     */
    public static void renderByDepth(MatrixStack matrixStack, EDepth depth, Consumer<MatrixStack> drawFunc) {
        if (depth != null) {
            RenderSystem.disableDepthTest();
            matrixStack.pushPose();
            matrixStack.translate(0, 0, depth.getDepth());
        }

        drawFunc.accept(matrixStack);

        if (depth != null) {
            matrixStack.popPose();
            RenderSystem.enableDepthTest();
        }
    }

    // endregion 设置深度

    // region 绘制纹理

    public static void bindTexture(ResourceLocation resourceLocation) {
        Minecraft.getInstance().getTextureManager().bind(resourceLocation);
    }

    public static void blit(MatrixStack matrixStack, int x0, int y0, int z, int destWidth, int destHeight, TextureAtlasSprite sprite) {
        AbstractGui.blit(matrixStack, x0, y0, z, destWidth, destHeight, sprite);
    }

    public static void blitBlend(MatrixStack matrixStack, int x0, int y0, int z, int destWidth, int destHeight, TextureAtlasSprite sprite) {
        blitByBlend(() ->
                AbstractGui.blit(matrixStack, x0, y0, z, destWidth, destHeight, sprite)
        );
    }

    public static void blit(MatrixStack matrixStack, int x0, int y0, int z, double u0, double v0, int width, int height, int textureHeight, int textureWidth) {
        AbstractGui.blit(matrixStack, x0, y0, z, (float) u0, (float) v0, width, height, textureHeight, textureWidth);
    }

    public static void blitBlend(MatrixStack matrixStack, int x0, int y0, int z, double u0, double v0, int width, int height, int textureHeight, int textureWidth) {
        blitByBlend(() ->
                AbstractGui.blit(matrixStack, x0, y0, z, (float) u0, (float) v0, width, height, textureHeight, textureWidth)
        );
    }

    /**
     * 使用指定的纹理坐标和尺寸信息绘制一个矩形区域。
     *
     * @param x0            矩形的左上角x坐标。
     * @param y0            矩形的左上角y坐标。
     * @param destWidth     目标矩形的宽度，决定了图像在屏幕上的宽度。
     * @param destHeight    目标矩形的高度，决定了图像在屏幕上的高度。
     * @param u0            源图像上矩形左上角的u轴坐标。
     * @param v0            源图像上矩形左上角的v轴坐标。
     * @param srcWidth      源图像上矩形的宽度，用于确定从源图像上裁剪的部分。
     * @param srcHeight     源图像上矩形的高度，用于确定从源图像上裁剪的部分。
     * @param textureWidth  整个纹理的宽度，用于计算纹理坐标。
     * @param textureHeight 整个纹理的高度，用于计算纹理坐标。
     */
    public static void blit(MatrixStack matrixStack, int x0, int y0, int destWidth, int destHeight, double u0, double v0, int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        AbstractGui.blit(matrixStack, x0, y0, destWidth, destHeight, (float) u0, (float) v0, srcWidth, srcHeight, textureWidth, textureHeight);
    }

    public static void blitBlend(MatrixStack matrixStack, int x0, int y0, int destWidth, int destHeight, double u0, double v0, int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        blitByBlend(() ->
                AbstractGui.blit(matrixStack, x0, y0, destWidth, destHeight, (float) u0, (float) v0, srcWidth, srcHeight, textureWidth, textureHeight)
        );
    }

    public static void blit(MatrixStack matrixStack, int x0, int y0, double u0, double v0, int destWidth, int destHeight, int textureWidth, int textureHeight) {
        AbstractGui.blit(matrixStack, x0, y0, (float) u0, (float) v0, destWidth, destHeight, textureWidth, textureHeight);
    }

    public static void blitBlend(MatrixStack matrixStack, int x0, int y0, double u0, double v0, int destWidth, int destHeight, int textureWidth, int textureHeight) {
        blitByBlend(() ->
                AbstractGui.blit(matrixStack, x0, y0, (float) u0, (float) v0, destWidth, destHeight, textureWidth, textureHeight)
        );
    }

    /**
     * 启用混合模式来绘制纹理
     */
    public static void blitByBlend(Runnable drawFunc) {
        // 启用混合模式来正确处理透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawFunc.run();
        RenderSystem.disableBlend();
    }

    @Data
    @Accessors(chain = true)
    public static class TransformArgs {
        private MatrixStack stack;
        private double x;
        private double y;
        private double width;
        private double height;
        /**
         * 缩放比例
         */
        private double scale = 1.0;
        /**
         * 水平翻转
         */
        private boolean flipHorizontal;
        /**
         * 垂直翻转
         */
        private boolean flipVertical;
        /**
         * 旋转角度
         */
        private double angle = 1.0;
        /**
         * 旋转中心
         */
        private RotationCenter center = RotationCenter.CENTER;

        public TransformArgs(MatrixStack stack) {
            this.stack = stack;
        }

        public TransformArgs setCoordinate(Coordinate coordinate) {
            this.x = coordinate.getX();
            this.y = coordinate.getY();
            this.width = coordinate.getWidth();
            this.height = coordinate.getHeight();
            return this;
        }

    }

    @Data
    @Accessors(chain = true)
    public static class TransformDrawArgs {
        private final MatrixStack stack;
        private double x;
        private double y;
        private double width;
        private double height;
    }


    public enum RotationCenter {
        TOP_LEFT,
        TOP_RIGHT,
        TOP_CENTER,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        BOTTOM_CENTER,
        CENTER,
    }

    /**
     * 变换后绘制
     *
     * @param args 变换参数
     */
    public static void renderByTransform(TransformArgs args, Consumer<TransformDrawArgs> drawFunc) {

        // 保存当前矩阵状态
        args.getStack().pushPose();

        // 计算目标点
        double tranX = 0, tranY = 0;
        double tranW = 0, tranH = 0;
        switch (args.getCenter()) {
            case CENTER:
                tranW = args.getWidth() / 2.0;
                tranH = args.getHeight() / 2.0;
                tranX = args.getX() + tranW;
                tranY = args.getY() + tranH;
                break;
            case TOP_LEFT:
                tranX = args.getX();
                tranY = args.getY();
                break;
            case TOP_RIGHT:
                tranW = args.getWidth();
                tranX = args.getX() + tranW;
                tranY = args.getY();
                break;
            case TOP_CENTER:
                tranW = args.getWidth() / 2.0;
                tranX = args.getX() + tranW;
                tranY = args.getY();
                break;
            case BOTTOM_LEFT:
                tranH = args.getHeight();
                tranX = args.getX();
                tranY = args.getY() + tranH;
                break;
            case BOTTOM_RIGHT:
                tranW = args.getWidth();
                tranH = args.getHeight();
                tranX = args.getX() + tranW;
                tranY = args.getY() + tranH;
                break;
            case BOTTOM_CENTER:
                tranW = args.getWidth() / 2.0;
                tranH = args.getHeight();
                tranX = args.getX() + tranW;
                tranY = args.getY() + tranH;
                break;
        }
        // 移至目标点
        args.getStack().translate(tranX, tranY, 0);

        // 翻缩放
        args.getStack().scale((float) args.getScale(), (float) args.getScale(), 1);

        // 旋转
        args.getStack().mulPose(Vector3f.ZP.rotationDegrees((float) args.getAngle()));

        // 翻转
        if (args.isFlipHorizontal()) {
            args.getStack().mulPose(Vector3f.YP.rotationDegrees(180));
        }
        if (args.isFlipVertical()) {
            args.getStack().mulPose(Vector3f.XP.rotationDegrees(180));
        }

        // 返回原点
        args.getStack().translate(-tranW, -tranH, 0);

        // 关闭背面剔除
        RenderSystem.disableCull();
        // 绘制方法
        TransformDrawArgs drawArgs = new TransformDrawArgs(args.getStack());
        drawArgs.setX(0).setY(0).setWidth(args.getWidth()).setHeight(args.getHeight());
        drawFunc.accept(drawArgs);
        // 恢复背面剔除
        RenderSystem.enableCull();

        // 恢复矩阵状态
        args.getStack().popPose();
    }

    // endregion 绘制纹理

    // region 绘制文字

    public static void drawString(MatrixStack matrixStack, FontRenderer font, String text, double x, double y) {
        AbstractGuiUtils.drawString(Text.literal(text).setMatrixStack(matrixStack).setFont(font), x, y);
    }

    public static void drawString(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, int argb) {
        AbstractGuiUtils.drawString(Text.literal(text).setColorArgb(argb).setMatrixStack(matrixStack).setFont(font), x, y);
    }

    public static void drawString(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, boolean shadow) {
        AbstractGuiUtils.drawString(Text.literal(text).setShadow(shadow).setMatrixStack(matrixStack).setFont(font), x, y);
    }

    public static void drawString(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, int argb, boolean shadow) {
        AbstractGuiUtils.drawString(Text.literal(text).setColorArgb(argb).setShadow(shadow).setMatrixStack(matrixStack).setFont(font), x, y);
    }

    public static void drawString(Text text, double x, double y, EDepth depth) {
        AbstractGuiUtils.renderByDepth(text.getMatrixStack(), depth, (stack) ->
                AbstractGuiUtils.drawString(text, x, y)
        );
    }

    public static void drawString(Text text, double x, double y) {
        AbstractGuiUtils.drawLimitedText(text, x, y, 0, 0, null);
    }

    /**
     * 获取多行文本的高度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextHeight(Text text) {
        return AbstractGuiUtils.multilineTextHeight(text.getFont(), text.getContent());
    }

    /**
     * 获取多行文本的高度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextHeight(FontRenderer font, String text) {
        return StringUtils.replaceLine(text).split("\n").length * font.lineHeight;
    }

    public static int getStringWidth(FontRenderer font, Collection<String> texts) {
        int width = 0;
        for (String s : texts) {
            width = Math.max(width, font.width(s));
        }
        return width;
    }

    public static int getStringHeight(FontRenderer font, Collection<String> texts) {
        return AbstractGuiUtils.multilineTextHeight(font, String.join("\n", texts));
    }

    public static int getTextWidth(FontRenderer font, Collection<Text> texts) {
        int width = 0;
        for (Text text : texts) {
            for (String string : StringUtils.replaceLine(text.getContent()).split("\n")) {
                width = Math.max(width, font.width(string));
            }
        }
        return width;
    }

    public static int getTextHeight(FontRenderer font, Collection<Text> texts) {
        return AbstractGuiUtils.multilineTextHeight(font, texts.stream().map(Text::getContent).collect(Collectors.joining("\n")));
    }

    /**
     * 获取多行文本的宽度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextWidth(Text text) {
        return AbstractGuiUtils.multilineTextWidth(text.getFont(), text.getContent());
    }

    /**
     * 获取多行文本的宽度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextWidth(FontRenderer font, String text) {
        int width = 0;
        if (StringUtils.isNotNullOrEmpty(text)) {
            for (String s : StringUtils.replaceLine(text).split("\n")) {
                width = Math.max(width, font.width(s));
            }
        }
        return width;
    }

    /**
     * 绘制多行文本，以\n为换行符
     *
     * @param text  要绘制的文本
     * @param x     绘制的X坐标
     * @param y     绘制的Y坐标
     * @param argbs 文本颜色
     */
    public static void drawMultilineText(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, int... argbs) {
        AbstractGuiUtils.drawMultilineText(Text.literal(text).setMatrixStack(matrixStack).setFont(font), x, y, argbs);
    }

    /**
     * 绘制多行文本，以\n为换行符
     *
     * @param text  要绘制的文本
     * @param x     绘制的X坐标
     * @param y     绘制的Y坐标
     * @param argbs 文本颜色
     */
    public static void drawMultilineText(@NonNull Text text, double x, double y, int... argbs) {
        if (StringUtils.isNotNullOrEmpty(text.getContent())) {
            String[] lines = StringUtils.replaceLine(text.getContent()).split("\n");
            for (int i = 0; i < lines.length; i++) {
                int argb;
                if (argbs.length == lines.length) {
                    argb = argbs[i];
                } else if (argbs.length > 0) {
                    argb = argbs[i % argbs.length];
                } else {
                    argb = text.getColorArgb();
                }
                AbstractGuiUtils.drawString(text.copy().setText(lines[i]).setColorArgb(argb), x, y + i * text.getFont().lineHeight);
            }
        }
    }

    /**
     * 绘制限制长度的文本，超出部分末尾以省略号表示
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param argb     文本颜色
     */
    public static void drawLimitedText(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, int maxWidth, int argb) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setMatrixStack(matrixStack).setFont(font).setColorArgb(argb).setShadow(true), x, y, maxWidth, 0, EnumEllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分末尾以省略号表示
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param argb     文本颜色
     * @param shadow   是否显示阴影
     */
    public static void drawLimitedText(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, int maxWidth, int argb, boolean shadow) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setMatrixStack(matrixStack).setFont(font).setColorArgb(argb).setShadow(shadow), x, y, maxWidth, 0, EnumEllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param position 省略号位置（开头、中间、结尾）
     * @param argb     文本颜色
     */
    public static void drawLimitedText(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, int maxWidth, EnumEllipsisPosition position, int argb) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setMatrixStack(matrixStack).setFont(font).setColorArgb(argb).setShadow(true), x, y, maxWidth, 0, position);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param position 省略号位置（开头、中间、结尾）
     * @param argb     文本颜色
     * @param shadow   是否显示阴影
     */
    public static void drawLimitedText(MatrixStack matrixStack, FontRenderer font, String text, double x, double y, int maxWidth, EnumEllipsisPosition position, int argb, boolean shadow) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setMatrixStack(matrixStack).setFont(font).setColorArgb(argb).setShadow(shadow), x, y, maxWidth, 0, position);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     */
    public static void drawLimitedText(Text text, double x, double y, int maxWidth) {
        AbstractGuiUtils.drawLimitedText(text, x, y, maxWidth, 0, EnumEllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param maxLine  文本显示的最大行数
     */
    public static void drawLimitedText(Text text, double x, double y, int maxWidth, int maxLine) {
        AbstractGuiUtils.drawLimitedText(text, x, y, maxWidth, maxLine, EnumEllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param position 省略号位置（开头、中间、结尾）
     */
    public static void drawLimitedText(Text text, double x, double y, int maxWidth, EnumEllipsisPosition position) {
        AbstractGuiUtils.drawLimitedText(text, x, y, maxWidth, 0, position);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param maxLine  文本显示的最大行数
     * @param position 省略号位置（开头、中间、结尾）
     */
    public static void drawLimitedText(Text text, double x, double y, int maxWidth, int maxLine, EnumEllipsisPosition position) {
        if (StringUtils.isNotNullOrEmpty(text.getContent())) {
            String ellipsis = "...";
            FontRenderer font = text.getFont();
            int ellipsisWidth = font.width(ellipsis);

            // 拆分文本行
            String[] lines = StringUtils.replaceLine(text.getContent()).split("\n");

            // 如果 maxLine <= 1 或 maxLine 大于等于行数，则正常显示所有行
            if (maxLine <= 0 || maxLine >= lines.length) {
                maxLine = lines.length;
                position = null; // 不需要省略号
            }

            List<String> outputLines = new ArrayList<>();
            if (position != null && maxLine > 1) {
                switch (position) {
                    case START:
                        // 显示最后 maxLine 行，开头加省略号
                        outputLines.add(ellipsis);
                        outputLines.addAll(Arrays.asList(lines).subList(lines.length - maxLine + 1, lines.length));
                        break;
                    case MIDDLE:
                        // 显示前后各一部分，中间加省略号
                        int midStart = maxLine / 2;
                        int midEnd = lines.length - (maxLine - midStart) + 1;
                        outputLines.addAll(Arrays.asList(lines).subList(0, midStart));
                        outputLines.add(ellipsis);
                        outputLines.addAll(Arrays.asList(lines).subList(midEnd, lines.length));
                        break;
                    case END:
                    default:
                        // 显示前 maxLine 行，结尾加省略号
                        outputLines.addAll(Arrays.asList(lines).subList(0, maxLine - 1));
                        outputLines.add(ellipsis);
                        break;
                }
            } else {
                if (maxLine == 1) {
                    outputLines.add(lines[0]);
                } else {
                    // 正常显示所有行
                    outputLines.addAll(Arrays.asList(lines));
                }
            }

            // 绘制文本
            int index = 0;
            int maxLineWidth = AbstractGuiUtils.multilineTextWidth(text);
            maxLineWidth = maxLine > 0 ? Math.min(maxLineWidth, maxWidth) : maxLineWidth;
            for (String line : outputLines) {
                // 如果宽度超出 maxWidth，进行截断并加省略号
                if (maxWidth > 0 && font.width(line) > maxWidth) {
                    if (position == EnumEllipsisPosition.START) {
                        // 截断前部
                        while (font.width(ellipsis + line) > maxWidth && line.length() > 1) {
                            line = line.substring(1);
                        }
                        line = ellipsis + line;
                    } else if (position == EnumEllipsisPosition.END) {
                        // 截断后部
                        while (font.width(line + ellipsis) > maxWidth && line.length() > 1) {
                            line = line.substring(0, line.length() - 1);
                        }
                        line = line + ellipsis;
                    } else {
                        // 截断两侧（默认处理）
                        int halfWidth = (maxWidth - ellipsisWidth) / 2;
                        String start = line, end = line;
                        while (font.width(start) > halfWidth && start.length() > 1) {
                            start = start.substring(0, start.length() - 1);
                        }
                        while (font.width(end) > halfWidth && end.length() > 1) {
                            end = end.substring(1);
                        }
                        line = start + ellipsis + end;
                    }
                }

                // 计算水平偏移
                float xOffset;
                switch (text.getAlign()) {
                    case CENTER:
                        xOffset = (maxLineWidth - font.width(line)) / 2.0f;
                        break;
                    case RIGHT:
                        xOffset = maxLineWidth - font.width(line);
                        break;
                    default:
                        xOffset = 0;
                        break;
                }

                // 绘制每行文本
                MatrixStack matrixStack = text.getMatrixStack();
                if (!text.isBgColorEmpty()) {
                    AbstractGuiUtils.fill(matrixStack, (int) (x + xOffset), (int) (y + index * font.lineHeight), font.width(line), font.lineHeight, text.getBgColorArgb());
                }
                if (text.isShadow()) {
                    font.drawShadow(matrixStack, text.copyWithoutChildren().setText(line).toComponent().toTextComponent(SakuraUtils.getClientLanguage()), (float) x + xOffset, (float) y + index * font.lineHeight, text.getColorArgb());
                } else {
                    font.draw(matrixStack, text.copyWithoutChildren().setText(line).toComponent().toTextComponent(SakuraUtils.getClientLanguage()), (float) x + xOffset, (float) y + index * font.lineHeight, text.getColorArgb());
                }

                index++;
            }
        }
    }

    // endregion 绘制文字

    // region 绘制图标

    /**
     * 绘制效果图标
     *
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param width          目标矩形的宽度，决定了图像在屏幕上的宽度
     * @param height         目标矩形的高度，决定了图像在屏幕上的高度
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(MatrixStack matrixStack, FontRenderer font, EffectInstance effectInstance, ResourceLocation textureLocation, TextureCoordinate textureCoordinate, int x, int y, int width, int height, boolean showText) {
        ResourceLocation effectIcon = TextureUtils.getEffectTexture(effectInstance);
        if (effectIcon == null) {
            AbstractGuiUtils.bindTexture(textureLocation);
            Coordinate buffUV = textureCoordinate.getBuffUV();
            AbstractGuiUtils.blit(matrixStack, x, y, width, height, (float) buffUV.getU0(), (float) buffUV.getV0(), buffUV.getUWidth(), buffUV.getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        } else {
            AbstractGuiUtils.bindTexture(effectIcon);
            AbstractGuiUtils.blit(matrixStack, x, y, 0, 0, width, height, width, height);
        }
        if (showText) {
            // 效果等级
            if (effectInstance.getAmplifier() >= 0) {
                Component amplifierString = Component.literal(StringUtils.intToRoman(effectInstance.getAmplifier() + 1));
                int amplifierWidth = font.width(amplifierString.toString());
                float fontX = x + width - (float) amplifierWidth / 2;
                float fontY = y - 1;
                int argb = 0xFFFFFFFF;
                font.drawShadow(matrixStack, amplifierString.setColorArgb(argb).toTextComponent(), fontX, fontY, argb);
            }
            // 效果持续时间
            if (effectInstance.getDuration() > 0) {
                Component durationString = Component.literal(DateUtils.toMaxUnitString(effectInstance.getDuration(), DateUtils.DateUnit.SECOND, 0, 1));
                int durationWidth = font.width(durationString.toString());
                float fontX = x + width - (float) durationWidth / 2 - 2;
                float fontY = y + (float) height / 2 + 1;
                int argb = 0xFFFFFFFF;
                font.drawShadow(matrixStack, durationString.setColorArgb(argb).toTextComponent(), fontX, fontY, argb);
            }
        }
    }

    /**
     * 绘制效果图标
     *
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param width          目标矩形的宽度，决定了图像在屏幕上的宽度
     * @param height         目标矩形的高度，决定了图像在屏幕上的高度
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(MatrixStack matrixStack, FontRenderer font, EffectInstance effectInstance, int x, int y, int width, int height, boolean showText) {
        AbstractGuiUtils.drawEffectIcon(matrixStack, font, effectInstance, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), x, y, width, height, showText);
    }

    /**
     * 绘制效果图标
     *
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(MatrixStack matrixStack, FontRenderer font, EffectInstance effectInstance, int x, int y, boolean showText) {
        AbstractGuiUtils.drawEffectIcon(matrixStack, font, effectInstance, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, showText);
    }

    /**
     * 绘制自定义图标
     *
     * @param reward          待绘制的奖励
     * @param textureLocation 纹理位置
     * @param textureUV       纹理坐标
     * @param x               矩形的左上角x坐标
     * @param y               矩形的左上角y坐标
     * @param totalWidth      纹理总宽度
     * @param totalHeight     纹理总高度
     * @param showText        是否显示物品数量等信息
     */
    public static void drawCustomIcon(MatrixStack matrixStack, FontRenderer font, Reward reward, ResourceLocation textureLocation, Coordinate textureUV, int x, int y, int totalWidth, int totalHeight, boolean showText) {
        AbstractGuiUtils.bindTexture(textureLocation);
        AbstractGuiUtils.blit(matrixStack, x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, (float) textureUV.getU0(), (float) textureUV.getV0(), (int) textureUV.getUWidth(), (int) textureUV.getVHeight(), totalWidth, totalHeight);
        if (showText) {
            Component num = Component.literal(String.valueOf((Integer) RewardManager.deserializeReward(reward)));
            int numWidth = font.width(num.toString());
            float fontX = x + ITEM_ICON_SIZE - (float) numWidth / 2 - 2;
            float fontY = y + (float) ITEM_ICON_SIZE - font.lineHeight + 2;
            int argb = 0xFFFFFFFF;
            font.drawShadow(matrixStack, num.setColorArgb(argb).toTextComponent(), fontX, fontY, argb);
        }
    }

    public static void renderItem(ItemRenderer itemRenderer, FontRenderer font, ItemStack itemStack, int x, int y, boolean showText) {
        itemRenderer.renderGuiItem(itemStack, x, y);
        if (showText) {
            itemRenderer.renderGuiItemDecorations(font, itemStack, x, y, String.valueOf(itemStack.getCount()));
        }
    }

    /**
     * 渲染奖励图标
     *
     * @param reward   待绘制的奖励
     * @param x        图标的x坐标
     * @param y        图标的y坐标
     * @param showText 是否显示物品数量等信息
     */
    public static void renderCustomReward(MatrixStack matrixStack, ItemRenderer itemRenderer, FontRenderer font, ResourceLocation textureLocation, TextureCoordinate textureUV, Reward reward, int x, int y, boolean showText) {
        AbstractGuiUtils.renderCustomReward(matrixStack, itemRenderer, font, textureLocation, textureUV, reward, x, y, showText, true);
    }

    /**
     * 渲染奖励图标
     *
     * @param reward      待绘制的奖励
     * @param x           图标的x坐标
     * @param y           图标的y坐标
     * @param showText    是否显示物品数量等信息
     * @param showQuality 是否显示奖励概率品质颜色
     */
    public static void renderCustomReward(MatrixStack matrixStack, ItemRenderer itemRenderer, FontRenderer font, ResourceLocation textureLocation, TextureCoordinate textureUV, Reward reward, int x, int y, boolean showText, boolean showQuality) {
        // 物品
        if (reward.getType().equals(EnumRewardType.ITEM)) {
            ItemStack itemStack = RewardManager.deserializeReward(reward);
            renderItem(itemRenderer, font, itemStack, x, y, showText);
        }
        // 效果
        else if (reward.getType().equals(EnumRewardType.EFFECT)) {
            EffectInstance effectInstance = RewardManager.deserializeReward(reward);
            AbstractGuiUtils.drawEffectIcon(matrixStack, font, effectInstance, textureLocation, textureUV, x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, showText);
        }
        // 经验点
        else if (reward.getType().equals(EnumRewardType.EXP_POINT)) {
            AbstractGuiUtils.drawCustomIcon(matrixStack, font, reward, textureLocation, textureUV.getPointUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 经验等级
        else if (reward.getType().equals(EnumRewardType.EXP_LEVEL)) {
            AbstractGuiUtils.drawCustomIcon(matrixStack, font, reward, textureLocation, textureUV.getLevelUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 补签卡
        else if (reward.getType().equals(EnumRewardType.SIGN_IN_CARD)) {
            AbstractGuiUtils.drawCustomIcon(matrixStack, font, reward, textureLocation, textureUV.getCardUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 消息
        else if (reward.getType().equals(EnumRewardType.MESSAGE)) {
            // 这玩意不是Integer类型也没有数量, 不能showText
            AbstractGuiUtils.drawCustomIcon(matrixStack, font, reward, textureLocation, textureUV.getMessageUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), false);
        }
        // 进度
        else if (reward.getType().equals(EnumRewardType.ADVANCEMENT)) {
            ResourceLocation resourceLocation = RewardManager.deserializeReward(reward);
            AdvancementData advancementData = SakuraSignIn.getAdvancementData().stream()
                    .filter(data -> data.getId().toString().equalsIgnoreCase(resourceLocation.toString()))
                    .findFirst().orElse(new AdvancementData(resourceLocation, null));
            itemRenderer.renderGuiItem(advancementData.getDisplayInfo().getIcon(), x, y);
        }
        // 指令
        else if (reward.getType().equals(EnumRewardType.COMMAND)) {
            renderItem(itemRenderer, font, new ItemStack(Items.REPEATING_COMMAND_BLOCK), x, y, false);
        }
        if (showText && showQuality && reward.getProbability().compareTo(BigDecimal.ONE) != 0) {
            AbstractGuiUtils.renderByDepth(matrixStack, (stack) ->
                    AbstractGuiUtils.drawString(stack, font, "?", x - 1, y - 1, AbstractGuiUtils.getProbabilityArgb(reward.getProbability().doubleValue()))
            );
        }
    }

    public static int getProbabilityArgb(double probability) {
        int argb = 0xFF000000;
        // 默认不渲染
        if (probability == 1) {
            argb = 0x00FFFFFF;
        }
        // 深灰色，最低级
        else if (probability >= 0.9) {
            argb = 0xEFA9A9A9;
        }
        // 灰色，低级
        else if (probability >= 0.8) {
            argb = 0xEFC0C0C0;
        }
        // 白色，普通
        else if (probability >= 0.7) {
            argb = 0xEFFFFFFF;
        }
        // 亮绿色，良好
        else if (probability >= 0.6) {
            argb = 0xEF32CD32;
        }
        // 深绿色，优秀
        else if (probability >= 0.5) {
            argb = 0xEF228B22;
        }
        // 蓝色，稀有
        else if (probability >= 0.4) {
            argb = 0xEF1E90FF;
        }
        // 深蓝色，稀有
        else if (probability >= 0.3) {
            argb = 0xEF4682B4;
        }
        // 紫色，史诗
        else if (probability >= 0.2) {
            argb = 0xEFA020F0;
        }
        // 金色，传说
        else if (probability >= 0.1) {
            argb = 0xEFFFD700;
        }
        // 橙红色，终极
        else if (probability > 0) {
            argb = 0xEFFF4500;
        }
        return argb;
    }

    //  endregion 绘制图标

    //  region 绘制形状

    /**
     * 绘制一个“像素”矩形
     *
     * @param x    像素的 X 坐标
     * @param y    像素的 Y 坐标
     * @param argb 像素的颜色
     */
    public static void drawPixel(MatrixStack matrixStack, int x, int y, int argb) {
        AbstractGui.fill(matrixStack, x, y, x + 1, y + 1, argb);
    }

    /**
     * 绘制一个正方形
     */
    public static void fill(MatrixStack matrixStack, int x, int y, int width, int argb) {
        AbstractGuiUtils.fill(matrixStack, x, y, width, width, argb);
    }

    /**
     * 绘制一个矩形
     */
    public static void fill(MatrixStack matrixStack, int x, int y, int width, int height, int argb) {
        AbstractGuiUtils.fill(matrixStack, x, y, width, height, argb, 0);
    }

    /**
     * 绘制一个圆角矩形
     *
     * @param x      矩形的左上角X坐标
     * @param y      矩形的左上角Y坐标
     * @param width  矩形的宽度
     * @param height 矩形的高度
     * @param argb   矩形的颜色
     * @param radius 圆角半径(0-10)
     */
    public static void fill(MatrixStack matrixStack, int x, int y, int width, int height, int argb, int radius) {
        // 如果半径为0，则直接绘制普通矩形
        if (radius <= 0) {
            AbstractGui.fill(matrixStack, x, y, x + width, y + height, argb);
            return;
        }

        // 限制半径最大值为10
        radius = Math.min(radius, 10);

        // 1. 绘制中间的矩形部分（去掉圆角占用的区域）
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + radius + 1, width - 2 * (radius + 1), height - 2 * (radius + 1), argb);

        // 2. 绘制四条边（去掉圆角占用的部分）
        // 上边
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y, width - 2 * radius - 2, radius, argb);
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + radius, width - 2 * (radius + 1), 1, argb);
        // 下边
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + height - radius, width - 2 * radius - 2, radius, argb);
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + height - radius - 1, width - 2 * (radius + 1), 1, argb);
        // 左边
        AbstractGuiUtils.fill(matrixStack, x, y + radius + 1, radius, height - 2 * radius - 2, argb);
        AbstractGuiUtils.fill(matrixStack, x + radius, y + radius + 1, 1, height - 2 * (radius + 1), argb);
        // 右边
        AbstractGuiUtils.fill(matrixStack, x + width - radius, y + radius + 1, radius, height - 2 * radius - 2, argb);
        AbstractGuiUtils.fill(matrixStack, x + width - radius - 1, y + radius + 1, 1, height - 2 * (radius + 1), argb);

        // 3. 绘制四个圆角
        // 左上角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + radius, y + radius, radius, argb, 1);
        // 右上角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + width - radius - 1, y + radius, radius, argb, 2);
        // 左下角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + radius, y + height - radius - 1, radius, argb, 3);
        // 右下角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + width - radius - 1, y + height - radius - 1, radius, argb, 4);
    }

    /**
     * 绘制一个圆的四分之一部分（圆角辅助函数）
     *
     * @param centerX  圆角中心点X坐标
     * @param centerY  圆角中心点Y坐标
     * @param radius   圆角半径
     * @param argb     圆角颜色
     * @param quadrant 指定绘制的象限（1=左上，2=右上，3=左下，4=右下）
     */
    private static void drawCircleQuadrant(MatrixStack matrixStack, int centerX, int centerY, int radius, int argb, int quadrant) {
        for (int dx = 0; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    switch (quadrant) {
                        case 1: // 左上角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX - dx, centerY - dy, argb);
                            break;
                        case 2: // 右上角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX + dx, centerY - dy, argb);
                            break;
                        case 3: // 左下角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX - dx, centerY + dy, argb);
                            break;
                        case 4: // 右下角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX + dx, centerY + dy, argb);
                            break;
                    }
                }
            }
        }
    }

    /**
     * 绘制一个矩形边框
     *
     * @param thickness 边框厚度
     * @param argb      边框颜色
     */
    public static void fillOutLine(MatrixStack matrixStack, int x, int y, int width, int height, int thickness, int argb) {
        // 上边
        AbstractGuiUtils.fill(matrixStack, x, y, width, thickness, argb);
        // 下边
        AbstractGuiUtils.fill(matrixStack, x, y + height - thickness, width, thickness, argb);
        // 左边
        AbstractGuiUtils.fill(matrixStack, x, y, thickness, height, argb);
        // 右边
        AbstractGuiUtils.fill(matrixStack, x + width - thickness, y, thickness, height, argb);
    }

    /**
     * 绘制一个圆角矩形边框
     *
     * @param x         矩形左上角X坐标
     * @param y         矩形左上角Y坐标
     * @param width     矩形宽度
     * @param height    矩形高度
     * @param thickness 边框厚度
     * @param argb      边框颜色
     * @param radius    圆角半径（0-10）
     */
    public static void fillOutLine(MatrixStack matrixStack, int x, int y, int width, int height, int thickness, int argb, int radius) {
        if (radius <= 0) {
            // 如果没有圆角，直接绘制普通边框
            AbstractGuiUtils.fillOutLine(matrixStack, x, y, width, height, thickness, argb);
        } else {
            // 限制圆角半径的最大值为10
            radius = Math.min(radius, 10);

            // 1. 绘制四条边（去掉圆角区域）
            // 上边
            AbstractGuiUtils.fill(matrixStack, x + radius, y, width - 2 * radius, thickness, argb);
            // 下边
            AbstractGuiUtils.fill(matrixStack, x + radius, y + height - thickness, width - 2 * radius, thickness, argb);
            // 左边
            AbstractGuiUtils.fill(matrixStack, x, y + radius, thickness, height - 2 * radius, argb);
            // 右边
            AbstractGuiUtils.fill(matrixStack, x + width - thickness, y + radius, thickness, height - 2 * radius, argb);

            // 2. 绘制四个圆角
            // 左上角
            drawCircleBorder(matrixStack, x + radius, y + radius, radius, thickness, argb, 1);
            // 右上角
            drawCircleBorder(matrixStack, x + width - radius - 1, y + radius, radius, thickness, argb, 2);
            // 左下角
            drawCircleBorder(matrixStack, x + radius, y + height - radius - 1, radius, thickness, argb, 3);
            // 右下角
            drawCircleBorder(matrixStack, x + width - radius - 1, y + height - radius - 1, radius, thickness, argb, 4);
        }
    }

    /**
     * 绘制一个圆角的边框区域（辅助函数）
     *
     * @param centerX   圆角中心点X坐标
     * @param centerY   圆角中心点Y坐标
     * @param radius    圆角半径
     * @param thickness 边框厚度
     * @param argb      边框颜色
     * @param quadrant  指定绘制的象限（1=左上，2=右上，3=左下，4=右下）
     */
    private static void drawCircleBorder(MatrixStack matrixStack, int centerX, int centerY, int radius, int thickness, int argb, int quadrant) {
        for (int dx = 0; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                if (Math.sqrt(dx * dx + dy * dy) <= radius && Math.sqrt(dx * dx + dy * dy) >= radius - thickness) {
                    switch (quadrant) {
                        case 1: // 左上角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX - dx, centerY - dy, argb);
                            break;
                        case 2: // 右上角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX + dx, centerY - dy, argb);
                            break;
                        case 3: // 左下角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX - dx, centerY + dy, argb);
                            break;
                        case 4: // 右下角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX + dx, centerY + dy, argb);
                            break;
                    }
                }
            }
        }
    }

    //  endregion 绘制形状

    //  region 绘制弹出层提示

    /**
     * 绘制弹出层消息
     *
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public static void drawPopupMessage(MatrixStack matrixStack, FontRenderer font, String message, double x, double y, int screenWidth, int screenHeight) {
        AbstractGuiUtils.drawPopupMessage(matrixStack, font, message, x, y, screenWidth, screenHeight, 0xFFFFFFFF, 0xAA000000);
    }

    /**
     * 绘制弹出层消息
     *
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param bgArgb       背景颜色
     * @param textArgb     文本颜色
     */
    public static void drawPopupMessage(MatrixStack matrixStack, FontRenderer font, String message, double x, double y, int screenWidth, int screenHeight, int textArgb, int bgArgb) {
        AbstractGuiUtils.drawPopupMessage(matrixStack, font, message, x, y, screenWidth, screenHeight, 2, textArgb, bgArgb);
    }

    /**
     * 绘制弹出层消息
     *
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param margin       弹出层的外边距(外层背景与屏幕边缘)
     * @param bgArgb       背景颜色
     * @param textArgb     文本颜色
     */
    public static void drawPopupMessage(MatrixStack matrixStack, FontRenderer font, String message, double x, double y, int screenWidth, int screenHeight, int margin, int textArgb, int bgArgb) {
        AbstractGuiUtils.drawPopupMessage(matrixStack, font, message, x, y, screenWidth, screenHeight, margin, margin, textArgb, bgArgb);
    }

    /**
     * 绘制弹出层消息
     *
     * @param x            鼠标坐标X
     * @param y            鼠标坐标Y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param margin       弹出层的外边距(外层背景与屏幕边缘)
     * @param padding      弹出层的内边距(外层背景与内部文字)
     * @param bgArgb       背景颜色
     * @param textArgb     文本颜色
     */
    public static void drawPopupMessage(MatrixStack matrixStack, FontRenderer font, String message, double x, double y, int screenWidth, int screenHeight, int margin, int padding, int textArgb, int bgArgb) {
        AbstractGuiUtils.drawPopupMessage(Text.literal(message).setMatrixStack(matrixStack).setFont(font).setColorArgb(textArgb), x, y, screenWidth, screenHeight, margin, padding, bgArgb);
    }

    /**
     * 绘制弹出层消息
     *
     * @param text         消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, 0xAA000000);
    }

    /**
     * 绘制弹出层消息
     *
     * @param text         消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param bgArgb       背景颜色
     */
    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int bgArgb) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, 2, bgArgb);
    }

    /**
     * 绘制弹出层消息
     *
     * @param text         消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param margin       弹出层的外边距(外层背景与屏幕边缘)
     * @param bgArgb       背景颜色
     */
    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int margin, int bgArgb) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, margin, margin, bgArgb);
    }

    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int margin, int padding, int bgAgb) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, margin, padding, bgAgb, true);
    }

    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int margin, int padding, int bgArgb, boolean inScreen) {
        // 计算消息宽度和高度, 并添加一些边距
        int msgWidth = AbstractGuiUtils.multilineTextWidth(text) + padding;
        int msgHeight = AbstractGuiUtils.multilineTextHeight(text) + padding;

        // 计算调整后的坐标
        double adjustedX = x;
        double adjustedY = y;
        if (inScreen) {
            if (msgWidth >= screenWidth) msgWidth = screenWidth - padding * 2;
            if (msgHeight >= screenHeight) msgHeight = screenHeight - padding * 2;

            // 初始化调整后的坐标
            adjustedX = x - msgWidth / 2.0; // 横向居中
            adjustedY = y - msgHeight - 5; // 放置在鼠标上方（默认偏移 5 像素）

            // 检查顶部空间是否充足
            boolean hasTopSpace = adjustedY >= margin;
            // 检查左右空间是否充足
            boolean hasLeftSpace = adjustedX >= margin;
            boolean hasRightSpace = adjustedX + msgWidth <= screenWidth - margin;

            // 如果顶部空间不足，调整到鼠标下方
            if (!hasTopSpace) {
                adjustedY = y + 1 + 5;
            }
            // 如果顶部空间充足
            else {
                // 如果左侧空间不足，靠右
                if (!hasLeftSpace) {
                    adjustedX = margin;
                }
                // 如果右侧空间不足，靠左
                else if (!hasRightSpace) {
                    adjustedX = screenWidth - msgWidth - margin;
                }
            }

            // 如果调整后仍然超出屏幕范围，强制限制在屏幕内
            adjustedX = Math.max(margin, Math.min(adjustedX, screenWidth - msgWidth - margin));
            adjustedY = Math.max(margin, Math.min(adjustedY, screenHeight - msgHeight - margin));
        }

        double finalAdjustedX = adjustedX;
        double finalAdjustedY = adjustedY;
        int finalMsgWidth = msgWidth;
        int finalMsgHeight = msgHeight;
        AbstractGuiUtils.renderByDepth(text.getMatrixStack(), EDepth.POPUP_TIPS, (stack) -> {
            // 在计算完的坐标位置绘制消息框背景
            AbstractGui.fill(text.getMatrixStack(), (int) finalAdjustedX, (int) finalAdjustedY, (int) (finalAdjustedX + finalMsgWidth), (int) (finalAdjustedY + finalMsgHeight), bgArgb);
            // 绘制消息文字
            AbstractGuiUtils.drawLimitedText(text, finalAdjustedX + (float) padding / 2, finalAdjustedY + (float) padding / 2, finalMsgWidth, finalMsgHeight / text.getFont().lineHeight, EnumEllipsisPosition.MIDDLE);
        });
    }

    //  endregion 绘制弹出层提示

    // region 杂项

    /**
     * 获取指定坐标点像素颜色
     */
    public static int getPixelArgb(double guiX, double guiY) {
        Minecraft mc = Minecraft.getInstance();
        MainWindow window = mc.getWindow();

        // 将 GUI 坐标（左上为原点）转换为物理屏幕坐标（左下为原点）
        int pixelX = (int) (guiX * window.getGuiScale());
        int pixelY = (int) (guiY * window.getGuiScale());
        int glY = window.getHeight() - pixelY - 1;

        // 创建 ByteBuffer 存储像素数据（RGBA）
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(pixelX, glY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        int r = buffer.get(0) & 0xFF;
        int g = buffer.get(1) & 0xFF;
        int b = buffer.get(2) & 0xFF;
        int a = buffer.get(3) & 0xFF;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 获取颜色的亮度
     */
    public static float getBrightness(int rgba) {
        int r = (rgba >> 16) & 0xFF;
        int g = (rgba >> 8) & 0xFF;
        int b = rgba & 0xFF;
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
    }

    // endregion 杂项

    // region 重写方法签名

    public static TextFieldWidget newTextFieldWidget(FontRenderer font, int x, int y, int width, int height, Component content) {
        return new TextFieldWidget(font, x, y, width, height, content.toTextComponent());
    }

    public static Button newButton(int x, int y, int width, int height, Component content, Button.IPressable onPress) {
        return new Button(x, y, width, height, content.toTextComponent(), onPress);
    }

    // endregion 重写方法签名
}
