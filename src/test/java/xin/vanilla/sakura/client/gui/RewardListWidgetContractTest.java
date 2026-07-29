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
 * 锁定奖励列表逐步迁移到 Banira Widget 的边界。
 */
public class RewardListWidgetContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void rewardEntriesUseBaniraWidgetRenderingAndEvents() {
        String widget = read(MAIN.resolve("client/gui/RewardListEntryWidget.java"));
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));

        assertTrue(widget.contains("extends BaseWidget"));
        assertTrue(widget.contains("BaseShapeWidget.drawShape"));
        assertTrue(widget.contains("TooltipWidget.drawPopupMessage"));
        assertTrue(screen.contains("Map<String, RewardListEntryWidget>"));
        assertTrue(screen.contains(".handleMouseClick("));
        assertTrue(screen.contains(".handleMouseRelease("));
        assertTrue(screen.contains(".updateMouseHover("));
        assertTrue(screen.contains("getTextColorCanRepair()"));
        assertFalse(screen.contains("operationButton.renderPopup(matrixStack, keyManager)"));
    }

    @Test
    public void itemRewardsUseBaniraItemRenderer() {
        String guiUtils = read(MAIN.resolve("util/AbstractGuiUtils.java"));
        assertTrue(guiUtils.contains("ItemWidget.renderItem("));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
