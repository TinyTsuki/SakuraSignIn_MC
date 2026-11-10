package xin.vanilla.sakura.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;

public final class LegacyGuiText {
    private LegacyGuiText() {
    }

    public static int draw(MatrixStack pose, FontRenderer font, String text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public static int draw(MatrixStack pose, FontRenderer font, ITextComponent text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public static int draw(MatrixStack pose, FontRenderer font, IReorderingProcessor text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
}
