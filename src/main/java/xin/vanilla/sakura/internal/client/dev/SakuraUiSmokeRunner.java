package xin.vanilla.sakura.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLEnvironment;
import xin.vanilla.sakura.screen.RewardOptionScreen;

/**
 * 仅在开发环境按显式目标打开界面，供有界 runClient 烟测使用。
 */
public final class SakuraUiSmokeRunner {
    public static final String ENVIRONMENT_KEY = "SAKURA_UI_SMOKE";

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
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        opened = true;
        minecraft.setScreen(new RewardOptionScreen());
    }
}
