package xin.vanilla.sakura.event;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.player.PlayerSignInDataCapability;
import xin.vanilla.sakura.util.GLFWKey;

@Mod.EventBusSubscriber(modid = SakuraSignIn.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientModEventHandler {
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

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        PlayerSignInDataCapability.register();
    }
}
