package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定输入表单与背包快捷入口的 Banira 接入边界。
 */
public class InputAndQuickActionBaniraContractTest {
    private static final Path MAIN = Paths.get("src/main/java");

    @Test
    public void stringInputDelegatesLayoutAndInteractionToBanira() throws Exception {
        String input = source("xin/vanilla/sakura/screen/StringInputScreen.java");
        String guiUtils = source("xin/vanilla/sakura/util/AbstractGuiUtils.java");

        assertTrue(input.contains("extends InputFormScreen"));
        assertTrue(input.contains("new InputFormScreen.Widget()"));
        assertFalse(input.contains("TextFieldWidget"));
        assertFalse(input.contains("extends Screen"));
        assertFalse(guiUtils.contains("newTextFieldWidget("));
        assertFalse(guiUtils.contains("newButton("));
    }

    @Test
    public void inventoryEntriesUseBaniraQuickActions() throws Exception {
        String actions = source("xin/vanilla/sakura/client/gui/SakuraQuickActions.java");
        String events = source("xin/vanilla/sakura/event/ClientEventHandler.java");
        String config = source("xin/vanilla/sakura/config/ClientConfig.java");

        assertTrue(actions.contains("QuickActionRegistry.get()"));
        assertTrue(actions.contains("QuickIcon.resource(texture)"));
        assertTrue(events.contains("SakuraQuickActions.register()"));
        assertFalse(events.contains("GuiScreenEvent"));
        assertFalse(config.contains("inventorySignInButtonCoordinate"));
        assertFalse(config.contains("inventoryRewardOptionButtonCoordinate"));
        assertFalse(Files.exists(MAIN.resolve(
                "xin/vanilla/sakura/screen/component/InventoryButton.java")));
    }

    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(MAIN.resolve(relative)), StandardCharsets.UTF_8);
    }
}
