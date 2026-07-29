package xin.vanilla.sakura.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.screen.RewardOptionScreen;

/**
 * 仅在开发环境按显式目标打开界面，供有界 runClient 烟测使用。
 */
public final class SakuraUiSmokeRunner {
    public static final String ENVIRONMENT_KEY = "SAKURA_UI_SMOKE";
    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean opened;

    private SakuraUiSmokeRunner() {
    }

    public static void tick() {
        if (opened || FMLEnvironment.production) {
            return;
        }

        String target = System.getenv(ENVIRONMENT_KEY);
        if (!"reward".equalsIgnoreCase(target)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        boolean inWorldWithoutScreen = minecraft.player != null
                && minecraft.level != null
                && parent == null;
        boolean atMainMenu = parent instanceof MainMenuScreen;
        if (!inWorldWithoutScreen && !atMainMenu) {
            return;
        }

        opened = true;
        RewardOptionScreen screen = new RewardOptionScreen();
        screen.previousScreen(parent);
        minecraft.setScreen(screen);
        LOGGER.info("Sakura UI smoke opened target: reward");
    }
}
