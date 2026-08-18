package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SignInScreenBaniraContractTest {

    @Test
    public void signInScreenUsesBaniraLifecycleAndWidgets() throws Exception {
        String screen = read("src/main/java/xin/vanilla/sakura/screen/SignInScreen.java");
        String cell = read("src/main/java/xin/vanilla/sakura/screen/SignInCell.java");

        assertTrue(screen.contains("extends BaniraScreen"));
        assertTrue(screen.contains("Map<Integer, RewardOperationWidget>"));
        assertTrue(screen.contains("renderWidgets(graphics, partialTicks)"));
        assertTrue(screen.contains("addDeferredTooltipRender"));
        assertTrue(screen.contains("if (SakuraClientState.getCalendarCurrentDate() == null)"));
        assertTrue(screen.contains("public void refreshPlayerData()"));
        assertTrue(screen.contains("TooltipWidget.drawPopupMessage"));
        assertTrue(cell.contains("extends BaseWidget"));
        assertTrue(cell.contains("protected boolean onMouseScroll(MouseScrollEvent event)"));
    }

    @Test
    public void synchronizedPlayerDataRefreshesTheOpenCalendar() throws Exception {
        String proxy = read("src/main/java/xin/vanilla/sakura/network/ClientProxy.java");

        assertTrue(proxy.contains("refreshOpenSignInScreen()"));
        assertTrue(proxy.contains("((SignInScreen) Minecraft.getInstance().screen).refreshPlayerData()"));
    }

    @Test
    public void legacyInputAndPopupComponentsAreRemoved() {
        assertFalse(exists("src/main/java/xin/vanilla/sakura/screen/component/OperationButton.java"));
        assertFalse(exists("src/main/java/xin/vanilla/sakura/screen/component/PopupOption.java"));
        assertFalse(exists("src/main/java/xin/vanilla/sakura/screen/component/KeyEventManager.java"));
    }

    @Test
    public void signInUiDoesNotUseLegacyRoundedFill() throws Exception {
        String screen = read("src/main/java/xin/vanilla/sakura/screen/SignInScreen.java");
        assertTrue(screen.contains("new ShapeDrawArgs.RectParams()"));
        assertFalse(screen.contains("AbstractGuiUtils.fill(stack, 4, 4"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static boolean exists(String path) {
        Path source = Paths.get(path);
        return Files.exists(source);
    }
}
