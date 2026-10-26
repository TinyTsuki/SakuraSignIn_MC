package xin.vanilla.sakura.client.ui;

import org.junit.*;
import xin.vanilla.banira.api.client.ui.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.Assert.*;

public class ApprovedRewardEditorTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @BeforeClass public static void bootstrap() { SignInUiParityTest.bootstrap(); }
    @Test public void headerAndSearchUseSharedPresentationWithoutRawTranslationKeys() {
        var port = new RewardEditorUiParityTest.Port(2); var view = new RecordingUiView();
        var session = new RewardEditorUiSession(port, () -> true); session.bind(view);
        assertNotNull("Approved shared header requires the real module icon", view.icons.get("brand"));
        assertNotNull(view.texts.get("search_hint"));
        assertFalse(view.texts.get("search_hint").toString().contains("word.banira_codex."));
        assertNotNull(view.actions.get("clear_search"));
        view.drafts.get("search").accept("Reward 1"); view.actions.get("clear_search").run();
        assertEquals("", session.query()); assertEquals(0, port.commands);
    }
    @Test public void typedCheckboxAndBoundedLayoutDoNotMutateUntilExplicitInput() {
        var port = new RewardEditorUiParityTest.Port(3); var view = new RecordingUiView();
        new RewardEditorUiSession(port, () -> true).bind(view);
        assertTrue("Reward rows need their own stable geometry", view.rows instanceof UiRowLayout);
        assertEquals(28, ((UiRowLayout) view.rows).rowHeight());
        var row = new RecordingUiView(); view.rows.bind(row, 0);
        assertFalse("Checkboxes use the existing typed-value path", row.controls.containsKey("input"));
        assertEquals(UiValue.Kind.BOOLEAN, row.values.get("input").kind());
        assertFalse(row.values.get("input").asBoolean()); assertEquals(0, port.entries);
        row.changes.get("input").accept(UiValue.bool(true));
        assertEquals(1, port.entries); assertEquals("TOGGLE", port.lastEntry.name());
    }
    @Test public void revokedRuleClearsRetainedInspectorContent() {
        var port = new RewardEditorUiParityTest.Port(0);
        port.rows.add(new RewardEditorUiSession.Entry("base,0", "Private reward", "Private type", "Private description", null, false, false, true, true));
        var session = new RewardEditorUiSession(port, () -> true); var view = new RecordingUiView(); session.bind(view);
        assertNotNull("Selected rewards need the approved inspector", view.texts.get("detail_title"));
        assertTrue(view.texts.get("detail_title").toString().contains("Private reward"));
        port.redacted = true; port.revision++; session.tick();
        assertFalse(view.texts.get("detail_title").toString().contains("Private"));
        assertFalse(view.texts.get("detail_description").toString().contains("Private"));
        assertEquals(0, view.rows.size());
    }
    @Test public void approvedTemplateKeepsAllExistingActionsAndNoBrowserScript() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/rewards.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("rewards.css")); assertTrue(html.contains("id=\"inspector\""));
        assertTrue(html.contains("id=\"more_panel\"")); assertTrue(html.contains("id=\"status\""));
        for (var action : RewardEditorUiSession.Action.values()) assertTrue(action.id, html.contains("id=\"" + action.id + "\""));
        assertFalse(html.contains("<script"));
    }
    @Test public void nativeViewDoesNotBindNewControlsOrDispatchActions() {
        var port = new RewardEditorUiParityTest.Port(2); var view = new RecordingUiView(); view.nativeView = true;
        new RewardEditorUiSession(port, () -> true).bind(view);
        assertTrue(view.actions.isEmpty()); assertTrue(view.controls.isEmpty()); assertEquals(0, port.commands);
    }
    @Test public void probabilityUsesTextToolsRatherThanTheReservedResetPreset() {
        var view = new RecordingUiView();
        new RewardEditorUiSession(new RewardEditorUiParityTest.Port(2), () -> true).bind(view);
        assertTrue("UiDomView otherwise replaces reset text with a RESET preset", ((UiRowLayout) view.rows).textTools());
    }
    @Test public void overlaysSuppressAndRestoreNativeGameElementsWithoutDroppingCallbacks() {
        var port = new RewardEditorUiParityTest.Port(0);
        var icon = UiGameElement.preset(xin.vanilla.banira.client.gui.widget.ButtonWidget.PresetStyle.SETTINGS);
        port.rows.add(new RewardEditorUiSession.Entry("base,0", "Reward", "Item", "Full", icon, false, false, true, true));
        var session = new RewardEditorUiSession(port, () -> true); var view = new RecordingUiView(); session.bind(view);
        var row = new RecordingUiView(); view.rows.bind(row, 0);
        assertNotNull(row.icons.get("icon")); assertNull("Hidden inspector must not paint its native icon", view.icons.get("detail_icon"));
        view.actions.get("more").run(); assertNull(row.icons.get("icon")); assertNull(row.icons.get("apply"));
        assertNull("The bounded menu also covers the native toolbar", view.icons.get("copy_icon"));
        assertNull(view.icons.get("details_icon"));
        view.actions.get("more").run(); assertNotNull(row.icons.get("icon")); assertNotNull(row.icons.get("apply"));
        view.actions.get("details").run(); assertNull(row.icons.get("icon")); assertNotNull(view.icons.get("detail_icon"));
        view.actions.get("close_details").run(); assertNotNull(row.icons.get("icon"));
        assertEquals(0, port.entries); assertEquals(0, port.commands);
        session.detach(UiDetachReason.RELOAD); assertTrue(row.actions.isEmpty());
    }
    @Test public void initialRuleChooserDoesNotReferenceANonexistentDescription() {
        var port = new RewardEditorUiParityTest.Port(0); port.rule = 0;
        var view = new RecordingUiView(); new RewardEditorUiSession(port, () -> true).bind(view);
        assertFalse(view.texts.get("detail_description").toString().contains("reward_rule_description_0"));
    }
    @Test public void detailsAndMoreAreExclusiveAndEscapeDismissesBeforeBusinessKeys() {
        var port = new RewardEditorUiParityTest.Port(1); var view = new RecordingUiView();
        var session = new RewardEditorUiSession(port, () -> true); session.bind(view);
        view.actions.get("more").run(); view.actions.get("details").run();
        assertTrue(view.enabled.get("inspector")); assertFalse(view.enabled.get("more_panel"));
        view.actions.get("add").run(); assertEquals("Occluded toolbar cannot run", 0, port.commands);
        assertTrue(session.key(256, 0, 0, true)); assertFalse(view.enabled.get("inspector"));
        assertEquals(0, port.commands); assertEquals(0, view.closes);
    }
    @Test public void moreMenuFitsTheSmallestAcceptedWorkspace() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/rewards.css"), StandardCharsets.UTF_8);
        var panel = java.util.regex.Pattern.compile("#more_panel\\{([^}]+)}").matcher(css);
        assertTrue(panel.find());
        assertTrue("Menu must open inside the viewport rather than below its toolbar", panel.group(1).contains("top:0;"));
        assertTrue(panel.group(1).contains("max-height:100%"));
        assertTrue(panel.group(1).contains("overflow-y:auto"));
        var buttons = java.util.regex.Pattern.compile("#more_panel button\\{([^}]+)}").matcher(css);
        assertTrue(buttons.find()); assertTrue(buttons.group(1).contains("height:20px"));
        assertTrue(buttons.group(1).contains("margin:0"));
        assertTrue("Six commands and frame must fit the real 146px viewport", 6 * 20 + 10 <= 146);
    }
    @Test public void protectedGroupToolsDoNotLeaveEmptyButtonRectangles() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/rewards.css"), StandardCharsets.UTF_8);
        assertTrue(css.contains(".reward-entry .tool:disabled{visibility:hidden}"));
    }
    @Test public void outsidePointerClosesMoreWithoutDispatchingABusinessCommand() {
        var port = new RewardEditorUiParityTest.Port(1); var view = new RecordingUiView();
        view.geometry.put("more_panel", new xin.vanilla.banira.client.data.ScreenCoordinate(200, 0, 130, 130));
        new RewardEditorUiSession(port, () -> true).bind(view);
        view.actions.get("more").run(); assertTrue(view.enabled.get("more_panel"));
        assertNotNull("Popup needs an outside-pointer dismissal", view.pointers.get("workspace"));
        view.pointers.get("workspace").accept(new UiPointer(UiPointer.Kind.DOWN, 210, 10, 0));
        assertTrue(view.enabled.get("more_panel"));
        view.pointers.get("workspace").accept(new UiPointer(UiPointer.Kind.DOWN, 20, 200, 0));
        assertFalse(view.enabled.get("more_panel")); assertEquals(0, port.commands);
    }
    @Test public void rewardTextUsesTheAuiFontRasterizerAtTheCurrentPixelScale() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/rewards.css"), StandardCharsets.UTF_8);
        assertTrue("Unset font families use Minecraft bitmap text at fractional sizes", css.contains(".rewards-page{font-family:sans-serif}"));
    }
    @Test public void fontRepairPreservesTheApprovedLayoutStylesExactly() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/rewards.css"), StandardCharsets.UTF_8);
        String layout = css.replace(".rewards-page{font-family:sans-serif}", "").trim();
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(layout.getBytes(StandardCharsets.UTF_8));
        assertEquals("C2C03C93919E39B474A7ABEF60DDA05788CFE812B4318AEDF23898DFFF808237", java.util.HexFormat.of().withUpperCase().formatHex(digest));
    }
    @Test public void rewardStylesStayWithinBudgetAfterWindowsCheckout() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/rewards.css"), StandardCharsets.UTF_8);
        String windows = css.replace("\r\n", "\n").replace("\n", "\r\n");
        assertTrue("CRLF checkouts retain the same 4KiB CSS budget", windows.getBytes(StandardCharsets.UTF_8).length <= 4096);
    }
}
