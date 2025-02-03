package xin.vanilla.sakura.event;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.screen.component.InventoryButton;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.sakura.util.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static xin.vanilla.sakura.SakuraSignIn.PNG_CHUNK_NAME;
import static xin.vanilla.sakura.util.I18nUtils.getI18nKey;

/**
 * 客户端事件处理器
 */
@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CATEGORIES = "key.sakura_sign_in.categories";

    // 定义按键绑定
    public static final KeyMapping SIGN_IN_SCREEN_KEY = new KeyMapping("key.sakura_sign_in.sign_in",
            GLFW.GLFW_KEY_H, CATEGORIES);
    public static final KeyMapping REWARD_OPTION_SCREEN_KEY = new KeyMapping("key.sakura_sign_in.reward_option",
            GLFW.GLFW_KEY_O, CATEGORIES);

    /**
     * 注册键绑定
     */
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
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 检测并消费点击事件
        if (SIGN_IN_SCREEN_KEY.consumeClick()) {
            // 打开签到界面
            ClientEventHandler.openSignInScreen(null);
        } else if (REWARD_OPTION_SCREEN_KEY.consumeClick()) {
            // 打开奖励配置界面
            Minecraft.getInstance().setScreen(new RewardOptionScreen());
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen) {
            if (event.isCancelable()) event.setCanceled(false);
            if (event instanceof ScreenEvent.Init.Post) {
                if (SakuraSignIn.getThemeTexture() == null) ClientEventHandler.loadThemeTexture();
                // 创建按钮并添加到界面
                String[] signInCoordinate = ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.get().split(",");
                String[] rewardOptionCoordinate = ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.get().split(",");
                double signInX_ = signInCoordinate.length == 2 ? StringUtils.toFloat(signInCoordinate[0]) : 0;
                double signInY_ = signInCoordinate.length == 2 ? StringUtils.toFloat(signInCoordinate[1]) : 0;
                double rewardOptionX_ = rewardOptionCoordinate.length == 2 ? StringUtils.toFloat(rewardOptionCoordinate[0]) : 0;
                double rewardOptionY_ = rewardOptionCoordinate.length == 2 ? StringUtils.toFloat(rewardOptionCoordinate[1]) : 0;

                double signInX = signInX_;
                double signInY = signInY_;
                double rewardOptionX = rewardOptionX_;
                double rewardOptionY = rewardOptionY_;

                // 如果坐标为0则设置默认坐标
                if (signInX == 0) signInX = 2;
                if (signInY == 0) signInY = 2;
                if (rewardOptionX == 0) rewardOptionX = 20;
                if (rewardOptionY == 0) rewardOptionY = 2;

                // 如果坐标发生变化则保存到配置文件
                if (signInX_ != signInX || signInY_ != signInY) {
                    ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", signInX, signInY));
                }
                if (rewardOptionX_ != rewardOptionX || rewardOptionY_ != rewardOptionY) {
                    ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", rewardOptionX, rewardOptionY));
                }

                // 如果坐标为百分比则转换为像素坐标
                if (signInX > 0 && signInX <= 1) signInX *= event.getScreen().width;
                if (signInY > 0 && signInY <= 1) signInY *= event.getScreen().height;
                if (rewardOptionX > 0 && rewardOptionX <= 1) rewardOptionX *= event.getScreen().width;
                if (rewardOptionY > 0 && rewardOptionY <= 1) rewardOptionY *= event.getScreen().height;

                // 转换为有效坐标
                signInX = InventoryButton.getValidX(signInX, AbstractGuiUtils.ITEM_ICON_SIZE);
                signInY = InventoryButton.getValidY(signInY, AbstractGuiUtils.ITEM_ICON_SIZE);
                rewardOptionX = InventoryButton.getValidX(rewardOptionX, AbstractGuiUtils.ITEM_ICON_SIZE);
                rewardOptionY = InventoryButton.getValidY(rewardOptionY, AbstractGuiUtils.ITEM_ICON_SIZE);

                InventoryButton signInButton = new InventoryButton((int) signInX, (int) signInY,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        I18nUtils.get("key.sakura_sign_in.sign_in"))
                        .setUV(SakuraSignIn.getThemeTextureCoordinate().getSignInBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                        .setOnClick((button) -> ClientEventHandler.openSignInScreen(event.getScreen()))
                        .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
                InventoryButton rewardOptionButton = new InventoryButton((int) rewardOptionX, (int) rewardOptionY,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        I18nUtils.get("key.sakura_sign_in.reward_option"))
                        .setUV(SakuraSignIn.getThemeTextureCoordinate().getRewardOptionBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                        .setOnClick((button) -> Minecraft.getInstance().setScreen(new RewardOptionScreen().setPreviousScreen(event.getScreen())))
                        .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
                ((ScreenEvent.Init.Post) event).addListener(signInButton);
                ((ScreenEvent.Init.Post) event).addListener(rewardOptionButton);
            }
            // 手动触发鼠标、键盘与渲染事件
            else if (event instanceof ScreenEvent.KeyPressed.Pre) {
                event.getScreen().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).keyPressed_(
                                    ((ScreenEvent.KeyPressed.Pre) event).getKeyCode(),
                                    ((ScreenEvent.KeyPressed.Pre) event).getScanCode(),
                                    ((ScreenEvent.KeyPressed.Pre) event).getModifiers()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof ScreenEvent.KeyReleased.Pre) {
                event.getScreen().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).keyReleased_(
                                    ((ScreenEvent.KeyReleased.Pre) event).getKeyCode(),
                                    ((ScreenEvent.KeyReleased.Pre) event).getScanCode(),
                                    ((ScreenEvent.KeyReleased.Pre) event).getModifiers()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof ScreenEvent.MouseButtonPressed.Pre) {
                event.getScreen().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).mouseClicked_(
                                    ((ScreenEvent.MouseButtonPressed.Pre) event).getMouseX(),
                                    ((ScreenEvent.MouseButtonPressed.Pre) event).getMouseY(),
                                    ((ScreenEvent.MouseButtonPressed.Pre) event).getButton()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof ScreenEvent.MouseButtonReleased.Pre) {
                event.getScreen().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).mouseReleased_(
                                    ((ScreenEvent.MouseButtonReleased.Pre) event).getMouseX(),
                                    ((ScreenEvent.MouseButtonReleased.Pre) event).getMouseY(),
                                    ((ScreenEvent.MouseButtonReleased.Pre) event).getButton()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof ScreenEvent.Render.Post) {
                event.getScreen().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> ((InventoryButton) button).render_(
                                ((ScreenEvent.Render.Post) event).getGuiGraphics(),
                                ((ScreenEvent.Render.Post) event).getMouseX(),
                                ((ScreenEvent.Render.Post) event).getMouseY(),
                                ((ScreenEvent.Render.Post) event).getPartialTick()
                        ));
            }
        }
    }

    public static void openSignInScreen(Screen previousScreen) {
        if (SakuraSignIn.isEnabled()) {
            SakuraSignIn.setCalendarCurrentDate(RewardManager.getCompensateDate(DateUtils.getClientDate()));
            Minecraft.getInstance().setScreen(new SignInScreen().setPreviousScreen(previousScreen));
        } else {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.translatable(getI18nKey("SakuraSignIn server is offline!")));
            }
        }
    }
}
