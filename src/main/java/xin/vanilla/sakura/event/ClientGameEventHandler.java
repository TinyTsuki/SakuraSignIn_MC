package xin.vanilla.sakura.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.PlayerDataAttachment;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ESignInType;
import xin.vanilla.sakura.network.packet.ClientConfigSyncPacket;
import xin.vanilla.sakura.network.packet.ClientModLoadedNotice;
import xin.vanilla.sakura.network.packet.ServerTimeSyncPacket;
import xin.vanilla.sakura.network.packet.SignInPacket;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.component.InventoryButton;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.util.*;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 客户端事件处理器
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, modid = SakuraSignIn.MODID, value = Dist.CLIENT)
public class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.debug("Client: Player logged in.");
        // 同步客户端配置到服务器
        PacketDistributor.sendToServer(new ClientConfigSyncPacket());
        PacketDistributor.sendToServer(new ClientModLoadedNotice());
    }

    /**
     * 同步客户端服务端数据
     */
    @SubscribeEvent
    public static void playerTickEvent(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 不用给未安装mod的玩家发送数据包
            if (SakuraSignIn.getPlayerCapabilityStatus().containsKey(player.getUUID().toString())) {
                // 同步玩家签到数据到客户端
                if (!SakuraSignIn.getPlayerCapabilityStatus().getOrDefault(player.getUUID().toString(), true)) {
                    // 如果玩家还活着则同步玩家传送数据到客户端
                    if (player.isAlive()) {
                        try {
                            PlayerDataAttachment.syncPlayerData(player);
                        } catch (Exception e) {
                            LOGGER.error("Failed to sync player data: ", e);
                        }
                    }
                }
                // 同步服务器时间到客户端
                player.connection.send(new ServerTimeSyncPacket());
            }
        }
    }

    /**
     * 玩家死亡后重生或者从末地回主世界
     */
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        ServerPlayer original = (ServerPlayer) event.getOriginal();
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        original.revive();

        SakuraUtils.cloneServerPlayerLanguage(original, newPlayer);
        PlayerSignInData oldDataCap = PlayerDataAttachment.getData(original);
        PlayerSignInData newDataCap = PlayerDataAttachment.getData(newPlayer);
        newDataCap.copyFrom(oldDataCap);
        if (SakuraSignIn.getPlayerCapabilityStatus().containsKey(newPlayer.getUUID().toString())) {
            SakuraSignIn.getPlayerCapabilityStatus().put(newPlayer.getUUID().toString(), false);
        }
    }

    /**
     * 玩家进入维度
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (SakuraSignIn.getPlayerCapabilityStatus().containsKey(player.getUUID().toString())) {
                SakuraSignIn.getPlayerCapabilityStatus().put(player.getUUID().toString(), false);
            }
            SakuraSignIn.EXECUTOR_SERVICE.submit(() -> {
                // IDEA 又开始了
                for (int i = 0; i < 120 && player.getChatVisibility() == null; i++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException e) {
                        // 恢复中断状态
                        Thread.currentThread().interrupt();
                        LOGGER.warn("Thread interrupted while waiting for player client settings.", e);
                        return;
                    }
                    LOGGER.debug("Waiting for player client setting: {}, {}.", SakuraUtils.getPlayerLanguage(player), i);
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Thread interrupted before processing sign in.", e);
                    return;
                }

                Objects.requireNonNull(player.getServer()).execute(() -> {
                    LOGGER.debug("Player language: {}.", SakuraUtils.getPlayerLanguage(player));
                    PlayerSignInData data = PlayerDataAttachment.getData(player);
                    if (ServerConfig.AUTO_SIGN_IN.get() && !RewardManager.isSignedIn(data, DateUtils.getServerDate(), true)) {
                        RewardManager.signIn(player, new SignInPacket(
                                DateUtils.toDateTimeString(DateUtils.getServerDate()),
                                data.isAutoRewarded(),
                                ESignInType.SIGN_IN
                        ));
                    }
                });
            });
        }
    }

    /**
     * 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家退出服务器时移除键(移除mod安装状态)
        if (event.getEntity() instanceof ServerPlayer) {
            SakuraSignIn.getPlayerCapabilityStatus().remove(event.getEntity().getStringUUID());
        }
    }

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
