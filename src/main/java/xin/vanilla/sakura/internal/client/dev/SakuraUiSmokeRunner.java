package xin.vanilla.sakura.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.theme.BuiltInThemeCatalog;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.StringList;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.screen.RewardOptionScreen;
import xin.vanilla.sakura.screen.SignInScreen;
import xin.vanilla.sakura.screen.StringInputScreen;

/**
 * 仅在开发环境按显式目标打开界面，供有界 runClient 烟测使用。
 */
public final class SakuraUiSmokeRunner {
    public static final String ENVIRONMENT_KEY = "SAKURA_UI_SMOKE";
    private static final Logger LOGGER = LogManager.getLogger();

    private static boolean opened;
    private static int themeIndex;
    private static int themeTicks;
    private static String previousThemeId;
    private static boolean previousSpecialVariant;
    private static boolean themeSmokeFinished;

    private SakuraUiSmokeRunner() {
    }

    public static void tick() {
        if (FMLEnvironment.production) {
            return;
        }

        String target = System.getenv(ENVIRONMENT_KEY);
        if ("theme-catalog".equalsIgnoreCase(target)) {
            tickThemeCatalog();
            return;
        }
        if (opened) {
            return;
        }
        boolean reward = "reward".equalsIgnoreCase(target);
        boolean signIn = "sign-in".equalsIgnoreCase(target);
        boolean quickAction = "quick-action".equalsIgnoreCase(target);
        boolean inputForm = "input-form".equalsIgnoreCase(target);
        if (!reward && !signIn && !quickAction && !inputForm) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        boolean inWorldWithoutScreen = !inputForm && minecraft.player != null
                && minecraft.level != null
                && parent == null;
        boolean atMainMenu = (reward || inputForm) && parent instanceof MainMenuScreen;
        if (!inWorldWithoutScreen && !atMainMenu) {
            return;
        }

        opened = true;
        Screen screen = quickAction
                ? new InventoryScreen(minecraft.player)
                : signIn ? new SignInScreen()
                : inputForm ? inputForm(parent)
                : new RewardOptionScreen();
        if (screen instanceof SignInScreen) {
            ((SignInScreen) screen).previousScreen(parent);
        } else if (screen instanceof RewardOptionScreen) {
            ((RewardOptionScreen) screen).previousScreen(parent);
        }
        minecraft.setScreen(screen);
        LOGGER.info("Sakura UI smoke opened target: {}",
                quickAction ? "quick-action" : signIn ? "sign-in" : inputForm ? "input-form" : "reward");
    }

    /**
     * 在真实客户端中依次重建五套主题界面，最后恢复进入烟测前的配置。
     */
    private static void tickThemeCatalog() {
        if (themeSmokeFinished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!opened) {
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
                return;
            }
            opened = true;
            previousThemeId = SakuraSignIn.getActiveThemeId();
            previousSpecialVariant = SakuraSignIn.isSpecialThemeVariant();
            themeIndex = 0;
            applyTheme(minecraft, BuiltInThemeCatalog.themeIds().get(themeIndex));
            return;
        }

        if (++themeTicks < 30) {
            return;
        }
        themeTicks = 0;
        themeIndex++;
        if (themeIndex < BuiltInThemeCatalog.themeIds().size()) {
            applyTheme(minecraft, BuiltInThemeCatalog.themeIds().get(themeIndex));
            return;
        }

        ClientConfig.get().display()
                .themeId(previousThemeId)
                .specialVariant(previousSpecialVariant);
        ClientConfig.save();
        ClientEventHandler.loadThemeTexture();
        minecraft.setScreen(new SignInScreen());
        themeSmokeFinished = true;
        LOGGER.info("Sakura theme smoke PASS: {}/{}",
                BuiltInThemeCatalog.themeIds().size(),
                BuiltInThemeCatalog.themeIds().size());
    }

    private static void applyTheme(Minecraft minecraft, String themeId) {
        ClientConfig.get().display().themeId(themeId).specialVariant(false);
        ClientEventHandler.loadThemeTexture();
        minecraft.setScreen(new SignInScreen());
        LOGGER.info("Sakura theme smoke loaded: id={}, texture={}",
                SakuraSignIn.getActiveThemeId(), SakuraSignIn.getThemeTexture());
    }

    private static Screen inputForm(Screen parent) {
        return new StringInputScreen(
                parent,
                Text.literal("Input form"),
                Text.literal("Enter test content"),
                ".*",
                "Sakura",
                values -> {
                    return new StringList();
                }
        );
    }
}
