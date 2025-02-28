package xin.vanilla.sakura.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.network.AdvancementData;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GuiComponent工具类
 */
@SuppressWarnings("unused")
@OnlyIn(Dist.CLIENT)
public class AbstractGuiUtils {

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
    public static void setDepth(PoseStack poseStack) {
        AbstractGuiUtils.setDepth(poseStack, EDepth.FOREGROUND);
    }

    /**
     * 设置深度
     *
     * @param poseStack 用于变换绘制坐标系的矩阵堆栈
     * @param depth     深度
     */
    public static void setDepth(PoseStack poseStack, EDepth depth) {
        RenderSystem.disableDepthTest();
        poseStack.pushPose();
        poseStack.translate(0, 0, depth.getDepth());
    }

    /**
     * 重置深度
     */
    public static void resetDepth(PoseStack poseStack) {
        poseStack.popPose();
        RenderSystem.enableDepthTest();
    }

    // endregion 设置深度

    // region 绘制纹理

    public static void bindTexture(ResourceLocation resourceLocation) {
        // 设置纹理的颜色(RGBA)为白色，表示没有颜色变化，纹理将按原样显示。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, resourceLocation);
    }

    public static void blit(PoseStack poseStack, int x0, int y0, int z, int destWidth, int destHeight, TextureAtlasSprite sprite) {
        GuiComponent.blit(poseStack, x0, y0, z, destWidth, destHeight, sprite);
    }

    public static void blit(PoseStack poseStack, int x0, int y0, int z, float u0, float v0, int width, int height, int textureHeight, int textureWidth) {
        GuiComponent.blit(poseStack, x0, y0, z, u0, v0, width, height, textureHeight, textureWidth);
    }

    /**
     * 使用指定的纹理坐标和尺寸信息绘制一个矩形区域。
     *
     * @param poseStack     用于变换绘制坐标系的矩阵堆栈。
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
    public static void blit(PoseStack poseStack, int x0, int y0, int destWidth, int destHeight, float u0, float v0, int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        GuiComponent.blit(poseStack, x0, y0, destWidth, destHeight, u0, v0, srcWidth, srcHeight, textureWidth, textureHeight);
    }

    public static void blit(PoseStack poseStack, int x0, int y0, float u0, float v0, int destWidth, int destHeight, int textureWidth, int textureHeight) {
        GuiComponent.blit(poseStack, x0, y0, u0, v0, destWidth, destHeight, textureWidth, textureHeight);
    }

    /**
     * 绘制旋转的纹理
     *
     * @param poseStack         矩阵栈
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
    public static void renderRotatedTexture(PoseStack poseStack, ResourceLocation texture, TextureCoordinate textureCoordinate, Coordinate coordinate, double baseX, double baseY, double scale, double angle, boolean flipHorizontal, boolean flipVertical) {
        double x = baseX + coordinate.getX() * scale;
        double y = baseY + coordinate.getY() * scale;
        int width = (int) (coordinate.getWidth() * scale);
        int height = (int) (coordinate.getHeight() * scale);
        float u0 = (float) coordinate.getU0();
        float v0 = (float) coordinate.getV0();
        int uWidth = (int) coordinate.getUWidth();
        int vHeight = (int) coordinate.getVHeight();
        // 绑定纹理
        AbstractGuiUtils.bindTexture(texture);
        // 保存当前矩阵状态
        poseStack.pushPose();
        // 平移到旋转中心 (x + width / 2, y + height / 2)
        poseStack.translate(x + width / 2.0, y + height / 2.0, 0);
        // 创建一个 Quaternion 用来表示绕 Z 轴旋转, 将角度转换为弧度
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(angle)));
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
        poseStack.translate(-width / 2.0, -height / 2.0, 0);
        // 启用混合模式来正确处理透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 绘制纹理
        AbstractGuiUtils.blit(poseStack, 0, 0, width, height, u0, v0, uWidth, vHeight, textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        RenderSystem.disableBlend();
        // 恢复矩阵状态
        poseStack.popPose();
    }

    /**
     * 绘制 颤抖的 纹理
     *
     * @param poseStack          矩阵栈
     * @param texture            纹理
     * @param textureCoordinate  纹理坐标
     * @param coordinate         绘制相对坐标
     * @param baseX              绘制的基础坐标X
     * @param baseY              绘制的基础坐标Y
     * @param scale              Screen纹理缩放比例
     * @param affectLight        是否受光照影响
     * @param tremblingAmplitude 颤抖幅度
     */
    public static void renderTremblingTexture(PoseStack poseStack, ResourceLocation texture, TextureCoordinate textureCoordinate, Coordinate coordinate, double baseX, double baseY, double scale, boolean affectLight, double tremblingAmplitude) {
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
        AbstractGuiUtils.bindTexture(texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.pushPose();
        // 添加偏移
        if (tremblingAmplitude > 0) {
            if (!affectLight || SakuraUtils.getEnvironmentBrightness(Minecraft.getInstance().player) > 4) {
                x += (random.nextFloat() - 0.5) * tremblingAmplitude;
                y += (random.nextFloat() - 0.5) * tremblingAmplitude;
            }
        }
        poseStack.translate(x, y, 0);
        // 绘制纹理
        AbstractGuiUtils.blit(poseStack, 0, 0, width, height, u0, v0, uWidth, vHeight, textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    // endregion 绘制纹理

    // region 绘制文字

    public static void drawString(PoseStack poseStack, Font font, String text, float x, float y) {
        AbstractGuiUtils.drawString(Text.literal(text).setPoseStack(poseStack).setFont(font), x, y);
    }

    public static void drawString(PoseStack poseStack, Font font, String text, float x, float y, int color) {
        AbstractGuiUtils.drawString(Text.literal(text).setColor(color).setPoseStack(poseStack).setFont(font), x, y);
    }

    public static void drawString(PoseStack poseStack, Font font, String text, float x, float y, boolean shadow) {
        AbstractGuiUtils.drawString(Text.literal(text).setShadow(shadow).setPoseStack(poseStack).setFont(font), x, y);
    }

    public static void drawString(PoseStack poseStack, Font font, String text, float x, float y, int color, boolean shadow) {
        AbstractGuiUtils.drawString(Text.literal(text).setColor(color).setShadow(shadow).setPoseStack(poseStack).setFont(font), x, y);
    }

    public static void drawString(Text text, float x, float y, EDepth depth) {
        if (depth != null) AbstractGuiUtils.setDepth(text.getPoseStack(), depth);
        AbstractGuiUtils.drawString(text, x, y);
        if (depth != null) AbstractGuiUtils.resetDepth(text.getPoseStack());
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
    public static int multilineTextHeight(Font font, String text) {
        return StringUtils.replaceLine(text).split("\n").length * font.lineHeight;
    }

    public static int getStringWidth(Font font, Collection<String> texts) {
        int width = 0;
        for (String s : texts) {
            width = Math.max(width, font.width(s));
        }
        return width;
    }

    public static int getStringHeight(Font font, Collection<String> texts) {
        return AbstractGuiUtils.multilineTextHeight(font, String.join("\n", texts));
    }

    public static int getTextWidth(Font font, Collection<Text> texts) {
        int width = 0;
        for (Text text : texts) {
            for (String string : StringUtils.replaceLine(text.getContent()).split("\n")) {
                width = Math.max(width, font.width(string));
            }
        }
        return width;
    }

    public static int getTextHeight(Font font, Collection<Text> texts) {
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
    public static int multilineTextWidth(Font font, String text) {
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
     * @param poseStack 渲染矩阵
     * @param font      字体渲染器
     * @param text      要绘制的文本
     * @param x         绘制的X坐标
     * @param y         绘制的Y坐标
     * @param colors    文本颜色
     */
    public static void drawMultilineText(PoseStack poseStack, Font font, String text, float x, float y, int... colors) {
        AbstractGuiUtils.drawMultilineText(Text.literal(text).setPoseStack(poseStack).setFont(font), x, y, colors);
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
     * @param poseStack 渲染矩阵
     * @param font      字体渲染器
     * @param text      要绘制的文本
     * @param x         绘制的X坐标
     * @param y         绘制的Y坐标
     * @param maxWidth  文本显示的最大宽度
     * @param color     文本颜色
     */
    public static void drawLimitedText(PoseStack poseStack, Font font, String text, float x, float y, int maxWidth, int color) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setPoseStack(poseStack).setFont(font).setColor(color).setShadow(true), x, y, maxWidth, 0, EllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分末尾以省略号表示
     *
     * @param poseStack 渲染矩阵
     * @param font      字体渲染器
     * @param text      要绘制的文本
     * @param x         绘制的X坐标
     * @param y         绘制的Y坐标
     * @param maxWidth  文本显示的最大宽度
     * @param color     文本颜色
     * @param shadow    是否显示阴影
     */
    public static void drawLimitedText(PoseStack poseStack, Font font, String text, float x, float y, int maxWidth, int color, boolean shadow) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setPoseStack(poseStack).setFont(font).setColor(color).setShadow(shadow), x, y, maxWidth, 0, EllipsisPosition.END);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param poseStack 渲染矩阵
     * @param font      字体渲染器
     * @param text      要绘制的文本
     * @param x         绘制的X坐标
     * @param y         绘制的Y坐标
     * @param maxWidth  文本显示的最大宽度
     * @param position  省略号位置（开头、中间、结尾）
     * @param color     文本颜色
     */
    public static void drawLimitedText(PoseStack poseStack, Font font, String text, float x, float y, int maxWidth, EllipsisPosition position, int color) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setPoseStack(poseStack).setFont(font).setColor(color).setShadow(true), x, y, maxWidth, 0, position);
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param poseStack 渲染矩阵
     * @param font      字体渲染器
     * @param text      要绘制的文本
     * @param x         绘制的X坐标
     * @param y         绘制的Y坐标
     * @param maxWidth  文本显示的最大宽度
     * @param position  省略号位置（开头、中间、结尾）
     * @param color     文本颜色
     * @param shadow    是否显示阴影
     */
    public static void drawLimitedText(PoseStack poseStack, Font font, String text, float x, float y, int maxWidth, EllipsisPosition position, int color, boolean shadow) {
        AbstractGuiUtils.drawLimitedText(Text.literal(text).setPoseStack(poseStack).setFont(font).setColor(color).setShadow(shadow), x, y, maxWidth, 0, position);
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
            Font font = text.getFont();
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
                float xOffset = switch (text.getAlign()) {
                    case CENTER -> (maxLineWidth - font.width(line)) / 2.0f;
                    case RIGHT -> maxLineWidth - font.width(line);
                    default -> 0;
                };

                // 绘制每行文本
                PoseStack poseStack = text.getPoseStack();
                if (text.isShadow()) {
                    font.drawShadow(poseStack, text.copyWithoutChildren().setText(line).toComponent().toTextComponent(SakuraUtils.getClientLanguage()), (float) x + xOffset, (float) y + index * font.lineHeight, text.getColor());
                } else {
                    font.draw(poseStack, text.copyWithoutChildren().setText(line).toComponent().toTextComponent(SakuraUtils.getClientLanguage()), (float) x + xOffset, (float) y + index * font.lineHeight, text.getColor());
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
     * @param poseStack         用于变换绘制坐标系的矩阵堆栈
     * @param font              字体渲染器
     * @param mobEffectInstance 待绘制的效果实例
     * @param x                 矩形的左上角x坐标
     * @param y                 矩形的左上角y坐标
     * @param width             目标矩形的宽度，决定了图像在屏幕上的宽度
     * @param height            目标矩形的高度，决定了图像在屏幕上的高度
     * @param showText          是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(PoseStack poseStack, Font font, MobEffectInstance mobEffectInstance, ResourceLocation textureLocation, TextureCoordinate textureCoordinate, int x, int y, int width, int height, boolean showText) {
        ResourceLocation effectIcon = TextureUtils.getEffectTexture(mobEffectInstance);
        if (effectIcon == null) {
            AbstractGuiUtils.bindTexture(textureLocation);
            Coordinate buffUV = textureCoordinate.getBuffUV();
            AbstractGuiUtils.blit(poseStack, x, y, width, height, (float) buffUV.getU0(), (float) buffUV.getV0(), (int) buffUV.getUWidth(), (int) buffUV.getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        } else {
            AbstractGuiUtils.bindTexture(effectIcon);
            AbstractGuiUtils.blit(poseStack, x, y, 0, 0, width, height, width, height);
        }
        if (showText) {
            // 效果等级
            if (mobEffectInstance.getAmplifier() >= 0) {
                Component amplifierString = Component.literal(StringUtils.intToRoman(mobEffectInstance.getAmplifier() + 1));
                int amplifierWidth = font.width(amplifierString.toString());
                float fontX = x + width - (float) amplifierWidth / 2;
                float fontY = y - 1;
                int color = 0xFFFFFFFF;
                font.drawShadow(poseStack, amplifierString.setColor(color).toTextComponent(), fontX, fontY, color);
            }
            // 效果持续时间
            if (mobEffectInstance.getDuration() > 0) {
                Component durationString = Component.literal(DateUtils.toMaxUnitString(mobEffectInstance.getDuration(), DateUtils.DateUnit.SECOND, 0, 1));
                int durationWidth = font.width(durationString.toString());
                float fontX = x + width - (float) durationWidth / 2 - 2;
                float fontY = y + (float) height / 2 + 1;
                int color = 0xFFFFFFFF;
                font.drawShadow(poseStack, durationString.setColor(color).toTextComponent(), fontX, fontY, color);
            }
        }
    }

    /**
     * 绘制效果图标
     *
     * @param poseStack      用于变换绘制坐标系的矩阵堆栈
     * @param font           字体渲染器
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param width          目标矩形的宽度，决定了图像在屏幕上的宽度
     * @param height         目标矩形的高度，决定了图像在屏幕上的高度
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(PoseStack poseStack, Font font, MobEffectInstance effectInstance, int x, int y, int width, int height, boolean showText) {
        AbstractGuiUtils.drawEffectIcon(poseStack, font, effectInstance, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), x, y, width, height, showText);
    }

    /**
     * 绘制效果图标
     *
     * @param poseStack      用于变换绘制坐标系的矩阵堆栈
     * @param font           字体渲染器
     * @param effectInstance 待绘制的效果实例
     * @param x              矩形的左上角x坐标
     * @param y              矩形的左上角y坐标
     * @param showText       是否显示效果等级和持续时间
     */
    public static void drawEffectIcon(PoseStack poseStack, Font font, MobEffectInstance effectInstance, int x, int y, boolean showText) {
        AbstractGuiUtils.drawEffectIcon(poseStack, font, effectInstance, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, showText);
    }

    /**
     * 绘制自定义图标
     *
     * @param poseStack       用于变换绘制坐标系的矩阵堆栈
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
    public static void drawCustomIcon(PoseStack poseStack, Font font, Reward reward, ResourceLocation textureLocation, Coordinate textureUV, int x, int y, int totalWidth, int totalHeight, boolean showText) {
        AbstractGuiUtils.bindTexture(textureLocation);
        AbstractGuiUtils.blit(poseStack, x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, (float) textureUV.getU0(), (float) textureUV.getV0(), (int) textureUV.getUWidth(), (int) textureUV.getVHeight(), totalWidth, totalHeight);
        if (showText) {
            Component num = Component.literal(String.valueOf((Integer) RewardManager.deserializeReward(reward)));
            int numWidth = font.width(num.toString());
            float fontX = x + ITEM_ICON_SIZE - (float) numWidth / 2 - 2;
            float fontY = y + (float) ITEM_ICON_SIZE - font.lineHeight + 2;
            int color = 0xFFFFFFFF;
            font.drawShadow(poseStack, num.setColor(color).toTextComponent(), fontX, fontY, color);
        }
    }

    public static void renderItem(PoseStack poseStack, ItemRenderer itemRenderer, Font fontRenderer, ItemStack itemStack, int x, int y, boolean showText) {
        itemRenderer.renderGuiItem(poseStack, itemStack, x, y);
        if (showText) {
            itemRenderer.renderGuiItemDecorations(poseStack, fontRenderer, itemStack, x, y, String.valueOf(itemStack.getCount()));
        }
    }

    /**
     * 渲染奖励图标
     *
     * @param poseStack    用于变换绘制坐标系的矩阵堆栈
     * @param itemRenderer 物品渲染器
     * @param fontRenderer 字体渲染器
     * @param reward       待绘制的奖励
     * @param x            图标的x坐标
     * @param y            图标的y坐标
     * @param showText     是否显示物品数量等信息
     */
    public static void renderCustomReward(PoseStack poseStack, ItemRenderer itemRenderer, Font fontRenderer, ResourceLocation textureLocation, TextureCoordinate textureUV, Reward reward, int x, int y, boolean showText) {
        AbstractGuiUtils.renderCustomReward(poseStack, itemRenderer, fontRenderer, textureLocation, textureUV, reward, x, y, showText, true);
    }

    /**
     * 渲染奖励图标
     *
     * @param poseStack    用于变换绘制坐标系的矩阵堆栈
     * @param itemRenderer 物品渲染器
     * @param fontRenderer 字体渲染器
     * @param reward       待绘制的奖励
     * @param x            图标的x坐标
     * @param y            图标的y坐标
     * @param showText     是否显示物品数量等信息
     * @param showQuality  是否显示奖励概率品质颜色
     */
    public static void renderCustomReward(PoseStack poseStack, ItemRenderer itemRenderer, Font fontRenderer, ResourceLocation textureLocation, TextureCoordinate textureUV, Reward reward, int x, int y, boolean showText, boolean showQuality) {
        // 物品
        if (reward.getType().equals(ERewardType.ITEM)) {
            ItemStack itemStack = RewardManager.deserializeReward(reward);
            renderItem(poseStack, itemRenderer, fontRenderer, itemStack, x, y, showText);
        }
        // 效果
        else if (reward.getType().equals(ERewardType.EFFECT)) {
            MobEffectInstance mobEffectInstance = RewardManager.deserializeReward(reward);
            AbstractGuiUtils.drawEffectIcon(poseStack, fontRenderer, mobEffectInstance, textureLocation, textureUV, x, y, ITEM_ICON_SIZE, ITEM_ICON_SIZE, showText);
        }
        // 经验点
        else if (reward.getType().equals(ERewardType.EXP_POINT)) {
            AbstractGuiUtils.drawCustomIcon(poseStack, fontRenderer, reward, textureLocation, textureUV.getPointUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 经验等级
        else if (reward.getType().equals(ERewardType.EXP_LEVEL)) {
            AbstractGuiUtils.drawCustomIcon(poseStack, fontRenderer, reward, textureLocation, textureUV.getLevelUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 补签卡
        else if (reward.getType().equals(ERewardType.SIGN_IN_CARD)) {
            AbstractGuiUtils.drawCustomIcon(poseStack, fontRenderer, reward, textureLocation, textureUV.getCardUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), showText);
        }
        // 消息
        else if (reward.getType().equals(ERewardType.MESSAGE)) {
            // 这玩意不是Integer类型也没有数量, 不能showText
            AbstractGuiUtils.drawCustomIcon(poseStack, fontRenderer, reward, textureLocation, textureUV.getMessageUV(), x, y, textureUV.getTotalWidth(), textureUV.getTotalHeight(), false);
        }
        // 进度
        else if (reward.getType().equals(ERewardType.ADVANCEMENT)) {
            ResourceLocation resourceLocation = RewardManager.deserializeReward(reward);
            AdvancementData advancementData = SakuraSignIn.getAdvancementData().stream()
                    .filter(data -> data.id().toString().equalsIgnoreCase(resourceLocation.toString()))
                    .findFirst().orElse(new AdvancementData(resourceLocation, null));
            itemRenderer.renderGuiItem(poseStack, advancementData.displayInfo().getIcon(), x, y);
        }
        // 指令
        else if (reward.getType().equals(ERewardType.COMMAND)) {
            renderItem(poseStack, itemRenderer, fontRenderer, new ItemStack(Items.REPEATING_COMMAND_BLOCK), x, y, false);
        }
        if (showText && showQuality && reward.getProbability().compareTo(BigDecimal.ONE) != 0) {
            AbstractGuiUtils.setDepth(poseStack, EDepth.FOREGROUND);
            AbstractGuiUtils.drawString(poseStack, fontRenderer, "?", x - 1, y - 1, AbstractGuiUtils.getProbabilityColor(reward.getProbability().doubleValue()));
            AbstractGuiUtils.resetDepth(poseStack);
        }
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
     * @param poseStack 用于变换绘制坐标系的矩阵堆栈
     * @param x         像素的 X 坐标
     * @param y         像素的 Y 坐标
     * @param color     像素的颜色
     */
    public static void drawPixel(PoseStack poseStack, int x, int y, int color) {
        GuiComponent.fill(poseStack, x, y, x + 1, y + 1, color);
    }

    /**
     * 绘制一个正方形
     */
    public static void fill(PoseStack poseStack, int x, int y, int width, int color) {
        AbstractGuiUtils.fill(poseStack, x, y, width, width, color);
    }

    /**
     * 绘制一个矩形
     */
    public static void fill(PoseStack poseStack, int x, int y, int width, int height, int color) {
        AbstractGuiUtils.fill(poseStack, x, y, width, height, color, 0);
    }

    /**
     * 绘制一个圆角矩形
     *
     * @param poseStack 矩阵栈
     * @param x         矩形的左上角X坐标
     * @param y         矩形的左上角Y坐标
     * @param width     矩形的宽度
     * @param height    矩形的高度
     * @param color     矩形的颜色
     * @param radius    圆角半径(0-10)
     */
    public static void fill(PoseStack poseStack, int x, int y, int width, int height, int color, int radius) {
        // 如果半径为0，则直接绘制普通矩形
        if (radius <= 0) {
            GuiComponent.fill(poseStack, x, y, x + width, y + height, color);
            return;
        }

        // 限制半径最大值为10
        radius = Math.min(radius, 10);

        // 1. 绘制中间的矩形部分（去掉圆角占用的区域）
        AbstractGuiUtils.fill(poseStack, x + radius + 1, y + radius + 1, width - 2 * (radius + 1), height - 2 * (radius + 1), color);

        // 2. 绘制四条边（去掉圆角占用的部分）
        // 上边
        AbstractGuiUtils.fill(poseStack, x + radius + 1, y, width - 2 * radius - 2, radius, color);
        AbstractGuiUtils.fill(poseStack, x + radius + 1, y + radius, width - 2 * (radius + 1), 1, color);
        // 下边
        AbstractGuiUtils.fill(poseStack, x + radius + 1, y + height - radius, width - 2 * radius - 2, radius, color);
        AbstractGuiUtils.fill(poseStack, x + radius + 1, y + height - radius - 1, width - 2 * (radius + 1), 1, color);
        // 左边
        AbstractGuiUtils.fill(poseStack, x, y + radius + 1, radius, height - 2 * radius - 2, color);
        AbstractGuiUtils.fill(poseStack, x + radius, y + radius + 1, 1, height - 2 * (radius + 1), color);
        // 右边
        AbstractGuiUtils.fill(poseStack, x + width - radius, y + radius + 1, radius, height - 2 * radius - 2, color);
        AbstractGuiUtils.fill(poseStack, x + width - radius - 1, y + radius + 1, 1, height - 2 * (radius + 1), color);

        // 3. 绘制四个圆角
        // 左上角
        AbstractGuiUtils.drawCircleQuadrant(poseStack, x + radius, y + radius, radius, color, 1);
        // 右上角
        AbstractGuiUtils.drawCircleQuadrant(poseStack, x + width - radius - 1, y + radius, radius, color, 2);
        // 左下角
        AbstractGuiUtils.drawCircleQuadrant(poseStack, x + radius, y + height - radius - 1, radius, color, 3);
        // 右下角
        AbstractGuiUtils.drawCircleQuadrant(poseStack, x + width - radius - 1, y + height - radius - 1, radius, color, 4);
    }

    /**
     * 绘制一个圆的四分之一部分（圆角辅助函数）
     *
     * @param poseStack 矩阵栈
     * @param centerX   圆角中心点X坐标
     * @param centerY   圆角中心点Y坐标
     * @param radius    圆角半径
     * @param color     圆角颜色
     * @param quadrant  指定绘制的象限（1=左上，2=右上，3=左下，4=右下）
     */
    private static void drawCircleQuadrant(PoseStack poseStack, int centerX, int centerY, int radius, int color, int quadrant) {
        for (int dx = 0; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    switch (quadrant) {
                        case 1 -> // 左上角
                                AbstractGuiUtils.drawPixel(poseStack, centerX - dx, centerY - dy, color);
                        case 2 -> // 右上角
                                AbstractGuiUtils.drawPixel(poseStack, centerX + dx, centerY - dy, color);
                        case 3 -> // 左下角
                                AbstractGuiUtils.drawPixel(poseStack, centerX - dx, centerY + dy, color);
                        case 4 -> // 右下角
                                AbstractGuiUtils.drawPixel(poseStack, centerX + dx, centerY + dy, color);
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
    public static void fillOutLine(PoseStack poseStack, int x, int y, int width, int height, int thickness, int color) {
        // 上边
        AbstractGuiUtils.fill(poseStack, x, y, width, thickness, color);
        // 下边
        AbstractGuiUtils.fill(poseStack, x, y + height - thickness, width, thickness, color);
        // 左边
        AbstractGuiUtils.fill(poseStack, x, y, thickness, height, color);
        // 右边
        AbstractGuiUtils.fill(poseStack, x + width - thickness, y, thickness, height, color);
    }

    /**
     * 绘制一个圆角矩形边框
     *
     * @param poseStack 矩阵栈
     * @param x         矩形左上角X坐标
     * @param y         矩形左上角Y坐标
     * @param width     矩形宽度
     * @param height    矩形高度
     * @param thickness 边框厚度
     * @param color     边框颜色
     * @param radius    圆角半径（0-10）
     */
    public static void fillOutLine(PoseStack poseStack, int x, int y, int width, int height, int thickness, int color, int radius) {
        if (radius <= 0) {
            // 如果没有圆角，直接绘制普通边框
            AbstractGuiUtils.fillOutLine(poseStack, x, y, width, height, thickness, color);
        } else {
            // 限制圆角半径的最大值为10
            radius = Math.min(radius, 10);

            // 1. 绘制四条边（去掉圆角区域）
            // 上边
            AbstractGuiUtils.fill(poseStack, x + radius, y, width - 2 * radius, thickness, color);
            // 下边
            AbstractGuiUtils.fill(poseStack, x + radius, y + height - thickness, width - 2 * radius, thickness, color);
            // 左边
            AbstractGuiUtils.fill(poseStack, x, y + radius, thickness, height - 2 * radius, color);
            // 右边
            AbstractGuiUtils.fill(poseStack, x + width - thickness, y + radius, thickness, height - 2 * radius, color);

            // 2. 绘制四个圆角
            // 左上角
            drawCircleBorder(poseStack, x + radius, y + radius, radius, thickness, color, 1);
            // 右上角
            drawCircleBorder(poseStack, x + width - radius - 1, y + radius, radius, thickness, color, 2);
            // 左下角
            drawCircleBorder(poseStack, x + radius, y + height - radius - 1, radius, thickness, color, 3);
            // 右下角
            drawCircleBorder(poseStack, x + width - radius - 1, y + height - radius - 1, radius, thickness, color, 4);
        }
    }

    /**
     * 绘制一个圆角的边框区域（辅助函数）
     *
     * @param poseStack 矩阵栈
     * @param centerX   圆角中心点X坐标
     * @param centerY   圆角中心点Y坐标
     * @param radius    圆角半径
     * @param thickness 边框厚度
     * @param color     边框颜色
     * @param quadrant  指定绘制的象限（1=左上，2=右上，3=左下，4=右下）
     */
    private static void drawCircleBorder(PoseStack poseStack, int centerX, int centerY, int radius, int thickness, int color, int quadrant) {
        for (int dx = 0; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                double sqrt = Math.sqrt(dx * dx + dy * dy);
                if (sqrt <= radius && sqrt >= radius - thickness) {
                    switch (quadrant) {
                        case 1: // 左上角
                            AbstractGuiUtils.drawPixel(poseStack, centerX - dx, centerY - dy, color);
                            break;
                        case 2: // 右上角
                            AbstractGuiUtils.drawPixel(poseStack, centerX + dx, centerY - dy, color);
                            break;
                        case 3: // 左下角
                            AbstractGuiUtils.drawPixel(poseStack, centerX - dx, centerY + dy, color);
                            break;
                        case 4: // 右下角
                            AbstractGuiUtils.drawPixel(poseStack, centerX + dx, centerY + dy, color);
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
     * @param poseStack    矩阵栈
     * @param font         字体渲染器
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public static void drawPopupMessage(PoseStack poseStack, Font font, String message, int x, int y, int screenWidth, int screenHeight) {
        AbstractGuiUtils.drawPopupMessage(poseStack, font, message, x, y, screenWidth, screenHeight, 0xFFFFFFFF, 0xAA000000);
    }

    /**
     * 绘制弹出层消息
     *
     * @param poseStack    矩阵栈
     * @param font         字体渲染器
     * @param message      消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param bgColor      背景颜色
     * @param textColor    文本颜色
     */
    public static void drawPopupMessage(PoseStack poseStack, Font font, String message, int x, int y, int screenWidth, int screenHeight, int textColor, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(poseStack, font, message, x, y, screenWidth, screenHeight, 2, textColor, bgColor);
    }

    /**
     * 绘制弹出层消息
     *
     * @param poseStack    矩阵栈
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
    public static void drawPopupMessage(PoseStack poseStack, Font font, String message, int x, int y, int screenWidth, int screenHeight, int margin, int textColor, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(poseStack, font, message, x, y, screenWidth, screenHeight, margin, margin, textColor, bgColor);
    }

    /**
     * 绘制弹出层消息
     *
     * @param poseStack    矩阵栈
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
    public static void drawPopupMessage(PoseStack poseStack, Font font, String message, int x, int y, int screenWidth, int screenHeight, int margin, int padding, int textColor, int bgColor) {
        AbstractGuiUtils.drawPopupMessage(Text.literal(message).setPoseStack(poseStack).setFont(font).setColor(textColor), x, y, screenWidth, screenHeight, margin, padding, bgColor);
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
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, margin, padding, bgColor, true);
    }

    public static void drawPopupMessage(Text text, int x, int y, int screenWidth, int screenHeight, int margin, int padding, int bgColor, boolean inScreen) {
        // 计算消息宽度和高度, 并添加一些边距
        int msgWidth = AbstractGuiUtils.multilineTextWidth(text) + padding;
        int msgHeight = AbstractGuiUtils.multilineTextHeight(text) + padding;

        // 计算调整后的坐标
        int adjustedX = x;
        int adjustedY = y;
        if (inScreen) {
            if (msgWidth >= screenWidth) msgWidth = screenWidth - padding * 2;
            if (msgHeight >= screenHeight) msgHeight = screenHeight - padding * 2;

            // 初始化调整后的坐标
            adjustedX = x - msgWidth / 2; // 横向居中
            adjustedY = y - msgHeight - 5; // 放置在鼠标上方（默认偏移 5 像素）

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
        }

        AbstractGuiUtils.setDepth(text.getPoseStack(), EDepth.POPUP_TIPS);
        // 在计算完的坐标位置绘制消息框背景
        GuiComponent.fill(text.getPoseStack(), adjustedX, adjustedY, adjustedX + msgWidth, adjustedY + msgHeight, bgColor);
        AbstractGuiUtils.resetDepth(text.getPoseStack());
        AbstractGuiUtils.setDepth(text.getPoseStack(), EDepth.POPUP_TIPS);
        // 绘制消息文字
        AbstractGuiUtils.drawLimitedText(text, adjustedX + (float) padding / 2, adjustedY + (float) padding / 2, msgWidth, msgHeight / text.getFont().lineHeight, EllipsisPosition.MIDDLE);
        AbstractGuiUtils.resetDepth(text.getPoseStack());
    }

    //  endregion 绘制弹出层提示

    // region 重写方法签名

    public static EditBox newTextFieldWidget(Font font, int x, int y, int width, int height, Component content) {
        return new EditBox(font, x, y, width, height, content.toTextComponent());
    }

    public static Button newButton(int x, int y, int width, int height, Component content, Button.OnPress onPress) {
        return Button.builder(content.toTextComponent(), onPress).pos(x, y).size(width, height).build();
    }

    // endregion 重写方法签名
}
