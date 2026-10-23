package xin.vanilla.sakura.client.ui;

import org.junit.*;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.Reward;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class LotteryUiPresentationTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @BeforeClass public static void bootstrap() { SignInUiParityTest.bootstrap(); }
    private static String text(RecordingUiView view, String id) {
        assertNotNull("Missing presentation field: " + id, view.texts.get(id));
        return view.texts.get(id).toString();
    }
    private static String translated(String key) { return SakuraComponent.get().transClient("word", key).toString(); }
    @Test public void quoteFeeStateAndCapacityAreIndependentAndRefreshKeepsConfirmedFee() {
        var port = new LotteryUiOperationHandoffTest.Port();
        var view = new RecordingUiView(); var session = new LotteryUiSession(port, () -> true);
        session.bind(view); assertEquals("\u2014", text(view, "fee")); assertEquals("\u2014", text(view, "available"));
        session.tick(); assertEquals(translated("lottery_confirming"), text(view, "quote_status"));
        port.accept(); session.tick(); assertEquals("xpPoints(2)", text(view, "fee")); assertEquals("12", text(view, "available"));
        assertEquals(translated("lottery_confirmed"), text(view, "quote_status"));
        port.millis = 3000; session.tick(); assertEquals("xpPoints(2)", text(view, "fee"));
        assertEquals("\u2014", text(view, "available")); assertFalse(view.enabled.get("batch"));
    }
    @Test public void everyRejectedQuoteDisablesDrawsAndKeepsAnHonestFailureReason() {
        for (LotteryQuote.Status status : LotteryQuote.Status.values()) {
            if (status == LotteryQuote.Status.READY) continue;
            var port = new LotteryUiOperationHandoffTest.Port(); var view = new RecordingUiView();
            var session = new LotteryUiSession(port, () -> true); session.bind(view); session.tick();
            var request = port.quotes.get(0);
            assertTrue(port.requests.accept(new LotteryQuote(request, status, 1, 0, "", ""))); session.tick();
            if (port.payment.isPending()) {
                request = port.quotes.get(port.quotes.size() - 1);
                assertEquals(1, request.getCount());
                assertTrue(port.requests.accept(new LotteryQuote(request, status, 1, 0, "", ""))); session.tick();
            }
            assertFalse(view.enabled.get("single")); assertFalse(view.enabled.get("batch"));
            assertEquals("\u2014", text(view, "available"));
            String key = switch (status) {
                case FORBIDDEN -> "lottery_permission_denied";
                case STALE_GENERATION -> "lottery_configuration_changed";
                case POOL_NOT_FOUND -> "lottery_pool_not_found";
                default -> "lottery_payment_unavailable";
            };
            assertEquals(translated(key), text(view, "info"));
            view.actions.get("single").run(); assertTrue(port.draws.isEmpty());
        }
    }
    @Test public void threeVirtualColumnsRetainOrderTailAndHiddenProbabilityBoundaries() {
        var port = new LotteryUiOperationHandoffTest.Port();
        for (int i = 1; i < 8; i++) port.daily.getRewards().add(Reward.getDefault());
        var view = new RecordingUiView(); var session = new LotteryUiSession(port, () -> true); session.bind(view);
        assertEquals(Set.of("fields", "fields_middle", "fields_end"), view.providers.keySet());
        int[] sizes = {3, 3, 2}; int column = 0;
        for (var rows : view.providers.values()) {
            assertEquals(sizes[column], rows.size());
            for (int row = 0; row < rows.size(); row++) assertEquals("daily:" + (row * 3 + column), rows.key(row));
            column++;
        }
        var stale = view.rows; session.detach(UiDetachReason.SWITCH);
        var late = new RecordingUiView(); stale.bind(late, 0); assertEquals(0, late.writes);
        port.daily.setPreviewMode(LotteryPreviewMode.PROBABILITY_ONLY); view = new RecordingUiView(); session.bind(view);
        var cell = new RecordingUiView(); view.rows.bind(cell, 0); assertNull(cell.icons.get("icon"));
        assertFalse(text(cell, "label").contains("Reward")); assertFalse(cell.tooltips.get("label").toString().contains("Reward"));
        session.detach(UiDetachReason.SWITCH); port.daily.setPreviewMode(LotteryPreviewMode.ITEMS_ONLY);
        view = new RecordingUiView(); session.bind(view); cell = new RecordingUiView(); view.rows.bind(cell, 0);
        assertEquals("", text(cell, "detail")); assertFalse(cell.tooltips.get("label").toString().contains("%"));
    }
    @Test public void hiddenAndMissingPoolsNeverPublishCountOrCapacityAsRemainingQuota() {
        var port = new LotteryUiOperationHandoffTest.Port(); port.daily.setPreviewMode(LotteryPreviewMode.NONE);
        var view = new RecordingUiView(); var session = new LotteryUiSession(port, () -> true); session.bind(view);
        assertEquals("", text(view, "preview_count")); assertEquals(0, view.rows.size());
        assertEquals(translated("lottery_available_count"), text(view, "available_label"));
        session.detach(UiDetachReason.RELOAD); port.pool = "missing"; view = new RecordingUiView(); session.bind(view);
        assertEquals("", text(view, "policy")); assertEquals("\u2014", text(view, "fee"));
        assertFalse(view.enabled.get("single"));
    }
    @Test public void fourActualAnimationButtonsUseSamePreferenceAndRejectRetiredCallbacks() {
        var port = new LotteryUiOperationHandoffTest.Port(); var view = new RecordingUiView();
        var session = new LotteryUiSession(port, () -> true); session.bind(view);
        for (var style : LotteryAnimationStyle.values()) {
            String id = "animation_" + style.name().toLowerCase(Locale.ROOT);
            assertNotNull("Missing animation action: " + id, view.actions.get(id));
            view.actions.get(id).run();
            assertEquals(style, port.style); assertFalse(view.enabled.get("animation_" + style.name().toLowerCase(Locale.ROOT)));
        }
        Runnable retired = view.actions.get("animation_strip"); session.detach(UiDetachReason.SWITCH); retired.run();
        assertEquals(LotteryAnimationStyle.INSTANT, port.style);
    }
    @Test public void approvedTemplatesUseScopedGridAndNoPrototypeOrManagementCode() throws Exception {
        Path root = Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        assertTrue("Dedicated compact lottery stylesheet", Files.exists(root.resolve("lottery.css")));
        for (String name : List.of("lottery", "lottery-result")) {
            String html = Files.readString(root.resolve(name + ".html"), StandardCharsets.UTF_8);
            assertTrue(html.contains("lottery.css")); assertTrue(html.contains("id=\"fields_middle\""));
            assertTrue(html.contains("id=\"fields_end\"")); assertFalse(html.contains("<script"));
            assertFalse(html.contains("edit-pool")); assertFalse(html.contains("scenario"));
        }
        assertEquals(List.of(), UiBoundaryChecks.resources(UiBoundaryChecks.ownResources("sakura_sign_in"), false));
    }
    @Test public void compactBillingScrollKeepsRewardViewportFixedAndHasNoRetainedIcons() throws Exception {
        var view = new RecordingUiView(); new LotteryUiSession(new LotteryUiOperationHandoffTest.Port(), () -> true).bind(view);
        assertFalse("Scrollable billing cannot own retained icons outside the reward viewport", view.icons.containsKey("single_icon"));
        assertFalse(view.icons.containsKey("batch_icon"));
        Path root = Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        String html = Files.readString(root.resolve("lottery.html"));
        for (String name : List.of("single", "batch"))
            assertFalse(html.contains("id=\"" + name + "_icon\""));
        String css = Files.readString(root.resolve("lottery.css"));
        String body = css.substring(css.indexOf(".body{"), css.indexOf('}', css.indexOf(".body{")));
        assertTrue(body.contains("overflow:hidden")); assertFalse(body.contains("flex-wrap"));
        String billing = css.substring(css.indexOf(".billing{"), css.indexOf('}', css.indexOf(".billing{")));
        assertTrue(billing.contains("overflow-y:auto"));
    }
    @Test public void compactViewportReservesACompleteRewardRow() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery.css"));
        assertTrue(css.contains(".description{max-height:18px;"));
        assertTrue(css.contains(".header,.footer{padding:3px 8px}"));
        assertTrue(css.contains(".status{margin:0}"));
        String body = css.substring(css.indexOf(".body{"), css.indexOf('}', css.indexOf(".body{")));
        assertTrue(body.contains("padding:4px"));
    }
    @Test public void gridCoordinatesReturnToFlatRewardIndexesAcrossNativeAndRepeatedHandoffs() {
        var port = new LotteryUiOperationHandoffTest.Port(); var session = new LotteryUiSession(port, () -> true);
        for (int repeat = 0; repeat < 4; repeat++) {
            var view = new RecordingUiView(); view.state.rowPosition(18);
            session.bind(view); assertEquals(6, view.state.rowPosition(), 0);
            view.state.rowPosition(5.5); session.detach(UiDetachReason.SWITCH);
            assertEquals(16.5, view.state.rowPosition(), 0);
            session.detach(UiDetachReason.RELOAD); assertEquals(16.5, view.state.rowPosition(), 0);
            var nativeView = new RecordingUiView(); nativeView.nativeView = true; nativeView.state.rowPosition(16.5);
            session.bind(nativeView); assertEquals(16.5, nativeView.state.rowPosition(), 0);
            session.detach(UiDetachReason.SWITCH);
        }
        long[] now = {0};
        var result = new LotteryRevealUiSession(LotteryUiAnimationParityTest.packet(), LotteryAnimationStyle.STRIP,
                () -> now[0], () -> true, () -> 69, () -> {});
        var view = new RecordingUiView(); view.state.rowPosition(18); result.bind(view);
        assertEquals(6, view.state.rowPosition(), 0); result.detach(UiDetachReason.SWITCH);
        assertEquals(18, view.state.rowPosition(), 0);
    }
}
