package xin.vanilla.sakura.client.ui;

import net.minecraft.util.ResourceLocation;
import xin.vanilla.banira.api.client.ui.UiGameElement;
import xin.vanilla.banira.api.client.ui.UiPageSession;
import xin.vanilla.banira.api.client.ui.UiPageSpec;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

public final class SakuraUiPages {
    private static long signInRevision;

    private SakuraUiPages() {
    }

    public static void signInChanged() {
        signInRevision++;
    }

    public static long signInRevision() {
        return signInRevision;
    }

    public static UiGameElement moduleIcon() {
        return UiGameElement.sprite(xin.vanilla.sakura.client.theme.SakuraThemeTextures.get("icon.sign_in").texture());
    }

    public static Texture themeIcon(ResourceLocation atlas, TextureCoordinate texture, int index, boolean selected) {
        if (index < 0 || index >= xin.vanilla.sakura.client.theme.BuiltInThemeCatalog.themeIds().size())
            throw new IllegalArgumentException("Invalid theme selector index");
        return xin.vanilla.sakura.client.theme.SakuraThemeTextures.get("selector."
                + xin.vanilla.sakura.client.theme.BuiltInThemeCatalog.themeIds().get(index) + (selected ? ".pressed" : ".normal")).texture();
    }

    public static UiPageSpec page(String name, BaniraScreen screen, UiPageSession session) {
        if (!com.google.common.collect.ImmutableSet.of("signin", "personal-date", "rewards", "lottery", "lottery-result").contains(name))
            throw new IllegalArgumentException("Unknown Sakura page");
        return new UiPageSpec(SakuraSignIn.MODID + ":" + name, SakuraSignIn.MODID + "/" + name + ".html",
                session, previous -> screen, screen::getEffectiveTheme);
    }
}
