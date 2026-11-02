package xin.vanilla.sakura.event;

import xin.vanilla.sakura.data.time.SakuraClock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import xin.vanilla.banira.api.client.BaniraKeyHandle;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.SakuraQuickActions;
import xin.vanilla.sakura.client.theme.BuiltInThemeCatalog;
import xin.vanilla.sakura.client.theme.BuiltInThemeDescriptor;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.internal.client.dev.SakuraUiSmokeRunner;
import xin.vanilla.sakura.notification.SakuraClientNotifications;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.banira.common.util.DateUtils;

/**
 * 客户端业务事件处理器，不接触具体加载器事件类型。
 */
public final class ClientEventHandler {
    private ClientEventHandler() {
    }

    public static void loadThemeTexture() {
        if (!Minecraft.getInstance().isSameThread()) {
            Minecraft.getInstance().execute(ClientEventHandler::loadThemeTexture);
            return;
        }
        BuiltInThemeDescriptor theme = BuiltInThemeCatalog.load(
                ClientConfig.get().display().themeId(),
                Minecraft.getInstance().getResourceManager());
        TextureCoordinate coordinates = theme.getLayout();
        boolean specialVariant = ClientConfig.get().display().specialVariant()
                && coordinates.isSpecial();

        xin.vanilla.sakura.client.theme.SakuraThemeTextures.install(theme);
        SakuraClientState.setActiveThemeId(theme.getId());
        SakuraClientState.setTheme(theme);
        SakuraClientState.setThemeTexture(theme.texture("background.calendar").location());
        SakuraClientState.setThemeTextureCoordinate(coordinates);
        SakuraClientState.setSpecialThemeVariant(specialVariant);

        SakuraQuickActions.register();
    }

    public static void onClientTick(BaniraKeyHandle signInKey,
                                    BaniraKeyHandle rewardOptionKey) {
        SakuraUiSmokeRunner.tick();
        if (signInKey.consumeClick()) {
            openSignInScreen(null);
        } else if (rewardOptionKey.consumeClick()) {
            Minecraft.getInstance().setScreen(new RewardOptionScreen());
        }
    }

    public static void openSignInScreen(Screen previousScreen) {
        if (SakuraClientState.isEnabled()) {
            SakuraClientState.setCalendarCurrentDate(
                    RewardManager.getCompensateDate(SakuraClock.clientNow()));
            Minecraft.getInstance().setScreen(new SignInScreen().previousScreen(previousScreen));
            return;
        }
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            Component component = SakuraComponent.get().transClient(
                    "word", "sakura_is_offline");
            SakuraClientNotifications.error(component, SakuraNotificationTypes.SIGN_IN);
        }
    }
}
