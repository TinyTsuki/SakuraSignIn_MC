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
import java.util.Locale;

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

    private static String remaining(String count) {
        return SakuraComponent.get().translateClient("format", "lottery_remaining_s", count);
    }

    @Test
    public void identicalPeriodicQuotesLeaveTheAuiUntouched() {
        lombok.var port = new LotteryUiOperationHandoffTest.Port();
        lombok.var view = new RecordingUiView();
        lombok.var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        assertEquals("\u2014", text(view, "fee"));
        assertEquals(remaining("\u2014"), text(view, "available"));
        session.tick();
        assertEquals(translated("lottery_quote_pending"), text(view, "info"));
        assertEquals(Boolean.TRUE, view.enabled.get("info"));
        port.accept();
        session.tick();
        assertEquals(translated("lottery_voucher_points") + " \u00d72", text(view, "fee"));
        assertEquals(remaining("12"), text(view, "available"));
        assertEquals("", text(view, "info"));
        assertEquals(Boolean.FALSE, view.enabled.get("info"));
        assertFalse(view.texts.containsKey("quote_status"));
        int settledWrites = view.writes;
        for (int i = 1; i <= 3; i++) {
            port.millis = 3000L * i;
            session.tick();
            assertEquals(i + 1, port.quotes.size());
            assertTrue(view.enabled.get("batch"));
            assertEquals(settledWrites, view.writes);
            port.millis += 150;
            port.accept();
            session.tick();
            assertEquals(settledWrites, view.writes);
        }
        port.millis = 12000;
        session.tick();
        lombok.var refresh = port.quotes.get(port.quotes.size() - 1);
        assertTrue(port.requests.accept(new LotteryQuote(refresh, LotteryQuote.Status.READY, 1, 7, "xpPoints(3)", java.util.UUID.randomUUID().toString())));
        session.tick();
        assertEquals(translated("lottery_voucher_points") + " \u00d73", text(view, "fee"));
        assertEquals(remaining("7"), text(view, "available"));
    }

    @Test
    public void auiConfirmationDuringRefreshWaitsForTheMatchingReply() {
        lombok.var port = new LotteryUiOperationHandoffTest.Port();
        lombok.var view = new RecordingUiView();
        lombok.var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        session.tick();
        port.accept();
        session.tick();
        port.millis = 3000;
        session.tick();
        view.actions.get("batch").run();
        assertTrue(view.enabled.get("confirm_panel"));
        view.actions.get("confirm_draw").run();
        assertFalse(view.enabled.get("confirm_panel"));
        assertEquals(2, port.quotes.size());
        assertTrue(port.draws.isEmpty());
        port.accept();
        session.tick();
        session.tick();
        assertEquals(1, port.draws.size());
    }

    @Test
    public void everyRejectedQuoteDisablesDrawsAndKeepsAnHonestFailureReason() {
        for (LotteryQuote.Status status : LotteryQuote.Status.values()) {
            if (status == LotteryQuote.Status.READY) continue;
            lombok.var port = new LotteryUiOperationHandoffTest.Port();
            lombok.var view = new RecordingUiView();
            lombok.var session = new LotteryUiSession(port, () -> true);
            session.bind(view);
            session.tick();
            lombok.var request = port.quotes.get(0);
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
            assertEquals(remaining("\u2014"), text(view, "available"));
            String key;
            switch (status) {
                case FORBIDDEN:
                    key = "lottery_permission_denied";
                    break;
                case STALE_GENERATION:
                    key = "lottery_configuration_changed";
                    break;
                case POOL_NOT_FOUND:
                    key = "lottery_pool_not_found";
                    break;
                case POOL_CLOSED:
                    key = "lottery_pool_closed";
                    break;
                default:
                    key = "lottery_payment_unavailable";
                    break;
            }
            assertEquals(translated(key), text(view, "info"));
            assertEquals(Boolean.TRUE, view.enabled.get("info"));
            view.actions.get("single").run();
            assertTrue(port.draws.isEmpty());
        }
    }

    @Test
    public void singleVirtualListRetainsOrderTailAndHiddenProbabilityBoundaries() {
        lombok.var port = new LotteryUiOperationHandoffTest.Port();
        for (int i = 1; i < 8; i++) port.daily.getRewards().add(Reward.getDefault());
        lombok.var view = new RecordingUiView();
        lombok.var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        assertEquals(com.google.common.collect.ImmutableSet.of("pool_rows", "fields"), view.providers.keySet());
        lombok.var rows = (xin.vanilla.banira.api.client.ui.UiRowLayout) view.providers.get("fields");
        assertEquals("content", rows.scrollerId());
        assertEquals(8, rows.size());
        for (int row = 0; row < rows.size(); row++) assertEquals("daily:" + row, rows.key(row));
        lombok.var stale = view.rows;
        session.detach(UiDetachReason.SWITCH);
        lombok.var late = new RecordingUiView();
        stale.bind(late, 0);
        assertEquals(0, late.writes);
        port.daily.setPreviewMode(LotteryPreviewMode.PROBABILITY_ONLY);
        view = new RecordingUiView();
        session.bind(view);
        lombok.var cell = new RecordingUiView();
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
        lombok.var port = new LotteryUiOperationHandoffTest.Port();
        port.daily.setPreviewMode(LotteryPreviewMode.NONE);
        lombok.var view = new RecordingUiView();
        lombok.var session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        assertEquals("", text(view, "preview_count"));
        assertEquals(0, view.rows.size());
        assertEquals(remaining("\u2014"), text(view, "available"));
        session.detach(UiDetachReason.RELOAD);
        port.pool = "missing";
        view = new RecordingUiView();
        session.bind(view);
        assertEquals("", text(view, "policy"));
        assertEquals("\u2014", text(view, "fee"));
        assertFalse(view.enabled.get("single"));
    }

    @Test
    public void batchOnlyPoolExposesItsConfiguredCountAndArtworkWithoutPlayerControls() {
        lombok.var port = new LotteryUiOperationHandoffTest.Port();
        port.daily.setDrawCounts(com.google.common.collect.ImmutableList.of(7));
        lombok.var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        assertFalse(view.enabled.get("single_slot"));
        assertTrue(view.enabled.get("batch_slot"));
        assertTrue(text(view, "batch_label").contains("7"));
        assertFalse(view.controls.containsKey("count"));
        assertFalse(view.actions.containsKey("animation_strip"));
        assertNotNull(view.icons.get("poster"));
        assertFalse(view.enabled.get("details_panel"));
        view.actions.get("details").run();
        assertTrue(view.enabled.get("details_panel"));
        view.actions.get("details_close").run();
        assertFalse(view.enabled.get("details_panel"));
        lombok.var row = new RecordingUiView();
        view.providers.get("pool_rows").bind(row, 0);
        assertEquals("Daily", text(view, "title"));
        assertEquals("Daily", text(row, "label"));
        assertNotNull(row.icons.get("icon"));
    }

    @Test
    public void approvedTemplatesKeepTheRewardViewportSeparateFromTheDialogHeader() throws Exception {
        Path root = java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        assertTrue("Dedicated compact lottery stylesheet", Files.exists(root.resolve("sakura-lottery.css")));
        for (String name : com.google.common.collect.ImmutableList.of("lottery", "lottery-result")) {
            String html = new String(Files.readAllBytes(root.resolve(name + ".html")), StandardCharsets.UTF_8);
            assertTrue(html.contains("href=\"" + (name.equals("lottery") ? "sakura-lottery" : name) + ".css\""));
            assertTrue(html.contains("id=\"fields\""));
            assertEquals(name.equals("lottery-result"), html.contains("id=\"fields_middle\""));
            assertEquals(name.equals("lottery-result"), html.contains("id=\"fields_end\""));
            if (name.equals("lottery")) {
                assertTrue(html.indexOf("id=\"title\"") < html.indexOf("id=\"policy\""));
                assertTrue(html.indexOf("id=\"policy\"") > html.indexOf("class=\"pool-copy\""));
                assertTrue(html.indexOf("id=\"details_close\"") < html.indexOf("id=\"content\""));
                assertTrue(html.indexOf("id=\"content\"") < html.indexOf("id=\"fields\""));
            }
            assertFalse(html.contains("<script"));
            assertFalse(html.contains("edit-pool"));
            assertFalse(html.contains("scenario"));
        }
        assertEquals(com.google.common.collect.ImmutableList.of(), UiBoundaryChecks.resources(UiBoundaryChecks.ownResources("sakura_sign_in"), false));
    }

    @Test
    public void poolEditorOptionTooltipsAreAvailableInBothLanguages() throws Exception {
        lombok.var keys = new java.util.ArrayList<>(com.google.common.collect.ImmutableList.of("lottery_voucher_item", "lottery_voucher_points", "lottery_voucher_levels",
                "lottery_voucher_score", "lottery_voucher_advanced", "lottery_artwork_none", "lottery_artwork_file", "lottery_artwork_auto",
                "lottery_artwork_item", "lottery_artwork_texture"));
        for (lombok.var style : xin.vanilla.sakura.data.lottery.LotteryAnimationStyle.values())
            keys.add("enum_lottery_animation_style_" + style.name().toLowerCase(java.util.Locale.ROOT));
        for (String locale : com.google.common.collect.ImmutableList.of("en_us", "zh_cn")) {
            com.google.gson.JsonObject language = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(java.nio.file.Paths.get(
                    "src/main/resources/assets/sakura_sign_in/lang/" + locale + ".json")), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : keys) {
                String tooltip = "word.sakura_sign_in." + key + "_tooltip";
                assertTrue(locale + ": " + tooltip, language.has(tooltip) && !language.get(tooltip).getAsString().trim().isEmpty());
            }
        }
    }

    @Test
    public void bottomDrawBarKeepsRewardViewportFixedAndHasNoRetainedIcons() throws Exception {
        lombok.var view = new RecordingUiView();
        new LotteryUiSession(new LotteryUiOperationHandoffTest.Port(), () -> true).bind(view);
        assertFalse("Scrollable billing cannot own retained icons outside the reward viewport", view.icons.containsKey("single_icon"));
        assertFalse(view.icons.containsKey("batch_icon"));
        Path root = java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        String html = new String(Files.readAllBytes(root.resolve("lottery.html")), java.nio.charset.StandardCharsets.UTF_8);
        for (String name : com.google.common.collect.ImmutableList.of("single", "batch"))
            assertFalse(html.contains("id=\"" + name + "_icon\""));
        String css = new String(Files.readAllBytes(root.resolve("sakura-lottery.css")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("class=\"pool-rail\""));
        assertTrue(html.indexOf("class=\"billing footer\"") > html.indexOf("class=\"feature\""));
        assertTrue(html.indexOf("id=\"content\"") > html.indexOf("id=\"details_panel\""));
        assertFalse(html.contains("quote_status"));
        assertTrue(html.contains("class=\"billing footer\""));
    }

    @Test
    public void compactViewportKeepsRailAndDetailsIndependentlyScrollable() throws Exception {
        String css = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in/sakura-lottery.css")), java.nio.charset.StandardCharsets.UTF_8);
        String html = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery.html")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("id=\"pool_scroll\" class=\"content\""));
        assertTrue(html.contains("id=\"detail\" class=\"content\""));
        assertTrue(css.contains("max-width:680px"));
        String baseCss = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery.css")), StandardCharsets.UTF_8);
        assertTrue(baseCss.contains("max-width:560px"));
    }

    @Test
    public void compactRowsKeepRewardNamesAndPercentagesTogether() throws Exception {
        lombok.var port = new LotteryUiOperationHandoffTest.Port();
        lombok.var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        lombok.var cell = new RecordingUiView();
        view.providers.get("fields").bind(cell, 0);
        assertEquals("100%", text(cell, "detail"));
        assertFalse(cell.tooltips.get("icon").toString().contains("%%"));
        String css = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in/sakura-lottery.css")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(css.contains("flex-direction:row"));
        assertTrue(css.contains(".reward-row .label{flex:1;min-width:0}"));
        assertTrue(css.contains(".reward-row .paragraph{flex:none"));
        String baseCss = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery.css")), StandardCharsets.UTF_8);
        assertTrue(baseCss.contains(".reward-row .paragraph{flex-shrink:0"));
    }

    @Test
    public void rewardIndexesSurviveRepeatedHandoffsAndTheResultGridStillConvertsCoordinates() {
        lombok.var port = new LotteryUiOperationHandoffTest.Port();
        lombok.var session = new LotteryUiSession(port, () -> true);
        for (int repeat = 0; repeat < 4; repeat++) {
            lombok.var view = new RecordingUiView();
            view.state.rowPosition(18);
            session.bind(view);
            assertEquals(18, view.state.rowPosition(), 0);
            view.state.rowPosition(16.5);
            session.detach(UiDetachReason.SWITCH);
            assertEquals(16.5, view.state.rowPosition(), 0);
            session.detach(UiDetachReason.RELOAD);
            assertEquals(16.5, view.state.rowPosition(), 0);
            lombok.var nativeView = new RecordingUiView();
            nativeView.nativeView = true;
            nativeView.state.rowPosition(16.5);
            session.bind(nativeView);
            assertEquals(16.5, nativeView.state.rowPosition(), 0);
            session.detach(UiDetachReason.SWITCH);
        }
        long[] now = {0};
        lombok.var packet = LotteryUiAnimationParityTest.packet();
        lombok.var many = new xin.vanilla.sakura.network.packet.LotteryRevealPacket(packet.getOperationId(), packet.getToken(), packet.getPoolName(),
                Collections.nCopies(100, Reward.getDefault()), packet.getPreview(), LotteryPreviewMode.ALL);
        lombok.var result = new LotteryRevealUiSession(many, LotteryAnimationStyle.STRIP,
                () -> now[0], () -> true, () -> 69, () -> {
        });
        lombok.var view = new RecordingUiView();
        view.state.rowPosition(18);
        result.bind(view);
        assertEquals(18, view.state.rowPosition(), 0);
        now[0] = 7000;
        result.tick();
        assertEquals(6, view.state.rowPosition(), 0);
        result.detach(UiDetachReason.SWITCH);
        assertEquals(18, view.state.rowPosition(), 0);
    }

    @Test
    public void drawClickedDuringRefreshWaitsForAMatchingFee() {
        LotteryUiOperationHandoffTest.Port port = new LotteryUiOperationHandoffTest.Port();
        RecordingUiView view = new RecordingUiView();
        LotteryUiSession session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        session.tick();
        port.accept();
        session.tick();
        port.millis = 3000;
        session.tick();
        view.actions.get("batch").run();
        view.actions.get("confirm_draw").run();
        assertTrue(port.draws.isEmpty());
        xin.vanilla.sakura.data.lottery.LotteryQuoteRequest pending = port.quotes.get(port.quotes.size() - 1);
        assertTrue(port.requests.accept(new LotteryQuote(pending, LotteryQuote.Status.READY, pending.getGeneration(),
                12, "xpPoints(3)", java.util.UUID.randomUUID().toString())));
        session.tick();
        assertTrue(port.draws.isEmpty());
        view.actions.get("batch").run();
        view.actions.get("confirm_draw").run();
        assertEquals(1, port.draws.size());
    }
}
