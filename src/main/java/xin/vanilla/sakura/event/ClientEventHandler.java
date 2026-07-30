package xin.vanilla.sakura.event;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.text.SakuraComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
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
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.GLFWKey;

/**
 * 客户端事件处理器
 */
@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CATEGORIES = "key.sakura_sign_in.categories";

    // 定义按键绑定
    public static KeyBinding SIGN_IN_SCREEN_KEY = new KeyBinding("key.sakura_sign_in.sign_in",
            GLFWKey.GLFW_KEY_H, CATEGORIES);
    public static KeyBinding REWARD_OPTION_SCREEN_KEY = new KeyBinding("key.sakura_sign_in.reward_option",
            GLFWKey.GLFW_KEY_O, CATEGORIES);

    /**
     * 注册键绑定
     */
    public static void registerKeyBindings() {
        ClientRegistry.registerKeyBinding(SIGN_IN_SCREEN_KEY);
        ClientRegistry.registerKeyBinding(REWARD_OPTION_SCREEN_KEY);
    }

    /**
     * 加载主题纹理
     */
    public static void loadThemeTexture() {
        BuiltInThemeDescriptor theme = BuiltInThemeCatalog.load(
                ClientConfig.get().display().themeId());
        TextureCoordinate coordinates = theme.getCoordinates();
        boolean specialVariant = ClientConfig.get().display().specialVariant()
                && coordinates.isSpecial();

        SakuraSignIn.setActiveThemeId(theme.getId());
        SakuraSignIn.setThemeTexture(theme.textureLocation());
        SakuraSignIn.setThemeTextureCoordinate(coordinates);
        SakuraSignIn.setSpecialThemeVariant(specialVariant);

        // 特殊版本复用同一图集，只改变两个签到状态图标的绘制偏移。
        if (specialVariant) {
            coordinates.getNotSignedInUV().setX(320);
            coordinates.getSignedInUV().setX(320);
        } else {
            coordinates.getNotSignedInUV().setX(0);
            coordinates.getSignedInUV().setX(0);
        }
        // 主题切换后重注册，快捷入口图标会同步使用新的纹理区域。
        SakuraQuickActions.register();
    }

    /**
     * 在客户端Tick事件触发时执行
     *
     * @param event 客户端Tick事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        SakuraUiSmokeRunner.tick();

        // 检测并消费点击事件
        if (SIGN_IN_SCREEN_KEY.consumeClick()) {
            // 打开签到界面
            ClientEventHandler.openSignInScreen(null);
        } else if (REWARD_OPTION_SCREEN_KEY.consumeClick()) {
            // 打开奖励配置界面
            Minecraft.getInstance().setScreen(new RewardOptionScreen());
        }
    }

    public static void openSignInScreen(Screen previousScreen) {
        if (SakuraSignIn.isEnabled()) {
            SakuraSignIn.setCalendarCurrentDate(RewardManager.getCompensateDate(DateUtils.getClientDate()));
            Minecraft.getInstance().setScreen(new SignInScreen().previousScreen(previousScreen));
        } else {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                Component component = SakuraComponent.get().transClient("message", "sakura_is_offline");
                SakuraClientNotifications.error(component, SakuraNotificationTypes.SIGN_IN);
            }
        }
    }
}
