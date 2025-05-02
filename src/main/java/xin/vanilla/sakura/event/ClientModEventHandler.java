package xin.vanilla.sakura.event;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.util.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static xin.vanilla.sakura.SakuraSignIn.PNG_CHUNK_NAME;

/**
 * 客户端事件处理器
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = SakuraSignIn.MODID, value = Dist.CLIENT)
public class ClientModEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CATEGORIES = "key.sakura_sign_in.categories";

    // 定义按键绑定
    public static final KeyMapping SIGN_IN_SCREEN_KEY = new KeyMapping("key.sakura_sign_in.sign_in",
            GLFWKey.GLFW_KEY_H, CATEGORIES);
    public static final KeyMapping REWARD_OPTION_SCREEN_KEY = new KeyMapping("key.sakura_sign_in.reward_option",
            GLFWKey.GLFW_KEY_O, CATEGORIES);

    /**
     * 在客户端设置阶段触发的事件处理方法
     * 此方法主要用于接收 FML 客户端设置事件，并执行相应的初始化操作
     */
    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // 创建配置文件目录
        ClientModEventHandler.createConfigPath();
    }

    /**
     * 注册键绑定
     */
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        LOGGER.debug("Registering key bindings");
        event.register(SIGN_IN_SCREEN_KEY);
        event.register(REWARD_OPTION_SCREEN_KEY);
    }

    /**
     * 创建配置文件目录
     */
    public static void createConfigPath() {
        File themesPath = new File(FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID).toFile(), "themes");
        if (!themesPath.exists()) {
            themesPath.mkdirs();
        }
    }

    /**
     * 加载主题纹理
     */
    public static void loadThemeTexture() {
        try {
            SakuraSignIn.setThemeTexture(TextureUtils.loadCustomTexture(ClientConfig.THEME.get()));
            SakuraSignIn.setSpecialVersionTheme(Boolean.TRUE.equals(ClientConfig.SPECIAL_THEME.get()));
            InputStream inputStream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(SakuraSignIn.getThemeTexture()).open();
            SakuraSignIn.setThemeTextureCoordinate(PNGUtils.readLastPrivateChunk(inputStream, PNG_CHUNK_NAME));
        } catch (IOException | ClassNotFoundException ignored) {
        }
        if (SakuraSignIn.getThemeTextureCoordinate(false) == null) {
            // 使用默认配置
            SakuraSignIn.setThemeTextureCoordinate(TextureCoordinate.getDefault());
        }
        // 设置内置主题特殊图标UV的偏移量
        if (SakuraSignIn.isSpecialVersionTheme() && SakuraSignIn.getThemeTextureCoordinate().isSpecial()) {
            SakuraSignIn.getThemeTextureCoordinate().getNotSignedInUV().setX(320);
            SakuraSignIn.getThemeTextureCoordinate().getSignedInUV().setX(320);
        } else {
            SakuraSignIn.getThemeTextureCoordinate().getNotSignedInUV().setX(0);
            SakuraSignIn.getThemeTextureCoordinate().getSignedInUV().setX(0);
        }
    }

    /**
     * 在客户端Tick事件触发时执行
     *
     * @param event 客户端Tick事件
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        // 检测并消费点击事件
        if (SIGN_IN_SCREEN_KEY.consumeClick()) {
            // 打开签到界面
            ClientModEventHandler.openSignInScreen(null);
        } else if (REWARD_OPTION_SCREEN_KEY.consumeClick()) {
            // 打开奖励配置界面
            Minecraft.getInstance().setScreen(new RewardOptionScreen());
        }
    }

    public static void openSignInScreen(Screen previousScreen) {
        if (SakuraSignIn.isEnabled()) {
            SakuraSignIn.setCalendarCurrentDate(RewardManager.getCompensateDate(DateUtils.getClientDate()));
            Minecraft.getInstance().setScreen(new SignInScreen().setPreviousScreen(previousScreen));
        } else {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                Component component = Component.translatableClient(EI18nType.MESSAGE, "sakura_is_offline");
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgColor(0x88FF5555));
            }
        }
    }
}
