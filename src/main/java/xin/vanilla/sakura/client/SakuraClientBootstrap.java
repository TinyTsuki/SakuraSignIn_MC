package xin.vanilla.sakura.client;

import lombok.Getter;
import xin.vanilla.banira.api.client.BaniraInput;
import xin.vanilla.banira.api.client.BaniraKeyHandle;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.network.ClientProxy;
import xin.vanilla.sakura.network.SakuraClientPacketHandlers;
import xin.vanilla.sakura.notification.SakuraClientNotificationTypes;
import xin.vanilla.banira.client.data.GLFWKey;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户端唯一启动入口，Fabric 与高版本分支保持同一注册结构。
 */
public final class SakuraClientBootstrap {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    @Getter
    private static BaniraKeyHandle signInKey;

    private SakuraClientBootstrap() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        BaniraThemes.register(SakuraSignIn.MODID,
                () -> ClientConfig.get().display().interfaceThemeMode());
        signInKey = BaniraInput.registerKey(
                SakuraSignIn.MODID, "sign_in", GLFWKey.GLFW_KEY_H);
        BaniraKeyHandle rewardOptionKey = BaniraInput.registerKey(
                SakuraSignIn.MODID, "reward_option", GLFWKey.GLFW_KEY_O);

        SakuraClientPacketHandlers.register(
                ClientProxy::handleSynPlayerData,
                ClientProxy::handleMonthData,
                ClientProxy::handleAdvancement,
                ClientProxy::handleRewardOptionSync,
                ClientProxy::handleRewardOptionUploadResult
        );
        BaniraClientEvents.ModLifecycle.onClientSetup(event -> {
            SakuraClientNotificationTypes.register();
            ClientEventHandler.loadThemeTexture();
        });
        BaniraClientEvents.Client.onClientTick(event ->
                ClientEventHandler.onClientTick(signInKey, rewardOptionKey));
        BaniraClientEvents.Player.onClientLoggedOut(event -> {
            SakuraClientState.clearSession();
            SakuraPlayerData.clearClient();
        });
    }
}
