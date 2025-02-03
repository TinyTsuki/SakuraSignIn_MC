package xin.vanilla.sakura.event;

import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.capability.IPlayerSignInData;
import xin.vanilla.sakura.config.ServerConfig;

/**
 * Mod 事件处理器
 */
@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(IPlayerSignInData.class);
    }

    @SubscribeEvent
    public void onConfigLoadOrReload(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ServerConfig.SERVER_CONFIG) {
            LOGGER.debug("Server Config loaded/reloaded.");
        }
    }

}
