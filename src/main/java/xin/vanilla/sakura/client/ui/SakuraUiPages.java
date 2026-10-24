package xin.vanilla.sakura.client.ui;

import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.api.client.ui.UiPageSession;
import xin.vanilla.banira.api.client.ui.UiPageSpec;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

public final class SakuraUiPages {
    private static long signInRevision;
    private SakuraUiPages() { }
    public static void signInChanged() { signInRevision++; }
    public static long signInRevision() { return signInRevision; }
    public static UiGameElement moduleIcon() {
        ResourceLocation atlas = SakuraClientState.getThemeTexture();
        TextureCoordinate texture = SakuraClientState.getThemeTextureCoordinate();
        if (atlas == null || !validModuleIcon(texture)) {
            atlas = new ResourceLocation(SakuraSignIn.MODID, "textures/gui/sign_in_calendar_original.png");
            texture = new TextureCoordinate();
        }
        Coordinate uv = texture.getSignInBtnUV();
        return UiGameElement.sprite(Texture.of(atlas, texture.getTotalWidth(), texture.getTotalHeight())
                .u0((int) uv.getU0()).v0((int) uv.getV0()).uWidth((int) uv.getUWidth()).vHeight((int) uv.getVHeight()));
    }
    private static boolean validModuleIcon(TextureCoordinate texture) {
        if (texture == null || texture.getTotalWidth() <= 0 || texture.getTotalHeight() <= 0) return false;
        Coordinate uv = texture.getSignInBtnUV();
        return Double.isFinite(uv.getU0()) && Double.isFinite(uv.getV0())
                && Double.isFinite(uv.getUWidth()) && Double.isFinite(uv.getVHeight())
                && uv.getU0() >= 0 && uv.getV0() >= 0 && uv.getUWidth() >= 1 && uv.getVHeight() >= 1
                && uv.getU0() + uv.getUWidth() <= texture.getTotalWidth()
                && uv.getV0() + uv.getVHeight() <= texture.getTotalHeight();
    }
    public static Texture themeIcon(ResourceLocation atlas, TextureCoordinate texture, int index, boolean selected) {
        Coordinate uv = selected ? texture.getThemeTapUV() : texture.getThemeUV();
        return Texture.of(atlas, texture.getTotalWidth(), texture.getTotalHeight())
                .u0((int) (uv.getU0() + index * texture.getThemeUV().getUWidth()))
                .v0((int) uv.getV0()).uWidth((int) uv.getUWidth()).vHeight((int) uv.getVHeight());
    }
    public static UiPageSpec page(String name, BaniraScreen screen, UiPageSession session) {
        if (!java.util.Set.of("signin", "personal-date", "rewards", "lottery", "lottery-result").contains(name)) throw new IllegalArgumentException("Unknown Sakura page");
        return new UiPageSpec(SakuraSignIn.MODID + ":" + name, SakuraSignIn.MODID + "/" + name + ".html",
                session, previous -> screen, screen::getEffectiveTheme);
    }
}
