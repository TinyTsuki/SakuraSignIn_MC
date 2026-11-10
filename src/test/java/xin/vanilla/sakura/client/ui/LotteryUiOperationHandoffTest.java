package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.api.client.ui.UiPageSource;
import xin.vanilla.banira.api.client.ui.UiValue;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.network.LotteryClientRequests;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.screen.LotteryRevealScreen;
import xin.vanilla.sakura.screen.LotteryScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class LotteryUiOperationHandoffTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        xin.vanilla.sakura.test.ForgeUnitTestBootstrap.bootstrap();
        SignInUiParityTest.bootstrap();
    }

    static final class Port implements LotteryUiSession.Port {
        final LotteryClientRequests requests = new LotteryClientRequests(UUID::randomUUID);
        final List<LotteryQuoteRequest> quotes = new ArrayList<>();
        final List<LotteryDrawRequest> draws = new ArrayList<>();
        long millis;
        int scans;
        String pool = "daily", count = "5";
        LotteryAnimationStyle style = LotteryAnimationStyle.STRIP;
        final LotteryPool daily = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY, 12, 60, new RewardList(List.of(Reward.getDefault())));
        final LotteryPaymentViewModel payment = new LotteryPaymentViewModel(requests, () -> millis, quotes::add, draws::add);

        Port() {
            requests.observeGeneration(1);
            daily.setDrawCounts(List.of(1, 5));
            daily.setDescription("Long\nDescription");
            daily.setVoucherExpression("xpPoints(2)");
        }

        public void prepare() {
            payment.select(pool, 5, 1);
        }

        public List<LotteryPool> pools() {
            scans++;
            return List.of(daily);
        }

        public String poolId() {
            return pool;
        }

        public void pool(String value) {
            pool = value;
            prepare();
        }

        public List<String> counts() {
            return List.of("5", "10", "12", "all");
        }

        public String count() {
            return count;
        }

        public void count(String value) {
            count = value;
            prepare();
        }

        public LotteryAnimationStyle animation() {
            return style;
        }

        public void animation(LotteryAnimationStyle value) {
            style = value;
        }

        public long generation() {
            return requests.getGeneration();
        }

        public LotteryPaymentViewModel payment() {
            return payment;
        }

        public String name(Reward reward) {
            return "Reward";
        }

        void accept() {
            var request = quotes.get(quotes.size() - 1);
            assertTrue(requests.accept(new LotteryQuote(request, LotteryQuote.Status.READY, request.getGeneration(), 12, "xpPoints(2)", UUID.randomUUID().toString())));
        }
    }

    @Test
    public void batchSelectionBridgesNativeCountAndKeepsLegacyAll() {
        Port port = new Port();
        port.daily.setDrawCounts(List.of(1, 5, 10));
        RecordingUiView view = new RecordingUiView();
        LotteryUiSession session = new LotteryUiSession(port, () -> true);
        session.bind(view);
        view.changes.get("batch_count").accept(UiValue.text("10"));
        assertEquals("10", port.count);
        assertEquals(10, port.batch());
        session.detach(UiDetachReason.RELOAD);
        port.daily.setDrawCounts(null);
        port.count = "all";
        RecordingUiView legacy = new RecordingUiView();
        session.bind(legacy);
        assertEquals(-1, port.batch());
        assertEquals("all", legacy.values.get("batch_count").asText());
        assertEquals(SakuraComponent.get().transClient("word", "lottery_draw_all").toString(), legacy.texts.get("batch_label").toString());
    }

    @Test
    public void bothOriginalOwnersExposePublicSharedPages() {
        assertTrue("Lottery main must retain its original owner", UiPageSource.class.isAssignableFrom(LotteryScreen.class));
        assertTrue("Lottery result must retain its original owner", UiPageSource.class.isAssignableFrom(LotteryRevealScreen.class));
    }

    @Test
    public void mainViewRemovalDoesNotCancelUnsentPaymentIntent() throws Exception {
        String source = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/screen/LotteryScreen.java"), StandardCharsets.UTF_8);
        assertFalse("Reload/switch removal is not cancellation", source.contains("onRemoved() { payment.cancelIntent()"));
    }

    @Test
    public void templatesUseSharedLightweightResources() throws Exception {
        Path root = Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in");
        for (String name : new String[]{"lottery", "lottery-result"}) {
            assertTrue("Missing " + name, Files.exists(root.resolve(name + ".html")));
            String html = Files.readString(root.resolve(name + ".html"), StandardCharsets.UTF_8);
            assertTrue(html.contains("../banira_codex/shared.css"));
            assertFalse(html.contains("<script"));
            assertTrue(Files.size(root.resolve(name + ".html")) <= 12288);
        }
    }

    @Test
    public void correlatedSingleIntentSurvivesSwitchResizeReloadAndNativeHandoff() {
        for (UiDetachReason reason : new UiDetachReason[]{UiDetachReason.SWITCH, UiDetachReason.RESIZE, UiDetachReason.RELOAD, UiDetachReason.FALLBACK}) {
            Port port = new Port();
            LotteryUiSession session = new LotteryUiSession(port, () -> true);
            RecordingUiView first = new RecordingUiView();
            session.bind(first);
            assertTrue(port.quotes.isEmpty());
            session.tick();
            port.accept();
            session.tick();
            first.actions.get("single").run();
            assertEquals(1, port.quotes.size());
            first.actions.get("confirm_draw").run();
            assertEquals(2, port.quotes.size());
            assertTrue(port.draws.isEmpty());
            Runnable stale = first.actions.get("single");
            session.detach(reason);
            RecordingUiView nativeView = new RecordingUiView();
            nativeView.nativeView = true;
            session.bind(nativeView);
            assertTrue(nativeView.actions.isEmpty());
            session.detach(reason);
            session.bind(new RecordingUiView());
            stale.run();
            port.accept();
            session.tick();
            session.tick();
            assertEquals(1, port.draws.size());
            String operation = port.draws.get(0).getOperationId();
            for (int i = 0; i < 4; i++) {
                session.detach(reason);
                session.bind(new RecordingUiView());
                session.tick();
            }
            assertEquals(1, port.draws.size());
            assertEquals(operation, port.draws.get(0).getOperationId());
            assertEquals(2, port.quotes.size());
        }
    }

    @Test
    public void closeCancelsOnlyUnsentIntentAndDisconnectedCallbacksCannotPay() {
        Port port = new Port();
        boolean[] connected = {true};
        LotteryUiSession session = new LotteryUiSession(port, () -> connected[0]);
        RecordingUiView first = new RecordingUiView();
        session.bind(first);
        session.tick();
        port.accept();
        session.tick();
        Runnable late = first.actions.get("single");
        late.run();
        first.actions.get("confirm_draw").run();
        session.close(UiCloseReason.USER_CLOSE);
        port.accept();
        port.payment.tick();
        late.run();
        assertTrue(port.draws.isEmpty());
        port = new Port();
        Port current = port;
        session = new LotteryUiSession(current, () -> connected[0]);
        first = new RecordingUiView();
        session.bind(first);
        session.tick();
        current.accept();
        session.tick();
        connected[0] = false;
        first.actions.get("batch").run();
        assertTrue(current.draws.isEmpty());
    }

    @Test
    public void drawConfirmationCanCancelAndOnlySubmitsOnce() {
        Port port = new Port();
        var session = new LotteryUiSession(port, () -> true);
        var view = new RecordingUiView();
        session.bind(view);
        session.tick();
        port.accept();
        session.tick();
        view.actions.get("batch").run();
        assertTrue(view.enabled.get("confirm_panel"));
        assertTrue(port.draws.isEmpty());
        view.actions.get("confirm_cancel").run();
        view.actions.get("confirm_draw").run();
        assertTrue(port.draws.isEmpty());
        view.actions.get("batch").run();
        view.actions.get("confirm_draw").run();
        view.actions.get("confirm_draw").run();
        assertFalse(view.enabled.get("confirm_panel"));
        assertEquals(1, port.draws.size());
    }

    @Test
    public void controlsKeepDescriptionCountsModesAndAuthoritativeQuoteWithoutStableScans() {
        Port port = new Port();
        LotteryUiSession session = new LotteryUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals("Long\nDescription", view.texts.get("description").toString());
        assertFalse(view.controls.containsKey("count"));
        assertFalse(view.controls.containsKey("animation"));
        assertFalse(view.enabled.get("single"));
        session.tick();
        port.accept();
        session.tick();
        assertTrue(view.enabled.get("batch"));
        assertTrue(view.texts.get("fee").toString().endsWith("\u00d72"));
        int scans = port.scans;
        for (int i = 0; i < 100; i++) session.tick();
        assertEquals("Stable ticks must not rescan pools", scans, port.scans);
    }

    @Test
    public void busyFailedAndHiddenPoolStatesMatchNativeAuthorization() {
        Port port = new Port();
        port.daily.setPreviewMode(LotteryPreviewMode.NONE);
        LotteryUiSession session = new LotteryUiSession(port, () -> true);
        RecordingUiView view = new RecordingUiView();
        session.bind(view);
        assertEquals(0, view.rows.size());
        session.tick();
        var request = port.quotes.get(0);
        port.requests.accept(new LotteryQuote(request, LotteryQuote.Status.FORBIDDEN, 1, 0, "", ""));
        session.tick();
        assertFalse(view.enabled.get("batch"));
        view.actions.get("batch").run();
        assertTrue(port.draws.isEmpty());
        assertEquals("Forbidden quotes must not trigger a second immediate request", 1, port.quotes.size());
        port.millis += 3000;
        session.tick();
        port.accept();
        session.tick();
        assertTrue(view.enabled.get("single"));
        view.actions.get("single").run();
        view.actions.get("confirm_draw").run();
        assertTrue(port.draws.isEmpty());
        assertEquals(1, port.quotes.get(port.quotes.size() - 1).getCount());
        port.accept();
        session.tick();
        assertEquals(1, port.draws.size());
        assertFalse(view.changes.containsKey("count"));
        view.actions.get("single").run();
        assertEquals(1, port.draws.size());
    }

    @Test
    public void poolFormDraftsSurviveBackendHandoffWithEveryMetadataField() {
        LotteryPool pool = new Port().daily;
        pool.setArtworkTexture("test:textures/lottery/spring.png");
        net.minecraft.client.gui.screens.Screen parent = new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()) {
        };
        var form = (xin.vanilla.banira.client.gui.InputFormScreen) xin.vanilla.sakura.client.gui.LotteryPoolForm.create(parent, pool, value -> {
        });
        var session = form.uiPage().session();
        var view = new RecordingUiView();
        session.bind(view);
        assertEquals(14, view.rows.size());
        var description = new RecordingUiView();
        view.rows.bind(description, 2);
        assertEquals("Long\nDescription", description.draftValues.get("input"));
        description.drafts.get("input").accept("New\nDraft");
        var voucher = new RecordingUiView();
        view.rows.bind(voucher, 3);
        assertTrue(voucher.draftValues.get("input").contains("2"));
        assertFalse(voucher.enabled.get("input"));
        InputFormScreen formOwner = form;
        InputFormScreen.Widget cost = formOwner.uiSession().args().getWidgets().get(3);
        InputFormScreen child = (InputFormScreen) cost.picker().apply(parent,
                value -> formOwner.uiSession().draft("voucherExpression", value));
        child.uiSession().draft("amount", "3");
        assertTrue(child.uiSession().submit());
        var metadata = List.of("1,5,10", "2026-11-06 13:30", "2026-11-06 13:45");
        for (int i = 0; i < metadata.size(); i++) {
            var row = new RecordingUiView();
            view.rows.bind(row, i + 8);
            row.drafts.get("input").accept(metadata.get(i));
        }
        var mainArtwork = new RecordingUiView();
        view.rows.bind(mainArtwork, 11);
        String mainSummary = mainArtwork.draftValues.get("input");
        assertTrue(mainSummary.contains(pool.getArtworkTexture()));
        var thumbnail = new RecordingUiView();
        view.rows.bind(thumbnail, 12);
        String thumbnailSummary = thumbnail.draftValues.get("input");
        assertNotNull(thumbnailSummary);
        session.detach(UiDetachReason.SWITCH);
        var next = new RecordingUiView();
        session.bind(next);
        description = new RecordingUiView();
        next.rows.bind(description, 2);
        assertEquals("New\nDraft", description.draftValues.get("input"));
        voucher = new RecordingUiView();
        next.rows.bind(voucher, 3);
        assertTrue(voucher.draftValues.get("input").contains("3"));
        assertNotNull(voucher.actions.get("pick"));
        for (int i = 0; i < metadata.size(); i++) {
            var row = new RecordingUiView();
            next.rows.bind(row, i + 8);
            assertEquals(metadata.get(i), row.draftValues.get("input"));
        }
        mainArtwork = new RecordingUiView();
        next.rows.bind(mainArtwork, 11);
        assertEquals(mainSummary, mainArtwork.draftValues.get("input"));
        assertNotNull(mainArtwork.actions.get("pick"));
        thumbnail = new RecordingUiView();
        next.rows.bind(thumbnail, 12);
        assertEquals(thumbnailSummary, thumbnail.draftValues.get("input"));
        assertNotNull(thumbnail.actions.get("pick"));
    }

    @Test
    public void actualOwnersRetainOneSessionAndSameNativeFallback() {
        var main = new LotteryScreen(null);
        var result = new LotteryRevealScreen(main, LotteryUiAnimationParityTest.packet());
        for (var owner : List.of(main, result)) {
            var source = (UiPageSource) owner;
            assertSame(source.pageSession(), source.uiPage().session());
            assertSame(source.pageSession(), source.pageSession());
            assertSame(owner, source.uiPage().nativeFactory().create(null));
        }
    }

    @Test
    public void retiredPreviewProvidersCannotWriteAfterDetachOrConnectionChange() {
        Port port = new Port();
        boolean[] connected = {true};
        var session = new LotteryUiSession(port, () -> connected[0]);
        var view = new RecordingUiView();
        session.bind(view);
        var rows = view.rows;
        session.detach(UiDetachReason.SWITCH);
        var late = new RecordingUiView();
        rows.bind(late, 0);
        assertEquals(0, late.writes);
        session.bind(new RecordingUiView());
        connected[0] = false;
        rows.bind(late, 0);
        assertEquals(0, late.writes);
    }

    @Test
    public void descriptionScrollIsRestoredAfterNativeViewportLayout() throws Exception {
        String source = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/screen/LotteryScreen.java"), StandardCharsets.UTF_8);
        String paragraph = source.substring(source.indexOf("private void renderParagraph"), source.indexOf("private void renderPreview"));
        assertTrue("Restoration must wait until native content geometry is known", paragraph.contains("pendingDescriptionScroll"));
        assertTrue(paragraph.indexOf("viewport.layout(") < paragraph.indexOf("viewport.scrollTo("));
    }
}
