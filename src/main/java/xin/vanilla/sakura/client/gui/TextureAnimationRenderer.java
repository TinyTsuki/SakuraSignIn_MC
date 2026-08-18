package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 奖励编辑器工具栏的纹理变换绘制。
 */
final class TextureAnimationRenderer {
    private TextureAnimationRenderer() {
    }

    static void drawRotated(PoseStack stack, ResourceLocation texture,
                            TextureCoordinate atlas, Coordinate coordinate,
                            double angle, boolean flipHorizontal,
                            boolean flipVertical) {
        int width = (int) coordinate.getWidth();
        int height = (int) coordinate.getHeight();
        double u = coordinate.getU0();
        double v = coordinate.getV0();
        int uWidth = (int) coordinate.getUWidth();
        int vHeight = (int) coordinate.getVHeight();
        if (flipHorizontal) {
            u += uWidth;
            uWidth = -uWidth;
        }
        if (flipVertical) {
            v += vHeight;
            vHeight = -vHeight;
        }
        stack.pushPose();
        stack.translate(coordinate.getX() + width / 2.0,
                coordinate.getY() + height / 2.0, 0);
        stack.mulPose(Axis.ZP.rotationDegrees((float) angle));
        stack.translate(-width / 2.0, -height / 2.0, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        AbstractGuiUtils.blit(stack, texture, 0, 0, width, height,
                u, v, uWidth, vHeight, atlas.getTotalWidth(), atlas.getTotalHeight());
        RenderSystem.disableBlend();
        stack.popPose();
    }

    static void drawTrembling(PoseStack stack, ResourceLocation texture,
                              TextureCoordinate atlas, Coordinate coordinate,
                              double amplitude) {
        double x = coordinate.getX();
        double y = coordinate.getY();
        if (amplitude > 0 && SakuraUtils.getEnvironmentBrightness(
                Minecraft.getInstance().player) > 4) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            x += (random.nextDouble() - 0.5) * amplitude;
            y += (random.nextDouble() - 0.5) * amplitude;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        AbstractGuiUtils.blit(stack, texture, (int) x, (int) y,
                (int) coordinate.getWidth(), (int) coordinate.getHeight(),
                coordinate.getU0(), coordinate.getV0(),
                (int) coordinate.getUWidth(), (int) coordinate.getVHeight(),
                atlas.getTotalWidth(), atlas.getTotalHeight());
        RenderSystem.disableBlend();
    }
}
