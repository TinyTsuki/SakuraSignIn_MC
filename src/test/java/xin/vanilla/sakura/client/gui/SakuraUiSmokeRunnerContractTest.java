package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * 锁定仅开发环境可用的 UI 烟测入口，避免测试钩子进入正式运行路径。
 */
public class SakuraUiSmokeRunnerContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void uiScreensCanBeOpenedOnlyByExplicitDevelopmentSmoke() {
        String runner = read(MAIN.resolve("internal/client/dev/SakuraUiSmokeRunner.java"));
        String events = read(MAIN.resolve("event/ClientEventHandler.java"));

        assertTrue(runner.contains("SAKURA_UI_SMOKE"));
        assertTrue(runner.contains("BaniraEnvironment.isProduction()"));
        assertTrue(runner.contains("\"reward\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("\"reward-extension\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("\"sign-in\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("\"theme-catalog\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("Sakura theme smoke PASS"));
        assertTrue(runner.contains("\"language\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("Sakura language smoke PASS"));
        assertTrue(runner.contains("SakuraLang.get().getI18nFiles()"));
        assertTrue(runner.contains("SakuraLang.getClientLanguage()"));
        assertTrue(runner.contains("\"quick-action\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("\"input-form\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("\"personal-date\".equalsIgnoreCase(target)"));
        assertTrue(runner.contains("(inputForm || rewardExtension || personalDate)"));
        assertTrue(runner.contains("&& parent instanceof MainMenuScreen"));
        assertTrue(runner.contains("inWorldWithoutScreen"));
        assertTrue(runner.contains("new RewardOptionScreen()"));
        assertTrue(runner.contains("seedRewardExtensionSmokeData()"));
        assertTrue(runner.contains("new ItemStack(Items.APPLE, 5)"));
        assertTrue(runner.contains("SakuraRewardTypes.EFFECT"));
        assertTrue(runner.contains("SakuraRewardTypes.EXPERIENCE_POINT"));
        assertTrue(runner.contains("SakuraRewardTypes.EXPERIENCE_LEVEL"));
        assertTrue(runner.contains("SakuraRewardTypes.SIGN_IN_CARD"));
        assertTrue(runner.contains("SakuraRewardTypes.ADVANCEMENT"));
        assertTrue(runner.contains("SakuraRewardTypes.MESSAGE"));
        assertTrue(runner.contains("SakuraRewardTypes.COMMAND"));
        assertTrue(runner.contains("RewardTypeId.of(\"example\", \"coin\")"));
        assertTrue(runner.contains("invalidItemPayload"));
        assertTrue(runner.contains("Sakura reward extension fixture PASS"));
        assertTrue(runner.contains("Sakura reward extension UI smoke PASS"));
        assertTrue(runner.contains("minecraft.stop()"));
        assertTrue(runner.contains("new SignInScreen()"));
        assertTrue(runner.contains("new InventoryScreen(minecraft.player)"));
        assertTrue(runner.contains("new StringInputScreen("));
        assertTrue(runner.contains("new PersonalDateConfigScreen(parent)"));
        assertTrue(runner.contains("Sakura personal date UI smoke PASS"));
        assertTrue(runner.contains("Sakura UI smoke opened target: {}"));
        assertTrue(events.contains("SakuraUiSmokeRunner.tick()"));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
