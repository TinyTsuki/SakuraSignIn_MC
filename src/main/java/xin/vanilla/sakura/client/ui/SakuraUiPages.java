package xin.vanilla.sakura.client.ui;

import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.api.client.ui.UiPageSession;
import xin.vanilla.banira.api.client.ui.UiPageSpec;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

public final class SakuraUiPages {
    private static long signInRevision;
    private SakuraUiPages() { }
    public static void signInChanged() { signInRevision++; }
    public static long signInRevision() { return signInRevision; }
    public static Texture themeIcon(ResourceLocation atlas, TextureCoordinate texture, int index, boolean selected) {
        Coordinate uv = selected ? texture.getThemeTapUV() : texture.getThemeUV();
        return Texture.of(atlas, texture.getTotalWidth(), texture.getTotalHeight())
                .u0((int) (uv.getU0() + index * texture.getThemeUV().getUWidth()))
                .v0((int) uv.getV0()).uWidth((int) uv.getUWidth()).vHeight((int) uv.getVHeight());
    }
    public static UiPageSpec page(String name, BaniraScreen screen, UiPageSession session) {
        if (!name.equals("signin") && !name.equals("personal-date")) throw new IllegalArgumentException("Unknown Sakura page");
        return new UiPageSpec(SakuraSignIn.MODID + ":" + name, SakuraSignIn.MODID + "/" + name + ".html",
                session, previous -> screen, screen::getEffectiveTheme);
    }
}
