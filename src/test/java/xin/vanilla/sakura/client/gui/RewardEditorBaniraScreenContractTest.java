package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定奖励编辑器使用 Banira 统一界面、输入和弹出层生命周期。
 */
public class RewardEditorBaniraScreenContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");
    private static final Path RESOURCES = Paths.get(
            "src/main/resources/assets/sakura_sign_in/lang");

    @Test
    public void screenUsesBaniraLifecycleAndWidgets() {
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));
        String operationWidget = read(MAIN.resolve("client/gui/RewardOperationWidget.java"));
        String rewardWidget = read(MAIN.resolve("client/gui/RewardListEntryWidget.java"));
        String clientEvents = read(MAIN.resolve("event/ClientEventHandler.java"));
        String quickActions = read(MAIN.resolve("client/gui/SakuraQuickActions.java"));
        String zhCn = read(RESOURCES.resolve("zh_cn.json"));

        assertTrue(screen.contains("extends BaniraScreen"));
        assertTrue(screen.contains("Map<Integer, RewardOperationWidget>"));
        assertTrue(screen.contains("protected void onRender("));
        assertTrue(screen.contains("renderWidgets("));
        assertTrue(screen.contains("popupOption.onSelect(this::handlePopupSelection)"));
        assertTrue(screen.contains("new ConfirmDialogScreen("));
        assertTrue(screen.contains("requestDeleteConfirmation()"));
        assertTrue(screen.contains("requestConfirmation(\"confirm_clear_reward_rule\""));
        assertTrue(screen.contains("return editHandler.handleDelete();"));
        assertTrue(screen.contains("!popupOption.isEmpty() || draggingRewardId != null"));
        assertTrue(screen.contains("inputState.isCtrlPressing()"));
        assertTrue(screen.contains("inputState.isShiftPressing()"));
        assertTrue(screen.contains("new ReadOnlyTextScreen("));
        assertTrue(screen.contains("deleteSelectedRewards()"));
        assertTrue(screen.contains("collapsedRewardGroups"));
        assertTrue(screen.contains("toggleRewardGroup("));
        assertTrue(screen.contains("isRewardGroupCollapsed(key)"));
        assertTrue(screen.contains("RewardGroupLayout"));
        assertTrue(screen.contains("inputState.isCtrlPressing()"));
        assertTrue(screen.contains("moveSelectedRewardsTo("));
        assertTrue(screen.contains("event.clickCount() == 3"));
        assertTrue(screen.contains("selectSemanticRewards("));
        assertTrue(screen.contains("RewardSemanticMatcher.matches("));
        assertTrue(screen.contains("rewardSelection.cycleMiddleReward("));
        assertTrue(screen.contains("toggleRewardGroupSelection("));
        assertTrue(screen.contains("showRewardGroupPopup("));
        assertTrue(screen.contains("findRewardGroupAt(event.mouseX(), event.mouseY())"));
        assertTrue(screen.contains("!rewardSelection.isSelected(rewardGroupTitleId(groupKey))"));
        assertTrue(screen.contains("currRewardButton = rewardGroupTitleId(groupKey)"));
        assertTrue(screen.contains("this.currRewardButton = key"));
        assertTrue(screen.contains("RewardSelectionIds.rewardIds(rewardMap)"));
        assertTrue(screen.contains("RewardSelectionIds.selectionIds(rewardMap, \"\u6807\u9898,\")"));
        assertTrue(screen.contains("pasteToSelectedGroups("));
        assertTrue(screen.contains("RewardSelectionIds.groupKeys("));
        assertTrue(screen.contains("private boolean updateRule()"));
        assertTrue(screen.contains("public boolean handleUndo() {\n            if (updateRule()) return false;"));
        assertTrue(screen.contains("public boolean handleRedo() {\n            if (updateRule()) return false;"));
        assertTrue(screen.contains("editHandler.handlePaste();"));
        assertTrue(screen.contains("renderDraggedReward("));
        assertTrue(screen.contains("for (String rewardId : selectedRewardIds())"));
        assertFalse(screen.contains("getEffectiveTheme().bgSurface()"));
        assertTrue(screen.contains("groupSelectionColor()"));
        assertTrue(screen.contains("groupBorderColor()"));
        assertTrue(screen.contains("getEffectiveTheme().buttonBorderHover()"));
        assertTrue(screen.contains("drawRewardGroupBorder("));
        assertTrue(screen.contains("groupHeaderHeight()"));
        assertTrue(screen.contains("mergeCurrentRuleRewards()"));
        assertTrue(screen.contains("RewardManager.mergeRewards(entry.getValue())"));
        assertTrue(screen.contains("createDrawnIcon(OperationButtonType.MERGE)"));
        int iconStart = screen.indexOf("private RewardOperationWidget createDrawnIcon(");
        int iconEnd = screen.indexOf("private RewardOperationWidget createThemeIcon(", iconStart);
        assertTrue(iconStart >= 0 && iconEnd > iconStart);
        String iconRenderer = screen.substring(iconStart, iconEnd);
        assertTrue(iconRenderer.contains("drawOperationIcon("));
        assertTrue(iconRenderer.contains("drawTransferArrow("));
        assertTrue(iconRenderer.contains("drawLineWithSquareCaps("));
        assertTrue(iconRenderer.contains("float terminalY = top + 10"));
        assertTrue(iconRenderer.contains("accentFocused()"));
        assertFalse(iconRenderer.contains("buttonBg"));
        assertFalse(iconRenderer.contains("buttonText()"));
        assertFalse(screen.contains("getHelpUV()"));
        assertTrue(screen.contains("font.lineHeight + groupHeaderVerticalPadding * 2"));
        assertTrue(screen.contains("drawWelcomeTips(matrixStack)"));
        assertFalse(screen.contains("drawLimitedText(matrixStack, tips.content()"));
        assertTrue(screen.contains("requestGroupConfirmation("));
        assertTrue(screen.contains("rewardGroupDisplayName("));
        assertTrue(screen.contains("requestGroupConfirmation(\"confirm_clear_reward_group\""));
        assertTrue(screen.contains("requestGroupConfirmation(\"confirm_delete_reward_group\""));
        assertTrue(zhCn.contains("\"format.sakura_sign_in.confirm_clear_reward_group\""));
        assertTrue(zhCn.contains("\"format.sakura_sign_in.confirm_delete_reward_group\""));
        assertFalse(screen.contains("confirm_delete_reward\""));
        assertTrue(screen.contains("RewardConfigManager.clearKey(rule, key)"));
        assertTrue(screen.contains("rewardContentHeight"));
        assertTrue(screen.contains("updateRewardDrag(inputState.mouseX(), inputState.mouseY())"));
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

        assertTrue(gui.contains("ItemWidget.renderItem(font,"));
        assertTrue(gui.contains("SakuraRewardClient.find(reward.getTypeId())"));
        assertFalse(gui.contains("fontRenderer.drawShadow(matrixStack, count"));
    }

    @Test
    public void rewardTooltipsStayFocusedAndEditorHelpOwnsInteractionGuidance() {
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));
        String zhCn = read(RESOURCES.resolve("zh_cn.json"));
        String enUs = read(RESOURCES.resolve("en_us.json"));

        assertTrue(screen.contains("rewardItemTooltip("));
        int methodStart = screen.indexOf("private Text rewardItemTooltip(");
        int methodEnd = screen.indexOf("private boolean isRewardGroupCollapsed", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart);
        assertFalse(screen.substring(methodStart, methodEnd).contains("append("));
        assertFalse(screen.contains("reward_item_selection_hint"));
        assertTrue(zhCn.contains("\"word.sakura_sign_in.reward_group_header_hint\": \"\u5de6\u952e\u6298\u53e0\u6216\u5c55\u5f00\\n"));
        assertTrue(enUs.contains("\"word.sakura_sign_in.reward_group_header_hint\": \"Left-click to collapse or expand\\n"));
        assertTrue(zhCn.contains("\u65b9\u5411\u952e\u6216 WASD"));
        assertTrue(enUs.contains("arrow keys or WASD"));
        assertFalse(zhCn.contains("Ctrl+\u5de6\u952e\u9009\u62e9\u5956\u52b1\u7ec4"));
        assertFalse(enUs.contains("Ctrl+left-click to select"));
    }

    @Test
    public void keyboardNavigationPasteAndProbabilityEditingHaveExplicitRoutes() {
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));
        String flow = read(MAIN.resolve("client/gui/RewardProbabilityFlow.java"));
        String coordinator = read(MAIN.resolve("client/reward/RewardEditorCoordinator.java"));

        assertTrue(screen.contains("RewardKeyboardNavigator.findNext("));
        assertTrue(screen.contains("pasteWithoutSelection("));
        assertTrue(screen.contains("RewardEditorCoordinator.openProbability("));
        assertTrue(coordinator.contains("RewardProbabilityFlow.create("));
        assertTrue(flow.contains("public final class RewardProbabilityFlow"));
        assertFalse(screen.contains(".shadow(true)"));
    }

    @Test
    public void f5RefreshesTheRewardEditorAndRestoresTheYOffset() {
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));
        int methodStart = screen.indexOf("private void refreshRewardScreen()");
        int methodEnd = screen.indexOf("private boolean moveRewardSelection", methodStart);

        assertTrue(screen.contains("eventArgs.keyCode() == GLFWKey.GLFW_KEY_F5"));
        assertTrue(methodStart >= 0 && methodEnd > methodStart);
        String method = screen.substring(methodStart, methodEnd);
        assertTrue(method.contains("yOffsetResetTime = 0"));
        assertTrue(method.contains("yOffsetOld = 0"));
        assertTrue(method.contains("setYOffset(0)"));
        assertTrue(method.contains("updateLayout()"));
    }

    @Test
    public void multiGroupPasteUsesOneHistoryAndRefreshTransaction() {
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));
        int start = screen.indexOf("private boolean pasteToSelectedGroups(");
        int end = screen.indexOf("public boolean handleDelete()", start);
        assertTrue(start >= 0 && end > start);
        String method = screen.substring(start, end);

        assertEquals(1, count(method, "RewardConfigManager.addUndoRewardOption(rule)"));
        assertEquals(1, count(method, "RewardConfigManager.clearRedoList()"));
        assertEquals(1, count(method, "RewardConfigManager.saveRewardOption()"));
        assertEquals(1, count(method, "updateLayout()"));
    }

    private static int count(String source, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
