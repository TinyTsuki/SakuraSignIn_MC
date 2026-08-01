package xin.vanilla.sakura.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.client.gui.CustomPlayerConfigEditScreen;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContextMenuItem;
import xin.vanilla.banira.client.gui.quickaction.QuickActionRegistry;
import xin.vanilla.banira.client.gui.quickaction.QuickIcon;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.SakuraComponent;

/**
 * 将 Sakura 的背包快捷入口注册到 Banira 通用快捷操作托盘。
 */
public final class SakuraQuickActions {
    static final String SIGN_IN_ID = SakuraSignIn.MODID + ":sign_in";
    private static String registeredSignature;

    private SakuraQuickActions() {
    }

    public static void register() {
        TextureCoordinate coordinates = SakuraClientState.getThemeTextureCoordinate();
        QuickActionRegistry registry = QuickActionRegistry.get();
        String signature = signature(coordinates);
        if (signature.equals(registeredSignature)
                && registry.getEntry(SIGN_IN_ID) != null) {
            return;
        }
        registry.unregister(SakuraSignIn.MODID + ":reward_option");
        registry.registerIcon(
                SIGN_IN_ID,
                icon(coordinates != null ? coordinates.getSignInBtnUV() : null, Items.CLOCK),
                SakuraComponent.get().transClient("key", "categories"),
                context -> ClientEventHandler.openSignInScreen(context.currentScreen()),
                new QuickActionContextMenuItem(
                        SakuraComponent.get().transClient("word", "edit_reward_config"),
                        context -> Minecraft.getInstance().setScreen(
                                new RewardOptionScreen().previousScreen(context.currentScreen()))
                ),
                new QuickActionContextMenuItem(
                        SakuraComponent.get().transClient("word", "edit_player_config"),
                        context -> openPlayerConfig(context.currentScreen())
                ),
                new QuickActionContextMenuItem(
                        SakuraComponent.get().transClient("word", "edit_client_config"),
                        context -> openConfig(ClientConfig.get().holder(), context.currentScreen())
                ),
                new QuickActionContextMenuItem(
                        SakuraComponent.get().transClient("word", "edit_server_config"),
                        context -> openConfig(CommonConfig.get().holder(), context.currentScreen())
                )
        );
        registeredSignature = signature;
    }

    private static void openConfig(ConfigHolder holder, Screen parent) {
        Minecraft.getInstance().setScreen(new ConfigEditorScreen(
                holder,
                new ConfigEditorScreen.Args()
                        .parentScreen(parent)
                        .season(BaniraThemes.seasonFor(SakuraSignIn.MODID))
        ));
    }

    private static void openPlayerConfig(Screen parent) {
        Minecraft.getInstance().setScreen(new CustomPlayerConfigEditScreen(
                new CustomPlayerConfigEditScreen.Args()
                        .parentScreen(parent)
                        .season(BaniraThemes.seasonFor(SakuraSignIn.MODID))
        ));
    }

    private static QuickIcon icon(Coordinate coordinate, Item fallback) {
        TextureCoordinate textureCoordinate = SakuraClientState.getThemeTextureCoordinate();
        if (SakuraClientState.getThemeTexture() == null || textureCoordinate == null || coordinate == null) {
            return QuickIcon.item(fallback);
        }
        Texture texture = Texture.of(
                        SakuraClientState.getThemeTexture(),
                        textureCoordinate.getTotalWidth(),
                        textureCoordinate.getTotalHeight())
                .u0((int) coordinate.getU0())
                .v0((int) coordinate.getV0())
                .uWidth((int) coordinate.getUWidth())
                .vHeight((int) coordinate.getVHeight());
        return QuickIcon.resource(texture);
    }

    private static String signature(TextureCoordinate coordinates) {
        String texture = String.valueOf(SakuraClientState.getThemeTexture());
        String signIn = coordinates != null ? String.valueOf(coordinates.getSignInBtnUV()) : "";
        return texture + "|" + signIn
                + "|" + SakuraComponent.get().translateClient("key", "categories")
                + "|" + SakuraComponent.get().translateClient("word", "edit_reward_config")
                + "|" + SakuraComponent.get().translateClient("word", "edit_player_config")
                + "|" + SakuraComponent.get().translateClient("word", "edit_client_config")
                + "|" + SakuraComponent.get().translateClient("word", "edit_server_config");
    }
}
