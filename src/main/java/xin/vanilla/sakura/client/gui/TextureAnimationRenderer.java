package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.util.SakuraUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 奖励编辑器工具栏的纹理变换绘制。
 */
final class TextureAnimationRenderer {
    private TextureAnimationRenderer() {
    }

    static void drawRotated(PoseStack stack, xin.vanilla.sakura.client.theme.ThemeTexture texture,
                            Coordinate coordinate,
                            double angle, boolean flipHorizontal,
                            boolean flipVertical) {
        int width = (int) coordinate.getWidth();
        int height = (int) coordinate.getHeight();
        double u = 0;
        double v = 0;
        int uWidth = texture.width();
        int vHeight = texture.height();
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
        stack.mulPose(Vector3f.ZP.rotationDegrees((float) angle));
        stack.translate(-width / 2.0, -height / 2.0, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        AbstractGuiUtils.blit(stack, texture.location(), 0, 0, width, height,
                u, v, uWidth, vHeight, texture.width(), texture.height());
        RenderSystem.disableBlend();
        stack.popPose();
    }

    static void drawTrembling(PoseStack stack, xin.vanilla.sakura.client.theme.ThemeTexture texture,
                              Coordinate coordinate,
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
        texture.draw(stack, (int) x, (int) y, (int) coordinate.getWidth(), (int) coordinate.getHeight());
        RenderSystem.disableBlend();
    }
}
