package xin.vanilla.sakura;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.event.BaniraEvents;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.api.reward.SakuraRewards;
import xin.vanilla.sakura.client.SakuraClientBootstrap;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.CommonConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.calendar.SakuraCalendars;
import xin.vanilla.sakura.internal.neoforge.NeoForgeSakuraEntrypoint;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardTypes;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardRulePermissions;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NeoForge 模组入口，只负责选择公共、加载器与客户端启动器。
 */
@Mod(SakuraSignIn.MODID)
public final class SakuraSignIn {
    public static final String DEFAULT_COMMAND_PREFIX = "sakura";
    public static final String MODID = "sakura_sign_in";
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicBoolean COMMON_INITIALIZED = new AtomicBoolean();

    public SakuraSignIn(IEventBus modEventBus, ModContainer modContainer) {
        initializeCommon();
        NeoForgeSakuraEntrypoint.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SakuraClientBootstrap.init();
        }
    }

    private static void initializeCommon() {
        if (!COMMON_INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        BuiltInRewardTypes.register();
        BuiltInRewardRulePermissions.registerVirtualPermissions();
        BaniraEvents.onCommonSetup(event -> event.enqueueWork(SakuraRewards::freeze));
        // 包处理器会读取配置快照，因此配置必须先于网络初始化。
        BaniraConfigs.register(CommonConfig.class, MODID);
        BaniraConfigs.register(ClientConfig.class, MODID);
        SakuraNotificationTypes.registerServerTypes();
        SakuraNetwork.initialize();

        BaniraEvents.Server.onStarting(event -> {
            SakuraCalendars.reload();
            RewardConfigManager.loadRewardOption();
            LOGGER.debug("Sign-in reward data loaded");
        });
        BaniraEvents.Server.onStopping(event -> SakuraPlayerData.saveAllAndClear());
    }
}
