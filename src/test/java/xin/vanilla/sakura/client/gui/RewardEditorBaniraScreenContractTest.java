package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定奖励编辑器使用 Banira 统一界面、输入和弹出层生命周期。
 */
public class RewardEditorBaniraScreenContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void screenUsesBaniraLifecycleAndWidgets() {
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));
        String operationWidget = read(MAIN.resolve("client/gui/RewardOperationWidget.java"));
        String rewardWidget = read(MAIN.resolve("client/gui/RewardListEntryWidget.java"));
        String clientEvents = read(MAIN.resolve("event/ClientEventHandler.java"));

        assertTrue(screen.contains("extends BaniraScreen"));
        assertTrue(screen.contains("Map<Integer, RewardOperationWidget>"));
        assertTrue(screen.contains("protected void onRender("));
        assertTrue(screen.contains("renderWidgets("));
        assertTrue(screen.contains("popupOption.onSelect(this::handlePopupSelection)"));
        assertTrue(screen.contains("if (!popupOption.isEmpty())"));
        assertTrue(screen.contains("inputState.onlyShiftPressed()"));
        assertTrue(operationWidget.contains("extends BaseWidget"));
        assertTrue(operationWidget.contains("TooltipWidget.drawPopupMessage"));
        assertTrue(rewardWidget.contains("MouseDragEvent"));
        assertTrue(clientEvents.contains(".previousScreen(event.getGui())"));

        assertFalse(screen.contains("KeyEventManager"));
        assertFalse(screen.contains("MouseCursor"));
        assertFalse(screen.contains("Map<Integer, OperationButton>"));
        assertFalse(screen.contains("new OperationButton("));
        assertFalse(screen.contains("OperationButton.RenderContext"));
        assertFalse(screen.contains("screen.component.PopupOption"));
        assertFalse(screen.contains("void mouseClicked("));
        assertFalse(screen.contains("void mouseReleased("));
        assertFalse(screen.contains("void mouseMoved("));
        assertFalse(clientEvents.contains("new RewardOptionScreen().setPreviousScreen("));
    }

    @Test
    public void obsoleteRewardEditorCursorIsRemoved() {
        assertFalse(Files.exists(MAIN.resolve("screen/component/MouseCursor.java")));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
