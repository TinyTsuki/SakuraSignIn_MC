package xin.vanilla.sakura.event;

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
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.text.SakuraComponent;
import xin.vanilla.sakura.util.DateUtils;

/**
 * 客户端业务事件处理器，不接触具体加载器事件类型。
 */
public final class ClientEventHandler {
    private ClientEventHandler() {
    }

    public static void loadThemeTexture() {
        BuiltInThemeDescriptor theme = BuiltInThemeCatalog.load(
                ClientConfig.get().display().themeId(),
                Minecraft.getInstance().getResourceManager());
        TextureCoordinate coordinates = theme.getCoordinates();
        boolean specialVariant = ClientConfig.get().display().specialVariant()
                && coordinates.isSpecial();

        SakuraClientState.setActiveThemeId(theme.getId());
        SakuraClientState.setThemeTexture(theme.textureLocation());
        SakuraClientState.setThemeTextureCoordinate(coordinates);
        SakuraClientState.setSpecialThemeVariant(specialVariant);

        // 特殊版本复用同一图集，只改变两个签到状态图标的绘制偏移。
        int signInIconOffset = specialVariant ? 320 : 0;
        coordinates.getNotSignedInUV().setX(signInIconOffset);
        coordinates.getSignedInUV().setX(signInIconOffset);
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
                    RewardManager.getCompensateDate(DateUtils.getClientDate()));
            Minecraft.getInstance().setScreen(new SignInScreen().previousScreen(previousScreen));
            return;
        }
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            Component component = SakuraComponent.get().transClient(
                    "message", "sakura_is_offline");
            SakuraClientNotifications.error(component, SakuraNotificationTypes.SIGN_IN);
        }
    }
}
