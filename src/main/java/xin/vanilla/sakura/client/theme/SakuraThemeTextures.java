package xin.vanilla.sakura.client.theme;

import xin.vanilla.sakura.client.SakuraClientState;

public final class SakuraThemeTextures {
    private SakuraThemeTextures() { }
    private static final ThemeTextureBindings BINDINGS = new ThemeTextureBindings();
    public static void install(BuiltInThemeDescriptor theme) {
        var manager = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        BINDINGS.replace(theme.getResolved().values(), texture -> {
            try (var input = texture.openPng()) {
                var dynamic = ThemeTextureBindings.transfer(com.mojang.blaze3d.platform.NativeImage.read(input),
                        net.minecraft.client.renderer.texture.DynamicTexture::new);
                try { manager.register(texture.location(), dynamic); }
                catch (RuntimeException | Error failure) { dynamic.close(); throw failure; }
            } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot upload packaged theme texture", failure); }
        }, manager::release);
    }
    private static final class DefaultTheme { static final BuiltInThemeDescriptor VALUE = BuiltInThemeCatalog.load(BuiltInThemeCatalog.DEFAULT_THEME_ID); }
    public static BuiltInThemeDescriptor current() {
        BuiltInThemeDescriptor theme = SakuraClientState.getTheme();
        return theme == null ? DefaultTheme.VALUE : theme;
    }
    public static ThemeTexture get(String key) { return current().texture(key); }
    public static ThemeTexture status(String role) {
        if("rewarded".equals(role)) return get("status.rewarded");
        return get("status." + role + (SakuraClientState.isSpecialThemeVariant() ? ".special" : ".normal"));
    }
}
