package xin.vanilla.sakura.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.DisplayEffectsScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.network.packet.ClientConfigToServer;
import xin.vanilla.sakura.network.packet.ClientLoadedToServer;
import xin.vanilla.sakura.rewards.RewardConfigManager;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.component.InventoryButton;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.*;

@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 客户端Tick事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 打开签到界面
        if (ClientModEventHandler.SIGN_IN_SCREEN_KEY.consumeClick()) {
            SakuraUtils.openSignInScreen(null);
        }
        // 打开奖励配置界面
        else if (ClientModEventHandler.REWARD_OPTION_SCREEN_KEY.consumeClick()) {
            if (!SakuraSignIn.isEnabled() || Minecraft.getInstance().player == null
                    || Minecraft.getInstance().player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())
            ) {
                Minecraft.getInstance().setScreen(new RewardOptionScreen());
            } else {
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponent(
                        Component.translatableClient(EnumI18nType.MESSAGE, "no_permission_to_open_reward")
                ));
            }
        }
    }

    /**
     * 服务端Tick事件
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        EventHandlerProxy.onServerTick(event);
    }

    /**
     * 玩家Tick事件
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        EventHandlerProxy.onPlayerTick(event);
    }

    /**
     * 能力附加事件
     */
    @SubscribeEvent
    public static void onAttachCapabilityEvent(AttachCapabilitiesEvent<Entity> event) {
        EventHandlerProxy.onAttachCapabilityEvent(event);
    }

    /**
     * 玩家死亡后重生或者从末地回主世界
     */
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        EventHandlerProxy.onPlayerCloned(event);
    }

    /**
     * 玩家进入维度
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        EventHandlerProxy.onEntityJoinWorld(event);
    }

    /**
     * 玩家登入事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EventHandlerProxy.onPlayerLoggedIn(event);
    }

    /**
     * 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        EventHandlerProxy.onPlayerLoggedOut(event);
    }

    @SubscribeEvent
    public static void onRenderScreen(GuiScreenEvent event) {
        if (event.getGui() instanceof DisplayEffectsScreen) {
            if (event.isCancelable()) event.setCanceled(false);
            if (event instanceof GuiScreenEvent.InitGuiEvent.Post) {
                if (SakuraSignIn.getThemeTexture() == null) SakuraUtils.loadThemeTexture();
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
                if (signInX > 0 && signInX <= 1) signInX *= event.getGui().width;
                if (signInY > 0 && signInY <= 1) signInY *= event.getGui().height;
                if (rewardOptionX > 0 && rewardOptionX <= 1) rewardOptionX *= event.getGui().width;
                if (rewardOptionY > 0 && rewardOptionY <= 1) rewardOptionY *= event.getGui().height;

                // 转换为有效坐标
                signInX = InventoryButton.getValidX(signInX, AbstractGuiUtils.ITEM_ICON_SIZE);
                signInY = InventoryButton.getValidY(signInY, AbstractGuiUtils.ITEM_ICON_SIZE);
                rewardOptionX = InventoryButton.getValidX(rewardOptionX, AbstractGuiUtils.ITEM_ICON_SIZE);
                rewardOptionY = InventoryButton.getValidY(rewardOptionY, AbstractGuiUtils.ITEM_ICON_SIZE);

                InventoryButton signInButton = new InventoryButton((int) signInX, (int) signInY,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        I18nUtils.getTranslationClient(EnumI18nType.KEY, "sign_in"))
                        .setUV(SakuraSignIn.getThemeTextureCoordinate().getSignInBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                        .setOnClick((button) -> SakuraUtils.openSignInScreen(event.getGui()))
                        .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_SIGN_IN_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
                InventoryButton rewardOptionButton = new InventoryButton((int) rewardOptionX, (int) rewardOptionY,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        AbstractGuiUtils.ITEM_ICON_SIZE,
                        I18nUtils.getTranslationClient(EnumI18nType.KEY, "reward_option"))
                        .setUV(SakuraSignIn.getThemeTextureCoordinate().getRewardOptionBtnUV(), SakuraSignIn.getThemeTextureCoordinate().getTotalWidth(), SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                        .setOnClick((button) -> Minecraft.getInstance().setScreen(new RewardOptionScreen().setPreviousScreen(event.getGui())))
                        .setOnDragEnd((coordinate) -> ClientConfig.INVENTORY_REWARD_OPTION_BUTTON_COORDINATE.set(String.format("%.6f,%.6f", coordinate.getX(), coordinate.getY())));
                ((GuiScreenEvent.InitGuiEvent.Post) event).addWidget(signInButton);
                if (!SakuraSignIn.isEnabled() || Minecraft.getInstance().player == null
                        || Minecraft.getInstance().player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())
                ) {
                    ((GuiScreenEvent.InitGuiEvent.Post) event).addWidget(rewardOptionButton);
                }
            }
            // 手动触发鼠标、键盘与渲染事件
            else if (event instanceof GuiScreenEvent.KeyboardKeyPressedEvent.Pre) {
                event.getGui().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).keyPressed_(
                                    ((GuiScreenEvent.KeyboardKeyPressedEvent.Pre) event).getKeyCode(),
                                    ((GuiScreenEvent.KeyboardKeyPressedEvent.Pre) event).getScanCode(),
                                    ((GuiScreenEvent.KeyboardKeyPressedEvent.Pre) event).getModifiers()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof GuiScreenEvent.KeyboardKeyReleasedEvent.Pre) {
                event.getGui().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).keyReleased_(
                                    ((GuiScreenEvent.KeyboardKeyReleasedEvent.Pre) event).getKeyCode(),
                                    ((GuiScreenEvent.KeyboardKeyReleasedEvent.Pre) event).getScanCode(),
                                    ((GuiScreenEvent.KeyboardKeyReleasedEvent.Pre) event).getModifiers()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof GuiScreenEvent.MouseClickedEvent.Pre) {
                event.getGui().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).mouseClicked_(
                                    ((GuiScreenEvent.MouseClickedEvent.Pre) event).getMouseX(),
                                    ((GuiScreenEvent.MouseClickedEvent.Pre) event).getMouseY(),
                                    ((GuiScreenEvent.MouseClickedEvent.Pre) event).getButton()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof GuiScreenEvent.MouseReleasedEvent.Pre) {
                event.getGui().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> {
                            boolean cancel = ((InventoryButton) button).mouseReleased_(
                                    ((GuiScreenEvent.MouseReleasedEvent.Pre) event).getMouseX(),
                                    ((GuiScreenEvent.MouseReleasedEvent.Pre) event).getMouseY(),
                                    ((GuiScreenEvent.MouseReleasedEvent.Pre) event).getButton()
                            );
                            if (event.isCancelable()) event.setCanceled(cancel);
                        });
            } else if (event instanceof GuiScreenEvent.DrawScreenEvent.Post) {
                event.getGui().children().stream()
                        .filter(button -> button instanceof InventoryButton)
                        .forEach(button -> ((InventoryButton) button).render_(
                                ((GuiScreenEvent.DrawScreenEvent.Post) event).getMatrixStack(),
                                ((GuiScreenEvent.DrawScreenEvent.Post) event).getMouseX(),
                                ((GuiScreenEvent.DrawScreenEvent.Post) event).getMouseY(),
                                ((GuiScreenEvent.DrawScreenEvent.Post) event).getRenderPartialTicks()
                        ));
            }
        }
        if (event instanceof GuiScreenEvent.DrawScreenEvent.Post) {
            NotificationManager.get().render(((GuiScreenEvent.DrawScreenEvent.Post) event).getMatrixStack());
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (Minecraft.getInstance().screen != null) return;
        NotificationManager.get().render(event.getMatrixStack());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        LOGGER.debug("Client: Player logged in.");
        // 加载奖励配置
        RewardConfigManager.loadRewardOption(false);
        // 同步客户端配置到服务器
        SakuraUtils.sendPacketToServer(new ClientConfigToServer());
        SakuraUtils.sendPacketToServer(new ClientLoadedToServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        LOGGER.debug("Client: Player logged out.");
        PlayerEntity player = event.getPlayer();
        if (player != null && player.level.isClientSide) {
            ClientPlayerEntity clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null && clientPlayer.getUUID().equals(player.getUUID())) {
                LOGGER.debug("Current player has logged out.");
                SakuraSignIn.setEnabled(false);
            }
        }
    }
}
