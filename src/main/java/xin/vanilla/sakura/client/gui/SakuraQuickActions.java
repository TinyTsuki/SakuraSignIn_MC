package xin.vanilla.sakura.client.gui;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.quickaction.QuickActionRegistry;
import xin.vanilla.banira.client.gui.quickaction.QuickIcon;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.text.SakuraComponent;

/**
 * 将 Sakura 的背包快捷入口注册到 Banira 通用快捷操作托盘。
 */
public final class SakuraQuickActions {
    static final String SIGN_IN_ID = SakuraSignIn.MODID + ":sign_in";
    static final String REWARD_OPTION_ID = SakuraSignIn.MODID + ":reward_option";
    private static String registeredSignature;

    private SakuraQuickActions() {
    }

    public static void register() {
        TextureCoordinate coordinates = SakuraSignIn.getThemeTextureCoordinate();
        QuickActionRegistry registry = QuickActionRegistry.get();
        String signature = signature(coordinates);
        if (signature.equals(registeredSignature)
                && registry.getEntry(SIGN_IN_ID) != null
                && registry.getEntry(REWARD_OPTION_ID) != null) {
            return;
        }
        registry.registerIcon(
                SIGN_IN_ID,
                icon(coordinates != null ? coordinates.getSignInBtnUV() : null, Items.CLOCK),
                SakuraComponent.get().transClient("key", "sign_in"),
                context -> ClientEventHandler.openSignInScreen(context.currentScreen())
        );
        registry.registerIcon(
                REWARD_OPTION_ID,
                icon(coordinates != null ? coordinates.getRewardOptionBtnUV() : null, Items.WRITABLE_BOOK),
                SakuraComponent.get().transClient("key", "reward_option"),
                context -> context.minecraft().setScreen(
                        new RewardOptionScreen().previousScreen(context.currentScreen()))
        );
        registeredSignature = signature;
    }

    private static QuickIcon icon(Coordinate coordinate, Item fallback) {
        TextureCoordinate textureCoordinate = SakuraSignIn.getThemeTextureCoordinate();
        if (SakuraSignIn.getThemeTexture() == null || textureCoordinate == null || coordinate == null) {
            return QuickIcon.item(fallback);
        }
        Texture texture = Texture.of(
                        SakuraSignIn.getThemeTexture(),
                        textureCoordinate.getTotalWidth(),
                        textureCoordinate.getTotalHeight())
                .u0((int) coordinate.getU0())
                .v0((int) coordinate.getV0())
                .uWidth((int) coordinate.getUWidth())
                .vHeight((int) coordinate.getVHeight());
        return QuickIcon.resource(texture);
    }

    private static String signature(TextureCoordinate coordinates) {
        String texture = String.valueOf(SakuraSignIn.getThemeTexture());
        String signIn = coordinates != null ? String.valueOf(coordinates.getSignInBtnUV()) : "";
        String reward = coordinates != null ? String.valueOf(coordinates.getRewardOptionBtnUV()) : "";
        return texture + "|" + signIn + "|" + reward
                + "|" + SakuraComponent.get().translateClient("key", "sign_in")
                + "|" + SakuraComponent.get().translateClient("key", "reward_option");
    }
}
