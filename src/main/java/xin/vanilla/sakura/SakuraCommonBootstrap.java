package xin.vanilla.sakura;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.event.BaniraEvents;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.RewardConfigManager;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;

import java.util.concurrent.atomic.AtomicBoolean;

import static xin.vanilla.sakura.SakuraSignIn.MODID;

/**
 * 跨加载器公共启动器，注册顺序在所有版本中保持一致。
 */
public final class SakuraCommonBootstrap {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private SakuraCommonBootstrap() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        // 配置必须先于网络初始化，包处理器会读取当前配置快照。
        BaniraConfig.register(CommonConfig.class, MODID);
        BaniraConfig.register(ClientConfig.class, MODID);
        SakuraNotificationTypes.registerServerTypes();
        SakuraNetwork.initialize();

        BaniraEvents.Server.onStarting(event -> {
            RewardConfigManager.loadRewardOption();
            LOGGER.debug("Sign-in reward data loaded");
        });
        BaniraEvents.Server.onStopping(event -> SakuraPlayerData.saveAllAndClear());
    }
}
