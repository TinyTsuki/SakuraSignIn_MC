package xin.vanilla.mc.util;

import com.mojang.blaze3d.platform.GlStateManager;
import lombok.Getter;
import lombok.NonNull;
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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.enums.ERewardType;
import xin.vanilla.mc.network.AdvancementData;
import xin.vanilla.mc.rewards.Reward;
import xin.vanilla.mc.rewards.RewardManager;
import xin.vanilla.mc.screen.component.Text;
import xin.vanilla.mc.screen.coordinate.Coordinate;
import xin.vanilla.mc.screen.coordinate.TextureCoordinate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AbstractGui工具类
 */
@OnlyIn(Dist.CLIENT)
public class AbstractGuiUtils {
    public static final Logger LOGGER = LogManager.getLogger();

    public final static int ITEM_ICON_SIZE = 16;

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
     * 设置深度
     */
    public static void setDepth() {
        AbstractGuiUtils.setDepth(EDepth.FOREGROUND);
    }

    /**
     * 设置深度
     *
     * @param depth 深度
     */
    public static void setDepth(EDepth depth) {
        GlStateManager.disableDepthTest();
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, depth.getDepth());
    }

    /**
     * 重置深度
     */
    public static void resetDepth() {
        GL11.glPopMatrix();
        GlStateManager.enableDepthTest();
    }

    // endregion 设置深度

    // region 绘制纹理

    public static void blit(int x0, int y0, int z, int destWidth, int destHeight, TextureAtlasSprite sprite) {
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        AbstractGui.blit(x0, y0, z, destWidth, destHeight, sprite);
    }

    public static void blit(int x0, int y0, int z, float u0, float v0, int width, int height, int textureHeight, int textureWidth) {
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        AbstractGui.blit(x0, y0, z, u0, v0, width, height, textureHeight, textureWidth);
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
    public static void blit(int x0, int y0, int destWidth, int destHeight, float u0, float v0, int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        AbstractGui.blit(x0, y0, destWidth, destHeight, u0, v0, srcWidth, srcHeight, textureWidth, textureHeight);
    }

    public static void blit(int x0, int y0, float u0, float v0, int destWidth, int destHeight, int textureWidth, int textureHeight) {
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlphaTest();
        GlStateManager.disableLighting();
        AbstractGui.blit(x0, y0, u0, v0, destWidth, destHeight, textureWidth, textureHeight);
    }

    /**
     * 绘制旋转的纹理
     *
     * @param texture           纹理
     * @param textureCoordinate 纹理坐标
     * @param coordinate        绘制相对坐标
     * @param baseX             绘制的基础坐标X
     * @param baseY             绘制的基础坐标Y
     * @param scale             Screen纹理缩放比例
     * @param angle             旋转角度
     * @param flipHorizontal    水平翻转
     * @param flipVertical      垂直翻转
     */
    public static void renderRotatedTexture(ResourceLocation texture, TextureCoordinate textureCoordinate, Coordinate coordinate, double baseX, double baseY, double scale, double angle, boolean flipHorizontal, boolean flipVertical) {
        double x = baseX + coordinate.getX() * scale;
        double y = baseY + coordinate.getY() * scale;
        int width = (int) (coordinate.getWidth() * scale);
        int height = (int) (coordinate.getHeight() * scale);
        float u0 = (float) coordinate.getU0();
        float v0 = (float) coordinate.getV0();
        int uWidth = (int) coordinate.getUWidth();
        int vHeight = (int) coordinate.getVHeight();
        // 绑定纹理
        Minecraft.getInstance().getTextureManager().bind(texture);
        // 保存当前矩阵状态
        GL11.glPushMatrix();
        // 平移到旋转中心 (x + width / 2, y + height / 2)
        GL11.glTranslatef((float) (x + width / 2.0), (float) (y + height / 2.0), 0);
        // 进行旋转，angle 是旋转角度，单位是度数，绕 Z 轴旋转
        GL11.glRotatef((float) angle, 0, 0, 1);
        // 左右翻转
        if (flipHorizontal) {
            u0 += uWidth;
            uWidth = -uWidth;
        }
        // 上下翻转
        if (flipVertical) {
            v0 += vHeight;
            vHeight = -vHeight;
        }
        // 平移回原点
        GL11.glTranslatef((float) (-width / 2.0), (float) (-height / 2.0), 0);
        // 启用混合模式来正确处理透明度
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // 绘制纹理
        AbstractGuiUtils.blit(0, 0, width, height, u0, v0, uWidth, vHeight, textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        GlStateManager.disableBlend();
        // 恢复矩阵状态
        GL11.glPopMatrix();
    }

    /**
     * 绘制 颤抖的 纹理
     *
     * @param texture            纹理
     * @param textureCoordinate  纹理坐标
     * @param coordinate         绘制相对坐标
     * @param baseX              绘制的基础坐标X
     * @param baseY              绘制的基础坐标Y
     * @param scale              Screen纹理缩放比例
     * @param affectLight        是否受光照影响
     * @param tremblingAmplitude 颤抖幅度
     */
    public static void renderTremblingTexture(ResourceLocation texture, TextureCoordinate textureCoordinate, Coordinate coordinate, double baseX, double baseY, double scale, boolean affectLight, double tremblingAmplitude) {
        double x = baseX + coordinate.getX() * scale;
        double y = baseY + coordinate.getY() * scale;
        int width = (int) (coordinate.getWidth() * scale);
        int height = (int) (coordinate.getHeight() * scale);
        float u0 = (float) coordinate.getU0();
        float v0 = (float) coordinate.getV0();
        int uWidth = (int) coordinate.getUWidth();
        int vHeight = (int) coordinate.getVHeight();
        Random random = new Random();
        // 绑定纹理
        Minecraft.getInstance().getTextureManager().bind(texture);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GL11.glPushMatrix();
        // 添加偏移
        if (tremblingAmplitude > 0) {
            if (!affectLight || WorldUtils.getEnvironmentBrightness(Minecraft.getInstance().player) > 4) {
                x += (random.nextFloat() - 0.5) * tremblingAmplitude;
                y += (random.nextFloat() - 0.5) * tremblingAmplitude;
            }
        }
        GL11.glTranslatef((float) x, (float) y, 0);
        // 绘制纹理
        AbstractGuiUtils.blit(0, 0, width, height, u0, v0, uWidth, vHeight, textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        GL11.glPopMatrix();
        GlStateManager.disableBlend();
    }

    // endregion 绘制纹理

    // region 绘制文字

    public static TextFormatting colorFromRgb(int color) {
        TextFormatting textFormatting = TextFormatting.getById(color);
        if (textFormatting == null) {
            LOGGER.warn("Invalid color code: {}", color);
        }
        return textFormatting;
    }

    public static ITextComponent setTextComponentColor(ITextComponent textComponent, int color) {
        return textComponent.withStyle(style -> style.setColor(colorFromRgb(color)));
    }

    public static int getTextComponentColor(ITextComponent textComponent) {
        return AbstractGuiUtils.getTextComponentColor(textComponent, 0xFFFFFFFF);
    }

    public static int getTextComponentColor(ITextComponent textComponent, int defaultColor) {
        return textComponent.getStyle().getColor() == null ? defaultColor : textComponent.getStyle().getColor().getColor();
    }

    public static ITextComponent textToComponent(Text text) {
        return new StringTextComponent(text.getContent()).setStyle(new Style()
                .setColor(colorFromRgb(text.getColor()))
                .setBold(text.isBold())
                .setItalic(text.isItalic())
                .setUnderlined(text.isUnderlined())
                .setStrikethrough(text.isStrikethrough())
                .setObfuscated(text.isObfuscated())
        );
    }

    public static Text componentToText(ITextComponent component) {
        return Text.literal(component.getString())
                .setColor(AbstractGuiUtils.getTextComponentColor(component))
                .setBold(component.getStyle().isBold())
                .setItalic(component.getStyle().isItalic())
                .setUnderlined(component.getStyle().isUnderlined())
                .setStrikethrough(component.getStyle().isStrikethrough())
                .setObfuscated(component.getStyle().isObfuscated());
    }

    public static void drawString(FontRenderer font, String text, float x, float y) {
        AbstractGuiUtils.drawString(Text.literal(text).setFont(font), x, y);
    }

    public static void drawString(FontRenderer font, String text, float x, float y, int color) {
        AbstractGuiUtils.drawString(Text.literal(text).setColor(color).setFont(font), x, y);
    }

    public static void drawString(FontRenderer font, String text, float x, float y, boolean shadow) {
        AbstractGuiUtils.drawString(Text.literal(text).setShadow(shadow).setFont(font), x, y);
    }

    public static void drawString(FontRenderer font, String text, float x, float y, int color, boolean shadow) {
        AbstractGuiUtils.drawString(Text.literal(text).setColor(color).setShadow(shadow).setFont(font), x, y);
    }

    public static void drawString(Text text, float x, float y, EDepth depth) {
        if (depth != null) AbstractGuiUtils.setDepth(depth);
        AbstractGuiUtils.drawString(text, x, y);
        if (depth != null) AbstractGuiUtils.resetDepth();
    }

    public static void drawString(Text text, float x, float y) {
        AbstractGuiUtils.drawLimitedText(text, x, y, 0, 0, null);
    }

    public enum EllipsisPosition {
        START,    // 省略号在文本开头
        MIDDLE,   // 省略号在文本中间
        END       // 省略号在文本结尾
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
     * @param font 字体渲染器
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
     * @param font 字体渲染器
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
     * @param font   字体渲染器
     * @param text   要绘制的文本
     * @param x      绘制的X坐标
     * @param y      绘制的Y坐标
     * @param colors 文本颜色
     */
    public static void drawMultilineText(FontRenderer font, String text, float x, float y, int... colors) {
        AbstractGuiUtils.drawMultilineText(Text.literal(text).setFont(font), x, y, colors);
    }

    /**
     * 绘制多行文本，以\n为换行符
     *
     * @param text   要绘制的文本
     * @param x      绘制的X坐标
     * @param y      绘制的Y坐标
     * @param colors 文本颜色
     */
    public static void drawMultilineText(@NonNull Text text, float x, float y, int... colors) {
        if (StringUtils.isNotNullOrEmpty(text.getContent())) {
            String[] lines = StringUtils.replaceLine(text.getContent()).split("\n");
            for (int i = 0; i < lines.length; i++) {
                int color;
                if (colors.length == lines.length) {
                    color = colors[i];
                } else if (colors.length > 0) {
                    color = colors[i % colors.length];
                } else {
                    color = text.getColor();
                }
                AbstractGuiUtils.drawString(text.copy().setText(lines[i]).setColor(color), x, y + i * text.getFont().lineHeight);
            }
        }
    }

    /**
     * 绘制限制长度的文本，超出部分末尾以省略号表示
     *
     * @param font     字体渲染器
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param color    文本颜色
     */
    public static void drawLimitedText(FontRenderer font, String text, float x, float y, int maxWidth, int color) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setFont(font).setColor(color).setShadow(true), x, y, maxWidth, 0, EllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分末尾以省略号表示
     *
     * @param font     字体渲染器
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param color    文本颜色
     * @param shadow   是否显示阴影
     */
    public static void drawLimitedText(FontRenderer font, String text, float x, float y, int maxWidth, int color, boolean shadow) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setFont(font).setColor(color).setShadow(shadow), x, y, maxWidth, 0, EllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param font     字体渲染器
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param position 省略号位置（开头、中间、结尾）
     * @param color    文本颜色
     */
    public static void drawLimitedText(FontRenderer font, String text, float x, float y, int maxWidth, EllipsisPosition position, int color) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setFont(font).setColor(color).setShadow(true), x, y, maxWidth, 0, position);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param font     字体渲染器
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param position 省略号位置（开头、中间、结尾）
     * @param color    文本颜色
     * @param shadow   是否显示阴影
     */
    public static void drawLimitedText(FontRenderer font, String text, float x, float y, int maxWidth, EllipsisPosition position, int color, boolean shadow) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setFont(font).setColor(color).setShadow(shadow), x, y, maxWidth, 0, position);
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
        AbstractGuiUtils.drawLimitedText(text, x, y, maxWidth, 0, EllipsisPosition.END);
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
        AbstractGuiUtils.drawLimitedText(text, x, y, maxWidth, maxLine, EllipsisPosition.END);
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
    public static void drawLimitedText(Text text, double x, double y, int maxWidth, EllipsisPosition position) {
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
    public static void drawLimitedText(Text text, double x, double y, int maxWidth, int maxLine, EllipsisPosition position) {
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
                    if (position == EllipsisPosition.START) {
                        // 截断前部
                        while (font.width(ellipsis + line) > maxWidth && line.length() > 1) {
                            line = line.substring(1);
                        }
                        line = ellipsis + line;
                    } else if (position == EllipsisPosition.END) {
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
                // 重置为白色, 避免颜色叠加问题
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                if (text.isShadow()) {
                    font.drawShadow(line, (float) x + xOffset, (float) y + index * font.lineHeight, text.getColor());
                } else {
                    font.draw(line, (float) x + xOffset, (float) y + index * font.lineHeight, text.getColor());
                }
                // 绘制下划线
                if (text.isUnderlined()) {
                    // 重置为白色, 避免颜色叠加问题
                    GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    AbstractGui.fill((int) x, (int) (y + font.lineHeight), (int) (x + font.width(line)), (int) (y + font.lineHeight + 1), text.getColor());
                }

                index++;
            }
        }
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
    }

    // endregion 绘制文字

    // region 绘制图标

    /**
     * 绘制效果图标
     *
     * @param font           字体渲染器
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param width          目标矩形的宽度，决定了图像在屏幕上的宽度
     * @param height         目标矩形的高度，决定了图像在屏幕上的高度
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(FontRenderer font, EffectInstance effectInstance, ResourceLocation textureLocation, TextureCoordinate textureCoordinate, int x, int y, int width, int height, boolean showText) {
        ResourceLocation effectIcon = TextureUtils.getEffectTexture(effectInstance);
        if (effectIcon == null) {
            Minecraft.getInstance().getTextureManager().bind(textureLocation);
            Coordinate buffUV = textureCoordinate.getBuffUV();
            AbstractGuiUtils.blit(x, y, width, height, (float) buffUV.getU0(), (float) buffUV.getV0(), (int) buffUV.getUWidth(), (int) buffUV.getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        } else {
            Minecraft.getInstance().getTextureManager().bind(effectIcon);
            AbstractGuiUtils.blit(x, y, 0, 0, width, height, width, height);
        }
        if (showText) {
            // 效果等级
            if (effectInstance.getAmplifier() >= 0) {
                String amplifierString = StringUtils.intToRoman(effectInstance.getAmplifier() + 1);
                int amplifierWidth = font.width(amplifierString);
                float fontX = x + width - (float) amplifierWidth / 2;
                float fontY = y - 1;
                // 重置为白色, 避免颜色叠加问题
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                font.drawShadow(amplifierString, fontX, fontY, 0xFFFFFF);
            }
            // 效果持续时间
            if (effectInstance.getDuration() > 0) {
                String durationString = DateUtils.toMaxUnitString(effectInstance.getDuration(), DateUtils.DateUnit.SECOND, 0, 1);
                int durationWidth = font.width(durationString);
                float fontX = x + width - (float) durationWidth / 2 - 2;
                float fontY = y + (float) height / 2 + 1;
                // 重置为白色, 避免颜色叠加问题
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                font.drawShadow(durationString, fontX, fontY, 0xFFFFFF);
            }
        }
    }

    /**
     * 绘制效果图标
     *
     * @param font           字体渲染器
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param width          目标矩形的宽度，决定了图像在屏幕上的宽度
     * @param height         目标矩形的高度，决定了图像在屏幕上的高度
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(FontRenderer font, EffectInstance effectInstance, int x, int y, int width, int height, boolean showText) {
        AbstractGuiUtils.drawEffectIcon(font, effectInstance, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), x, y, width, height, showText);
    }

    /**
     * 绘制效果图标
     *
     * @param font           字体渲染器
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(FontRenderer font, EffectInstance effectInstance, int x, int y, boolean showText) {
        AbstractGuiUtils.drawEffectIcon(font, effectInstance, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, showText);
    }

    /**
     * 绘制自定义图标
     *
     * @param font            字体渲染器
     * @param reward          待绘制的奖励
     * @param textureLocation 纹理位置
     * @param textureUV       纹理坐标
     * @param x               矩形的左上角x坐标
     * @param y               矩形的左上角y坐标
     * @param totalWidth      纹理总宽度
     * @param totalHeight     纹理总高度
     * @param showText        是否显示物品数量等信息
     */
    public static void drawCustomIcon(FontRenderer font, Reward reward, ResourceLocation textureLocation, Coordinate textureUV, int x, int y, int totalWidth, int totalHeight, boolean showText) {
        Minecraft.getInstance().getTextureManager().bind(textureLocation);
        AbstractGuiUtils.blit(x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, (float) textureUV.getU0(), (float) textureUV.getV0(), (int) textureUV.getUWidth(), (int) textureUV.getVHeight(), totalWidth, totalHeight);
        if (showText) {
            String num = String.valueOf((Integer) RewardManager.deserializeReward(reward));
            int numWidth = font.width(num);
            float fontX = x + ITEM_ICON_SIZE - (float) numWidth / 2 - 2;
            float fontY = y + (float) ITEM_ICON_SIZE - font.lineHeight + 2;
            // 重置为白色, 避免颜色叠加问题
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            font.drawShadow(num, fontX, fontY, 0xFFFFFF);
        }
    }

    public static void renderItem(ItemRenderer itemRenderer, FontRenderer fontRenderer, ItemStack itemStack, int x, int y, boolean showText) {
        // FIXME 方块物品渲染颜色问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        itemRenderer.renderAndDecorateItem(itemStack, x, y);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        if (showText) {
            itemRenderer.renderGuiItemDecorations(fontRenderer, itemStack, x, y, String.valueOf(itemStack.getCount()));
        }
    }

    /**
     * 渲染奖励图标
     *
     * @param itemRenderer 物品渲染器
     * @param fontRenderer 字体渲染器
     * @param reward       待绘制的奖励
     * @param x            图标的x坐标
     * @param y            图标的y坐标
     * @param showText     是否显示物品数量等信息
     */
    public static void renderCustomReward(ItemRenderer itemRenderer, FontRenderer fontRenderer, ResourceLocation textureLocation, TextureCoordinate textureUV, Reward reward, int x, int y, boolean showText) {
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        // 物品
        if (reward.getType().equals(ERewardType.ITEM)) {
            ItemStack itemStack = RewardManager.deserializeReward(reward);
            renderItem(itemRenderer, fontRenderer, itemStack, x, y, showText);
        }
        // 效果
        else if (reward.getType().equals(ERewardType.EFFECT)) {
            EffectInstance effectInstance = RewardManager.deserializeReward(reward);
            AbstractGuiUtils.drawEffectIcon(fontRenderer, effectInstance, textureLocation, textureUV, x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, showText);
        }
        // 经验点
        else if (reward.getType().equals(ERewardType.EXP_POINT)) {
            AbstractGuiUtils.drawCustomIcon(fontRenderer, reward, textureLocation, textureUV.getPointUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 经验等级
        else if (reward.getType().equals(ERewardType.EXP_LEVEL)) {
            AbstractGuiUtils.drawCustomIcon(fontRenderer, reward, textureLocation, textureUV.getLevelUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 补签卡
        else if (reward.getType().equals(ERewardType.SIGN_IN_CARD)) {
            AbstractGuiUtils.drawCustomIcon(fontRenderer, reward, textureLocation, textureUV.getCardUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 消息
        else if (reward.getType().equals(ERewardType.MESSAGE)) {
            // 这玩意不是Integer类型也没有数量, 不能showText
            AbstractGuiUtils.drawCustomIcon(fontRenderer, reward, textureLocation, textureUV.getMessageUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), false);
        }
        // 进度
        else if (reward.getType().equals(ERewardType.ADVANCEMENT)) {
            ResourceLocation resourceLocation = RewardManager.deserializeReward(reward);
            AdvancementData advancementData = SakuraSignIn.getAdvancementData().stream()
                    .filter(data -> data.getId().toString().equalsIgnoreCase(resourceLocation.toString()))
                    .findFirst().orElse(new AdvancementData(resourceLocation, null));
            AbstractGuiUtils.renderItem(itemRenderer, fontRenderer, advancementData.getDisplayInfo().getIcon(), x, y, false);
        }
        // 指令
        else if (reward.getType().equals(ERewardType.COMMAND)) {
            renderItem(itemRenderer, fontRenderer, new ItemStack(Items.REPEATING_COMMAND_BLOCK), x, y, false);
        }
        if (showText && reward.getProbability() != 1) {
            AbstractGuiUtils.setDepth(EDepth.FOREGROUND);
            AbstractGuiUtils.drawString(fontRenderer, "?", x - 1, y - 1, AbstractGuiUtils.getProbabilityColor(reward.getProbability()));
            AbstractGuiUtils.resetDepth();
        }
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
    }


    public static int getProbabilityColor(double probability) {
        int color = 0xFF000000;
        // 默认不渲染
        if (probability == 1) {
            color = 0x00FFFFFF;
        }
        // 深灰色，最低级
        else if (probability >= 0.9) {
            color = 0xEFA9A9A9;
        }
        // 灰色，低级
        else if (probability >= 0.8) {
            color = 0xEFC0C0C0;
        }
        // 白色，普通
        else if (probability >= 0.7) {
            color = 0xEFFFFFFF;
        }
        // 亮绿色，良好
        else if (probability >= 0.6) {
            color = 0xEF32CD32;
        }
        // 深绿色，优秀
        else if (probability >= 0.5) {
            color = 0xEF228B22;
        }
        // 蓝色，稀有
        else if (probability >= 0.4) {
            color = 0xEF1E90FF;
        }
        // 深蓝色，稀有
        else if (probability >= 0.3) {
            color = 0xEF4682B4;
        }
        // 紫色，史诗
        else if (probability >= 0.2) {
            color = 0xEFA020F0;
        }
        // 金色，传说
        else if (probability >= 0.1) {
            color = 0xEFFFD700;
        }
        // 橙红色，终极
        else if (probability > 0) {
            color = 0xEFFF4500;
        }
        return color;
    }

    //  endregion 绘制图标

    //  region 绘制形状

    /**
     * 绘制一个“像素”矩形
     *
     * @param x     像素的 X 坐标
     * @param y     像素的 Y 坐标
     * @param color 像素的颜色
     */
    public static void drawPixel(int x, int y, int color) {
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        AbstractGui.fill(x, y, x + 1, y + 1, color);
    }

    /**
     * 绘制一个正方形
     */
    public static void fill(int x, int y, int width, int color) {
        AbstractGuiUtils.fill(x, y, width, width, color);
    }

    /**
     * 绘制一个矩形
     */
    public static void fill(int x, int y, int width, int height, int color) {
        AbstractGuiUtils.fill(x, y, width, height, color, 0);
    }

    /**
     * 绘制一个圆角矩形
     *
     * @param x      矩形的左上角X坐标
     * @param y      矩形的左上角Y坐标
     * @param width  矩形的宽度
     * @param height 矩形的高度
     * @param color  矩形的颜色
     * @param radius 圆角半径(0-10)
     */
    public static void fill(int x, int y, int width, int height, int color, int radius) {
        // 如果半径为0，则直接绘制普通矩形
        if (radius <= 0) {
            // 重置为白色, 避免颜色叠加问题
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            AbstractGui.fill(x, y, x + width, y + height, color);
            return;
        }

        // 限制半径最大值为10
        radius = Math.min(radius, 10);

        // 1. 绘制中间的矩形部分（去掉圆角占用的区域）
        AbstractGuiUtils.fill(x + radius + 1, y + radius + 1, width - 2 * (radius + 1), height - 2 * (radius + 1), color);

        // 2. 绘制四条边（去掉圆角占用的部分）
        // 上边
        AbstractGuiUtils.fill(x + radius + 1, y, width - 2 * radius - 2, radius, color);
        AbstractGuiUtils.fill(x + radius + 1, y + radius, width - 2 * (radius + 1), 1, color);
        // 下边
        AbstractGuiUtils.fill(x + radius + 1, y + height - radius, width - 2 * radius - 2, radius, color);
        AbstractGuiUtils.fill(x + radius + 1, y + height - radius - 1, width - 2 * (radius + 1), 1, color);
        // 左边
        AbstractGuiUtils.fill(x, y + radius + 1, radius, height - 2 * radius - 2, color);
        AbstractGuiUtils.fill(x + radius, y + radius + 1, 1, height - 2 * (radius + 1), color);
        // 右边
        AbstractGuiUtils.fill(x + width - radius, y + radius + 1, radius, height - 2 * radius - 2, color);
        AbstractGuiUtils.fill(x + width - radius - 1, y + radius + 1, 1, height - 2 * (radius + 1), color);

        // 3. 绘制四个圆角
        // 左上角
        AbstractGuiUtils.drawCircleQuadrant(x + radius, y + radius, radius, color, 1);
        // 右上角
        AbstractGuiUtils.drawCircleQuadrant(x + width - radius - 1, y + radius, radius, color, 2);
        // 左下角
        AbstractGuiUtils.drawCircleQuadrant(x + radius, y + height - radius - 1, radius, color, 3);
        // 右下角
        AbstractGuiUtils.drawCircleQuadrant(x + width - radius - 1, y + height - radius - 1, radius, color, 4);
    }

    /**
     * 绘制一个圆的四分之一部分（圆角辅助函数）
     *
     * @param centerX  圆角中心点X坐标
     * @param centerY  圆角中心点Y坐标
     * @param radius   圆角半径
     * @param color    圆角颜色
     * @param quadrant 指定绘制的象限（1=左上，2=右上，3=左下，4=右下）
     */
    private static void drawCircleQuadrant(int centerX, int centerY, int radius, int color, int quadrant) {
        for (int dx = 0; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    switch (quadrant) {
                        case 1: // 左上角
                            AbstractGuiUtils.drawPixel(centerX - dx, centerY - dy, color);
                            break;
                        case 2: // 右上角
                            AbstractGuiUtils.drawPixel(centerX + dx, centerY - dy, color);
                            break;
                        case 3: // 左下角
                            AbstractGuiUtils.drawPixel(centerX - dx, centerY + dy, color);
                            break;
                        case 4: // 右下角
                            AbstractGuiUtils.drawPixel(centerX + dx, centerY + dy, color);
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
     * @param color     边框颜色
     */
    public static void fillOutLine(int x, int y, int width, int height, int thickness, int color) {
        // 上边
        AbstractGuiUtils.fill(x, y, width, thickness, color);
        // 下边
        AbstractGuiUtils.fill(x, y + height - thickness, width, thickness, color);
        // 左边
        AbstractGuiUtils.fill(x, y, thickness, height, color);
        // 右边
        AbstractGuiUtils.fill(x + width - thickness, y, thickness, height, color);
    }

    /**
     * 绘制一个圆角矩形边框
     *
     * @param x         矩形左上角X坐标
     * @param y         矩形左上角Y坐标
     * @param width     矩形宽度
     * @param height    矩形高度
     * @param thickness 边框厚度
     * @param color     边框颜色
     * @param radius    圆角半径（0-10）
     */
    public static void fillOutLine(int x, int y, int width, int height, int thickness, int color, int radius) {
        if (radius <= 0) {
            // 如果没有圆角，直接绘制普通边框
            AbstractGuiUtils.fillOutLine(x, y, width, height, thickness, color);
        } else {
            // 限制圆角半径的最大值为10
            radius = Math.min(radius, 10);

            // 1. 绘制四条边（去掉圆角区域）
            // 上边
            AbstractGuiUtils.fill(x + radius, y, width - 2 * radius, thickness, color);
            // 下边
            AbstractGuiUtils.fill(x + radius, y + height - thickness, width - 2 * radius, thickness, color);
            // 左边
            AbstractGuiUtils.fill(x, y + radius, thickness, height - 2 * radius, color);
            // 右边
            AbstractGuiUtils.fill(x + width - thickness, y + radius, thickness, height - 2 * radius, color);

            // 2. 绘制四个圆角
            // 左上角
            drawCircleBorder(x + radius, y + radius, radius, thickness, color, 1);
            // 右上角
            drawCircleBorder(x + width - radius - 1, y + radius, radius, thickness, color, 2);
            // 左下角
            drawCircleBorder(x + radius, y + height - radius - 1, radius, thickness, color, 3);
            // 右下角
            drawCircleBorder(x + width - radius - 1, y + height - radius - 1, radius, thickness, color, 4);
        }
    }

    /**
     * 绘制一个圆角的边框区域（辅助函数）
     *
     * @param centerX   圆角中心点X坐标
     * @param centerY   圆角中心点Y坐标
     * @param radius    圆角半径
     * @param thickness 边框厚度
     * @param color     边框颜色
     * @param quadrant  指定绘制的象限（1=左上，2=右上，3=左下，4=右下）
     */
    private static void drawCircleBorder(int centerX, int centerY, int radius, int thickness, int color, int quadrant) {
        for (int dx = 0; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                if (Math.sqrt(dx * dx + dy * dy) <= radius && Math.sqrt(dx * dx + dy * dy) >= radius - thickness) {
                    switch (quadrant) {
                        case 1: // 左上角
                            AbstractGuiUtils.drawPixel(centerX - dx, centerY - dy, color);
                            break;
                        case 2: // 右上角
                            AbstractGuiUtils.drawPixel(centerX + dx, centerY - dy, color);
                            break;
                        case 3: // 左下角
                            AbstractGuiUtils.drawPixel(centerX - dx, centerY + dy, color);
                            break;
                        case 4: // 右下角
                            AbstractGuiUtils.drawPixel(centerX + dx, centerY + dy, color);
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
     * @param font         字体渲染器
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public static void drawPopupMessage(FontRenderer font, String message, int x, int y, int screenWidth, int screenHeight) {
        AbstractGuiUtils.drawPopupMessage(font, message, x, y, screenWidth, screenHeight, 0xFFFFFFFF, 0xAA000000);
    }

    /**
     * 绘制弹出层消息
     *
     * @param font         字体渲染器
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param bgColor      背景颜色
     * @param textColor    文本颜色
     */
    public static void drawPopupMessage(FontRenderer font, String message, int x, int y, int screenWidth, int screenHeight, int textColor, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(font, message, x, y, screenWidth, screenHeight, 2, textColor, bgColor);
    }

    /**
     * 绘制弹出层消息
     *
     * @param font         字体渲染器
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param margin       弹出层的外边距(外层背景与屏幕边缘)
     * @param bgColor      背景颜色
     * @param textColor    文本颜色
     */
    public static void drawPopupMessage(FontRenderer font, String message, int x, int y, int screenWidth, int screenHeight, int margin, int textColor, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(font, message, x, y, screenWidth, screenHeight, margin, margin, textColor, bgColor);
    }

    /**
     * 绘制弹出层消息
     *
     * @param font         字体渲染器
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标Y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param margin       弹出层的外边距(外层背景与屏幕边缘)
     * @param padding      弹出层的内边距(外层背景与内部文字)
     * @param bgColor      背景颜色
     * @param textColor    文本颜色
     */
    public static void drawPopupMessage(FontRenderer font, String message, int x, int y, int screenWidth, int screenHeight, int margin, int padding, int textColor, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(Text.literal(message).setFont(font).setColor(textColor), x, y, screenWidth, screenHeight, margin, padding, bgColor);
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
    public static void drawPopupMessage(Text text, int x, int y, int screenWidth, int screenHeight) {
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
     * @param bgColor      背景颜色
     */
    public static void drawPopupMessage(Text text, int x, int y, int screenWidth, int screenHeight, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, 2, bgColor);
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
     * @param bgColor      背景颜色
     */
    public static void drawPopupMessage(Text text, int x, int y, int screenWidth, int screenHeight, int margin, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, margin, margin, bgColor);
    }

    public static void drawPopupMessage(Text text, int x, int y, int screenWidth, int screenHeight, int margin, int padding, int bgColor) {
        // 计算消息宽度和高度, 并添加一些边距
        int msgWidth = AbstractGuiUtils.multilineTextWidth(text) + padding;
        int msgHeight = AbstractGuiUtils.multilineTextHeight(text) + padding;

        if (msgWidth >= screenWidth) msgWidth = screenWidth - padding * 2;
        if (msgHeight >= screenHeight) msgHeight = screenHeight - padding * 2;

        // 初始化调整后的坐标
        int adjustedX = x - msgWidth / 2; // 横向居中
        int adjustedY = y - msgHeight - 5; // 放置在鼠标上方（默认偏移 5 像素）

        // 检查顶部空间是否充足
        boolean hasTopSpace = adjustedY >= margin;
        // 检查左右空间是否充足
        boolean hasLeftSpace = adjustedX >= margin;
        boolean hasRightSpace = adjustedX + msgWidth <= screenWidth - margin;

        if (!hasTopSpace) {
            // 如果顶部空间不足，调整到鼠标下方
            adjustedY = y + 1 + 5;
        } else {
            // 如果顶部空间充足
            if (!hasLeftSpace) {
                // 如果左侧空间不足，靠右
                adjustedX = margin;
            } else if (!hasRightSpace) {
                // 如果右侧空间不足，靠左
                adjustedX = screenWidth - msgWidth - margin;
            }
        }

        // 如果调整后仍然超出屏幕范围，强制限制在屏幕内
        adjustedX = Math.max(margin, Math.min(adjustedX, screenWidth - msgWidth - margin));
        adjustedY = Math.max(margin, Math.min(adjustedY, screenHeight - msgHeight - margin));
        AbstractGuiUtils.setDepth(EDepth.POPUP_TIPS);
        // 重置为白色, 避免颜色叠加问题
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        // 在计算完的坐标位置绘制消息框背景
        AbstractGui.fill(adjustedX, adjustedY, adjustedX + msgWidth, adjustedY + msgHeight, bgColor);
        AbstractGuiUtils.resetDepth();
        AbstractGuiUtils.setDepth(EDepth.POPUP_TIPS);
        // 绘制消息文字
        AbstractGuiUtils.drawLimitedText(text, adjustedX + (float) padding / 2, adjustedY + (float) padding / 2, msgWidth, msgHeight / text.getFont().lineHeight, EllipsisPosition.MIDDLE);
        AbstractGuiUtils.resetDepth();
    }

    //  endregion 绘制弹出层提示

    // region 重写方法签名

    public static TextFieldWidget newTextFieldWidget(FontRenderer font, int x, int y, int width, int height, ITextComponent content) {
        return new TextFieldWidget(font, x, y, width, height, content.getString());
    }

    public static Button newButton(int x, int y, int width, int height, ITextComponent content, Button.IPressable onPress) {
        return new Button(x, y, width, height, content.getString(), onPress);
    }

    // endregion 重写方法签名
}
