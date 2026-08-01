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
        String quickActions = read(MAIN.resolve("client/gui/SakuraQuickActions.java"));

        assertTrue(screen.contains("extends BaniraScreen"));
        assertTrue(screen.contains("Map<Integer, RewardOperationWidget>"));
        assertTrue(screen.contains("protected void onRender("));
        assertTrue(screen.contains("renderWidgets("));
        assertTrue(screen.contains("popupOption.onSelect(this::handlePopupSelection)"));
        assertTrue(screen.contains("new ConfirmDialogScreen("));
        assertTrue(screen.contains("requestDeleteConfirmation()"));
        assertTrue(screen.contains("!popupOption.isEmpty() || draggingRewardId != null"));
        assertTrue(screen.contains("inputState.isCtrlPressed()"));
        assertTrue(screen.contains("inputState.isShiftPressed()"));
        assertTrue(screen.contains("new ReadOnlyTextScreen("));
        assertTrue(screen.contains("deleteSelectedRewards()"));
        assertTrue(screen.contains("collapsedRewardGroups"));
        assertTrue(screen.contains("toggleRewardGroup("));
        assertTrue(screen.contains("isRewardGroupCollapsed(key)"));
        assertTrue(screen.contains("RewardGroupLayout"));
        assertTrue(screen.contains("inputState.isCtrlPressed()"));
        assertTrue(screen.contains("moveSelectedRewardsTo("));
        assertTrue(screen.contains("renderDraggedReward("));
        assertTrue(screen.contains("for (String rewardId : selectedRewardIds())"));
        assertFalse(screen.contains("getEffectiveTheme().bgSurface()"));
        assertTrue(screen.contains("groupSelectionColor()"));
        assertTrue(screen.contains("drawRewardGroupBorder("));
        assertTrue(screen.contains("RewardConfigManager.clearKey(rule, key)"));
        assertTrue(screen.contains("rewardContentHeight"));
        assertTrue(screen.contains("ShapeDrawArgs.ShapeType.RECT"));
        assertTrue(screen.contains(".setHoverTint(0)"));
        assertTrue(operationWidget.contains("extends BaseWidget"));
        assertTrue(operationWidget.contains("TooltipWidget.drawPopupMessage"));
        assertTrue(rewardWidget.contains("MouseDragEvent"));
        assertTrue(rewardWidget.contains("onLongPress(MouseEvent event)"));
        assertTrue(rewardWidget.contains("longPressDragHandler"));
        assertTrue(quickActions.contains(".previousScreen(context.currentScreen())"));

        assertFalse(screen.contains("KeyEventManager"));
        assertFalse(screen.contains("MouseCursor"));
        assertFalse(screen.contains("Map<Integer, OperationButton>"));
        assertFalse(screen.contains("new OperationButton("));
        assertFalse(screen.contains("OperationButton.RenderContext"));
        assertFalse(screen.contains("screen.component.PopupOption"));
        assertFalse(screen.contains("isKeyAndMousePressed"));
        assertFalse(screen.contains("void mouseClicked("));
        assertFalse(screen.contains("void mouseReleased("));
        assertFalse(screen.contains("void mouseMoved("));
        assertFalse(clientEvents.contains("new RewardOptionScreen().setPreviousScreen("));
        assertFalse(clientEvents.contains("GuiScreenEvent"));
    }

    @Test
    public void obsoleteRewardEditorCursorIsRemoved() {
        assertFalse(Files.exists(MAIN.resolve("screen/component/MouseCursor.java")));
    }

    @Test
    public void itemCountIsRenderedAfterTheItemModel() {
        String gui = read(MAIN.resolve("client/gui/RewardRenderer.java"));

        assertTrue(gui.contains("ItemWidget.renderItem(itemRenderer, font,"));
        assertTrue(gui.contains("RewardManager.deserializeReward(reward), x, y, showText)"));
        assertFalse(gui.contains("fontRenderer.drawShadow(matrixStack, count"));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
