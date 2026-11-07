package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiValue;
import xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryQuote;
import xin.vanilla.sakura.reward.Reward;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class LotteryRecentAdapterTest {
    @Rule
    public final xin.vanilla.sakura.test.NativeClientRule nativeClient = new xin.vanilla.sakura.test.NativeClientRule();
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @Test
    public void legacyNativePortBridgesBatchWithoutChangingAnimationOrAllCount() {
        var original = new LotteryUiOperationHandoffTest.Port();
        LotteryUiSession.Port port = original;
        assertEquals(5, port.batch());
        port.batch(10);
        assertEquals("10", original.count);
        port.batch(-1);
        assertEquals("all", original.count);
        assertEquals(-1, port.batch());
        assertEquals(original.style, port.animation());
    }

    @Test
    public void newBatchOnlyPortDoesNotNeedLegacyControlMethods() {
        var original = new LotteryUiOperationHandoffTest.Port();
        LotteryUiSession.Port port = new LotteryUiSession.Port() {
            private int batch = 5;

            public void prepare() {
                original.prepare();
            }

            public List<LotteryPool> pools() {
                return original.pools();
            }

            public String poolId() {
                return original.poolId();
            }

            public void pool(String value) {
                original.pool(value);
            }

            public int batch() {
                return batch;
            }

            public void batch(int value) {
                batch = value;
            }

            public long generation() {
                return original.generation();
            }

            public LotteryPaymentViewModel payment() {
                return original.payment();
            }

            public String name(Reward reward) {
                return original.name(reward);
            }
        };
        var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        assertTrue(view.enabled.get("batch_slot"));
        assertFalse(view.controls.containsKey("animation"));
        port.batch(5);
        assertEquals(5, port.batch());
    }

    @Test
    public void explicitCountsExcludeSingleAndRejectStalePlayerChoices() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.daily.setDrawCounts(List.of(7));
        var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        assertFalse(view.enabled.get("single_slot"));
        assertTrue(view.enabled.get("batch_slot"));
        assertFalse(view.enabled.get("batch_picker"));
        view.actions.get("single").run();
        assertFalse(view.enabled.get("confirm_panel"));
        view.changes.get("batch_count").accept(UiValue.text("10"));
        assertEquals("5", port.count);
        assertFalse(view.controls.containsKey("count"));
        assertFalse(view.controls.containsKey("animation"));
    }

    @Test
    public void nullLegacyCountsRemainUsableAndKeepAllChoice() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.daily.setDrawCounts(null);
        port.count("all");
        var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        assertTrue(view.enabled.get("single_slot"));
        assertTrue(view.enabled.get("batch_slot"));
        assertTrue(view.controls.get("batch_count").options().stream().anyMatch(option -> "-1".equals(option.value())));
        assertEquals(-1, port.batch());
    }

    @Test
    public void confirmationRejectsNullAndChangedFeesAndCannotSubmitTwice() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.prepare();
        port.payment.tick();
        port.accept();
        port.payment.tick();
        port.payment.clickConfirmed(5, null);
        port.payment.clickConfirmed(5, "xpPoints(3)");
        assertTrue(port.draws.isEmpty());
        port.payment.clickConfirmed(5, "xpPoints(2)");
        port.payment.clickConfirmed(5, "xpPoints(2)");
        assertEquals(1, port.draws.size());
    }

    @Test
    public void confirmedSingleWaitsForMatchingRequoteAndDoesNotAcceptChangedFee() {
        for (boolean sameFee : List.of(false, true)) {
            var port = new LotteryUiOperationHandoffTest.Port();
            port.prepare();
            port.payment.tick();
            port.accept();
            port.payment.tick();
            port.payment.clickConfirmed(1, "xpPoints(2)");
            assertTrue(port.draws.isEmpty());
            var quote = port.quotes.get(port.quotes.size() - 1);
            assertEquals(1, quote.getCount());
            assertTrue(port.requests.accept(new LotteryQuote(quote, LotteryQuote.Status.READY, 1, 12,
                    sameFee ? "xpPoints(2)" : "xpPoints(3)", UUID.randomUUID().toString())));
            port.payment.tick();
            port.payment.tick();
            assertEquals(sameFee ? 1 : 0, port.draws.size());
        }
    }

    @Test
    public void nativeChoicesHideChestWithoutChangingTheLegacyCountGateOrArtworkResolution() throws Exception {
        String nativeSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/xin/vanilla/sakura/screen/LotteryScreen.java"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(nativeSource.contains("style != LotteryAnimationStyle.CHEST"));
        assertTrue(nativeSource.contains("pool.allowsCount(1)"));
        assertTrue(nativeSource.contains("pool.getDrawCounts() == null || batchCount() != 1"));
        assertTrue(nativeSource.contains("layout.count()"));
        assertTrue(nativeSource.contains("layout.animation()"));
        String artwork = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/xin/vanilla/sakura/client/gui/lottery/LotteryArtwork.java"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(artwork.contains("ItemUtils.deserializeItemStack(pool.getArtworkItem())"));
        assertTrue(artwork.contains("UiGameElement element()"));
    }
}
