package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.data.lottery.LotteryQuote;
import xin.vanilla.sakura.reward.Reward;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.*;

public class LotteryUiPresentationTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    private static String text(RecordingUiView view, String id) {
        assertNotNull("Missing presentation field: " + id, view.texts.get(id));
        return view.texts.get(id).toString();
    }

    private static String translated(String key) {
        return SakuraComponent.get().transClient("word", key).toString();
    }

    @Test
    public void quoteFeeStateAndCapacityAreIndependentAndRefreshKeepsConfirmedFee() {
        var port = new LotteryUiOperationHandoffTest.Port();
        var view = new RecordingUiView();
        var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        assertEquals("\u2014", text(view, "fee"));
        assertEquals("\u2014", text(view, "available"));
        session.tick();
        assertEquals(translated("lottery_quote_pending"), text(view, "info"));
        assertEquals(Boolean.TRUE, view.enabled.get("info"));
        port.accept();
        session.tick();
        assertEquals("xpPoints(2)", text(view, "fee"));
        assertEquals("12", text(view, "available"));
        assertEquals("", text(view, "info"));
        assertEquals(Boolean.FALSE, view.enabled.get("info"));
        assertFalse(view.texts.containsKey("quote_status"));
        port.millis = 3000;
        session.tick();
        assertEquals("xpPoints(2)", text(view, "fee"));
        assertEquals("\u2014", text(view, "available"));
        assertFalse(view.enabled.get("batch"));
        assertEquals(Boolean.TRUE, view.enabled.get("info"));
    }

    @Test
    public void everyRejectedQuoteDisablesDrawsAndKeepsAnHonestFailureReason() {
        for (LotteryQuote.Status status : LotteryQuote.Status.values()) {
            if (status == LotteryQuote.Status.READY) continue;
            var port = new LotteryUiOperationHandoffTest.Port();
            var view = new RecordingUiView();
            var session = new LotteryUiSession(port, () -> true);
            session.bind(view);
            session.tick();
            var request = port.quotes.get(0);
            assertTrue(port.requests.accept(new LotteryQuote(request, status, 1, 0, "", "")));
            session.tick();
            if (port.payment.isPending()) {
                request = port.quotes.get(port.quotes.size() - 1);
                assertEquals(1, request.getCount());
                assertTrue(port.requests.accept(new LotteryQuote(request, status, 1, 0, "", "")));
                session.tick();
            }
            assertFalse(view.enabled.get("single"));
            assertFalse(view.enabled.get("batch"));
            assertEquals("\u2014", text(view, "available"));
            String key = switch (status) {
                case FORBIDDEN -> "lottery_permission_denied";
                case STALE_GENERATION -> "lottery_configuration_changed";
                case POOL_NOT_FOUND -> "lottery_pool_not_found";
                default -> "lottery_payment_unavailable";
            };
            assertEquals(translated(key), text(view, "info"));
            assertEquals(Boolean.TRUE, view.enabled.get("info"));
            view.actions.get("single").run();
            assertTrue(port.draws.isEmpty());
        }
    }

    @Test
    public void threeVirtualColumnsRetainOrderTailAndHiddenProbabilityBoundaries() {
        var port = new LotteryUiOperationHandoffTest.Port();
        for (int i = 1; i < 8; i++) port.daily.getRewards().add(Reward.getDefault());
        var view = new RecordingUiView();
        var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        assertEquals(Set.of("fields", "fields_middle", "fields_end"), view.providers.keySet());
        int[] sizes = {3, 3, 2};
        int column = 0;
        for (var rows : view.providers.values()) {
            assertEquals(sizes[column], rows.size());
            for (int row = 0; row < rows.size(); row++) assertEquals("daily:" + (row * 3 + column), rows.key(row));
            column++;
        }
        var stale = view.rows;
        session.detach(UiDetachReason.SWITCH);
        var late = new RecordingUiView();
        stale.bind(late, 0);
        assertEquals(0, late.writes);
        port.daily.setPreviewMode(LotteryPreviewMode.PROBABILITY_ONLY);
        view = new RecordingUiView();
        session.bind(view);
        var cell = new RecordingUiView();
        view.rows.bind(cell, 0);
        assertNull(cell.icons.get("icon"));
        assertFalse(text(cell, "label").contains("Reward"));
        assertFalse(cell.tooltips.get("label").toString().contains("Reward"));
        session.detach(UiDetachReason.SWITCH);
        port.daily.setPreviewMode(LotteryPreviewMode.ITEMS_ONLY);
        view = new RecordingUiView();
        session.bind(view);
        cell = new RecordingUiView();
        view.rows.bind(cell, 0);
        assertEquals("", text(cell, "detail"));
        assertFalse(cell.tooltips.get("label").toString().contains("%"));
    }

    @Test
    public void hiddenAndMissingPoolsNeverPublishCountOrCapacityAsRemainingQuota() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.daily.setPreviewMode(LotteryPreviewMode.NONE);
        var view = new RecordingUiView();
        var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        assertEquals("", text(view, "preview_count"));
        assertEquals(0, view.rows.size());
        assertEquals(translated("lottery_available_count"), text(view, "available_label"));
        session.detach(UiDetachReason.RELOAD);
        port.pool = "missing";
        view = new RecordingUiView();
        session.bind(view);
        assertEquals("", text(view, "policy"));
        assertEquals("\u2014", text(view, "fee"));
        assertFalse(view.enabled.get("single"));
    }

    @Test
    public void fourActualAnimationButtonsUseSamePreferenceAndRejectRetiredCallbacks() {
        var port = new LotteryUiOperationHandoffTest.Port();
        var view = new RecordingUiView();
        var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        for (var style : LotteryAnimationStyle.values()) {
            String id = "animation_" + style.name().toLowerCase(Locale.ROOT);
            assertNotNull("Missing animation action: " + id, view.actions.get(id));
            view.actions.get(id).run();
            assertEquals(style, port.style);
            assertFalse(view.enabled.get("animation_" + style.name().toLowerCase(Locale.ROOT)));
        }
        Runnable retired = view.actions.get("animation_strip");
        session.detach(UiDetachReason.SWITCH);
        retired.run();
        assertEquals(LotteryAnimationStyle.INSTANT, port.style);
    }

    @Test
    public void allCountUsesCompactLabelAndRetainsCompleteTooltip() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.count("all");
        var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        var all = view.controls.get("count").options().stream().filter(option -> "all".equals(option.value())).findFirst().orElseThrow();
        assertEquals(translated("lottery_draw_all_short"), all.displayLabel());
        assertEquals(translated("lottery_draw_all"), view.tooltips.get("count").toString());
    }

    @Test
    public void approvedTemplatesUseScopedGridAndNoPrototypeOrManagementCode() throws Exception {
        Path root = Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        assertTrue("Dedicated compact lottery stylesheet", Files.exists(root.resolve("lottery.css")));
        for (String name : List.of("lottery", "lottery-result")) {
            String html = Files.readString(root.resolve(name + ".html"), StandardCharsets.UTF_8);
            assertTrue(html.contains("href=\"" + name + ".css\""));
            assertTrue(html.contains("id=\"fields_middle\""));
            assertTrue(html.contains("id=\"fields_end\""));
            assertFalse(html.contains("<script"));
            assertFalse(html.contains("edit-pool"));
            assertFalse(html.contains("scenario"));
        }
        assertEquals(List.of(), UiBoundaryChecks.resources(UiBoundaryChecks.ownResources("sakura_sign_in"), false));
    }

    @Test
    public void bottomDrawBarKeepsRewardViewportFixedAndHasNoRetainedIcons() throws Exception {
        var view = new RecordingUiView();
        new LotteryUiSession(new LotteryUiOperationHandoffTest.Port(), () -> true).bind(view);
        assertFalse("Scrollable billing cannot own retained icons outside the reward viewport", view.icons.containsKey("single_icon"));
        assertFalse(view.icons.containsKey("batch_icon"));
        Path root = Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        String html = Files.readString(root.resolve("lottery.html"));
        for (String name : List.of("single", "batch"))
            assertFalse(html.contains("id=\"" + name + "_icon\""));
        String css = Files.readString(root.resolve("lottery.css"));
        assertFalse("No side billing column", html.contains("<aside"));
        assertTrue(html.indexOf("class=\"billing\"") > html.indexOf("id=\"content\""));
        assertFalse(html.contains("quote_status"));
        String billing = css.substring(css.indexOf(".billing{"), css.indexOf('}', css.indexOf(".billing{")));
        assertTrue(billing.contains("flex-shrink:0"));
        assertFalse(billing.contains("overflow-y:auto"));
    }

    @Test
    public void compactViewportReservesACompleteRewardRow() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery.css"));
        assertTrue(css.contains("max-height:12px"));
        String description = css.substring(css.indexOf(".description{"), css.indexOf('}', css.indexOf(".description{")));
        assertFalse("A fixed ten-pixel border box clips an eight-pixel AUI line", description.contains("height:10px"));
        assertTrue(description.contains("padding:0 8px"));
        assertTrue(css.contains("max-width:480px"));
        assertTrue(css.contains("max-height:280px"));
        assertTrue("Use the AUI font renderer explicitly", css.contains("font-family:default"));
        assertTrue(css.contains(".status[disabled]"));
    }

    @Test
    public void compactCaptionsKeepPercentagesAndAnimationNamesWithinTheirColumns() throws Exception {
        var port = new LotteryUiOperationHandoffTest.Port();
        var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        var cell = new RecordingUiView();
        view.providers.get("fields").bind(cell, 0);
        assertEquals("100%", text(cell, "detail"));
        assertFalse(cell.tooltips.get("icon").toString().contains("%%"));
        for (var style : LotteryAnimationStyle.values())
            assertEquals(translated("lottery_animation_button_" + style.name().toLowerCase(Locale.ROOT)), text(view, "animation_" + style.name().toLowerCase(Locale.ROOT)));
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery.css"));
        String labels = css.substring(css.indexOf(".grid .label,.grid .paragraph{"), css.indexOf('}', css.indexOf(".grid .label,.grid .paragraph{")));
        assertTrue(labels.contains("width:100%"));
    }

    @Test
    public void gridCoordinatesReturnToFlatRewardIndexesAcrossNativeAndRepeatedHandoffs() {
        var port = new LotteryUiOperationHandoffTest.Port();
        var session = new LotteryUiSession(port, () -> true);
        for (int repeat = 0; repeat < 4; repeat++) {
            var view = new RecordingUiView();
            view.state.rowPosition(18);
            session.bind(view);
            assertEquals(6, view.state.rowPosition(), 0);
            view.state.rowPosition(5.5);
            session.detach(UiDetachReason.SWITCH);
            assertEquals(16.5, view.state.rowPosition(), 0);
            session.detach(UiDetachReason.RELOAD);
            assertEquals(16.5, view.state.rowPosition(), 0);
            var nativeView = new RecordingUiView();
            nativeView.nativeView = true;
            nativeView.state.rowPosition(16.5);
            session.bind(nativeView);
            assertEquals(16.5, nativeView.state.rowPosition(), 0);
            session.detach(UiDetachReason.SWITCH);
        }
        long[] now = {0};
        var packet = LotteryUiAnimationParityTest.packet();
        var many = new xin.vanilla.sakura.network.packet.LotteryRevealPacket(packet.getOperationId(), packet.getToken(), packet.getPoolName(),
                Collections.nCopies(100, Reward.getDefault()), packet.getPreview(), LotteryPreviewMode.ALL);
        var result = new LotteryRevealUiSession(many, LotteryAnimationStyle.STRIP,
                () -> now[0], () -> true, (key, scan) -> key == 69, () -> {
        });
        var view = new RecordingUiView();
        view.state.rowPosition(18);
        result.bind(view);
        assertEquals(18, view.state.rowPosition(), 0);
        now[0] = 7000;
        result.tick();
        assertEquals(6, view.state.rowPosition(), 0);
        result.detach(UiDetachReason.SWITCH);
        assertEquals(18, view.state.rowPosition(), 0);
    }
}
