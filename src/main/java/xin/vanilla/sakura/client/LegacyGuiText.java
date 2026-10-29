package xin.vanilla.sakura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class LegacyGuiText {
    private LegacyGuiText() {
    }

    public static int draw(PoseStack pose, Font font, String text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public static int draw(PoseStack pose, Font font, Component text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public static int draw(PoseStack pose, Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }
}
