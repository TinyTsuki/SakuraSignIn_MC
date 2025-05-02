package xin.vanilla.sakura.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.component.InventoryButton;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.I18nUtils;
import xin.vanilla.sakura.util.StringUtils;

/**
 * 客户端事件处理器
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = SakuraSignIn.MODID, value = Dist.CLIENT)
public class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen) {
            if (SakuraSignIn.getThemeTexture() == null) ClientModEventHandler.loadThemeTexture();
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
                    I18nUtils.getTranslationClient(EI18nType.KEY, "sign_in"))
                    .setUV(SakuraSignIn.getThemeTextureCoordinate().getSignInBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                    .setOnClick((button) -> ClientModEventHandler.openSignInScreen(event.getScreen()))
                    .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
            InventoryButton rewardOptionButton = new InventoryButton((int) rewardOptionX, (int) rewardOptionY,
                    AbstractGuiUtils.ITEM_ICON_SIZE,
                    AbstractGuiUtils.ITEM_ICON_SIZE,
                    I18nUtils.getTranslationClient(EI18nType.KEY, "reward_option"))
                    .setUV(SakuraSignIn.getThemeTextureCoordinate().getRewardOptionBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                    .setOnClick((button) -> Minecraft.getInstance().setScreen(new RewardOptionScreen().setPreviousScreen(event.getScreen())))
                    .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
            event.addListener(signInButton);
            event.addListener(rewardOptionButton);
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen) {
            event.getScreen().children().stream()
                    .filter(button -> button instanceof InventoryButton)
                    .forEach(button -> ((InventoryButton) button).render_(
                            event.getGuiGraphics(),
                            event.getMouseX(),
                            event.getMouseY(),
                            event.getPartialTick()
                    ));
        }
        if (event instanceof ScreenEvent.Render.Post) {
            NotificationManager.get().render(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen) {
            event.getScreen().children().stream()
                    .filter(button -> button instanceof InventoryButton)
                    .forEach(button -> {
                        boolean cancel = ((InventoryButton) button).keyPressed_(
                                event.getKeyCode(),
                                event.getScanCode(),
                                event.getModifiers()
                        );
                        event.setCanceled(cancel);
                    });
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.KeyReleased.Pre event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen) {
            event.getScreen().children().stream()
                    .filter(button -> button instanceof InventoryButton)
                    .forEach(button -> {
                        boolean cancel = ((InventoryButton) button).keyReleased_(
                                event.getKeyCode(),
                                event.getScanCode(),
                                event.getModifiers()
                        );
                        event.setCanceled(cancel);
                    });
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen) {
            event.getScreen().children().stream()
                    .filter(button -> button instanceof InventoryButton)
                    .forEach(button -> {
                        boolean cancel = ((InventoryButton) button).mouseClicked_(
                                event.getMouseX(),
                                event.getMouseY(),
                                event.getButton()
                        );
                        event.setCanceled(cancel);
                    });
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen) {
            event.getScreen().children().stream()
                    .filter(button -> button instanceof InventoryButton)
                    .forEach(button -> {
                        boolean cancel = ((InventoryButton) button).mouseReleased_(
                                event.getMouseX(),
                                event.getMouseY(),
                                event.getButton()
                        );
                        event.setCanceled(cancel);
                    });
        }
    }

    @SubscribeEvent()
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen != null) return;
        NotificationManager.get().render(event.getGuiGraphics());
    }
}
