package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiValue;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.data.lottery.LotteryDrawRequest;
import xin.vanilla.sakura.data.lottery.LotteryQuote;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class RecentUiLegacyBridgeTest {
    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    @Test
    public void legacyCountAndAnimationPortsRetainTheirNullSemantics() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.daily.setDrawCounts(null);
        port.daily.setAnimationStyle(null);
        assertEquals(5, port.batch());
        port.batch(10);
        assertEquals("10", port.count());
        port.batch(-1);
        assertEquals("all", port.count());
        assertEquals(-1, port.batch());
        port.count = "5";
        var view = new RecordingUiView();
        new LotteryUiSession(port, () -> true).bind(view);
        assertTrue(view.enabled.get("single_slot"));
        assertTrue(view.enabled.get("batch_slot"));
        assertEquals(List.of("5", "10", "12", "-1"),
                view.controls.get("batch_count").options().stream().map(option -> option.value()).toList());
        view.changes.get("batch_count").accept(UiValue.text("-1"));
        assertEquals("all", port.count());
        assertNull(port.daily.getDrawCounts());
        assertNull(port.daily.getAnimationStyle());
        assertEquals(LotteryAnimationStyle.STRIP, port.animation());
    }

    @Test
    public void explicitServerCountsCannotBeExpandedByTheLegacyPort() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.daily.setDrawCounts(List.of(7, 10));
        port.count = "7";
        var session = new LotteryUiSession(port, () -> true);
        var view = new RecordingUiView();
        session.bind(view);
        session.tick();
        port.accept();
        session.tick();
        assertFalse(view.enabled.get("single_slot"));
        view.actions.get("single").run();
        assertFalse(view.enabled.get("confirm_panel"));
        view.changes.get("batch_count").accept(UiValue.text("12"));
        assertEquals("7", port.count());
        view.changes.get("batch_count").accept(UiValue.text("10"));
        assertEquals("10", port.count());
        assertEquals(List.of(7, 10), port.daily.getDrawCounts());
        assertTrue(port.draws.isEmpty());
    }

    @Test
    public void confirmationRejectsChangedFeesBeforeAndAfterARequote() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.prepare();
        port.payment.tick();
        port.accept();
        port.payment.tick();
        port.payment.clickConfirmed(5, "xpPoints(3)");
        assertTrue(port.draws.isEmpty());
        port.millis = 3000;
        port.payment.tick();
        assertTrue(port.payment.isRefreshing());
        var background = port.quotes.get(port.quotes.size() - 1);
        port.payment.clickConfirmed(1, "xpPoints(2)");
        var explicit = port.quotes.get(port.quotes.size() - 1);
        assertEquals(1, explicit.getCount());
        assertFalse(port.requests.accept(new LotteryQuote(background, LotteryQuote.Status.READY, 1, 12,
                "xpPoints(2)", UUID.randomUUID().toString())));
        assertTrue(port.requests.accept(new LotteryQuote(explicit, LotteryQuote.Status.READY, 1, 12,
                "xpPoints(3)", UUID.randomUUID().toString())));
        port.payment.tick();
        assertTrue(port.draws.isEmpty());
        assertEquals("xpPoints(3)", port.payment.summary());
    }

    @Test
    public void matchingConfirmedIntentStillSendsExactlyOneDraw() {
        var port = new LotteryUiOperationHandoffTest.Port();
        port.prepare();
        port.payment.tick();
        port.accept();
        port.payment.tick();
        port.payment.clickConfirmed(1, "xpPoints(2)");
        port.accept();
        port.payment.tick();
        port.payment.tick();
        assertEquals(1, port.draws.size());
        LotteryDrawRequest sent = port.draws.get(0);
        assertEquals(1, sent.getRequest().getCount());
    }

    @Test
    public void enumWireOrderAndLegacyChestFallbackStayStable() {
        assertEquals(List.of("STRIP", "CARDS", "ROULETTE", "INSTANT", "ENVELOPE", "FAN", "BOUNCE", "CHEST", "FLIP"),
                java.util.Arrays.stream(LotteryAnimationStyle.values()).map(Enum::name).toList());
        assertEquals(LotteryAnimationStyle.ENVELOPE, LotteryAnimationStyle.CHEST.presentationStyle());
        assertFalse(LotteryAnimationStyle.editorChoices(LotteryAnimationStyle.CHEST).contains(LotteryAnimationStyle.CHEST));
    }

    @Test
    public void sourceAwareArtworkAndNativePickerLayoutRemainAvailable() throws Exception {
        String artwork = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/client/gui/lottery/LotteryArtwork.java"), StandardCharsets.UTF_8);
        assertTrue(artwork.contains("ItemUtils.deserializeItemStack(pool.getArtworkItem())"));
        assertTrue(artwork.contains("public void render("));
        assertTrue(artwork.contains("public static DropdownOption option("));
        String nativePage = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/screen/LotteryScreen.java"), StandardCharsets.UTF_8);
        assertTrue(nativePage.contains("poolSelect = dropdown(layout.pool())"));
        assertTrue(nativePage.contains("pool.getDrawCounts() == null || batchCount() != 1"));
        String form = Files.readString(Path.of("src/main/java/xin/vanilla/sakura/client/gui/LotteryPoolForm.java"), StandardCharsets.UTF_8);
        assertTrue(form.contains("existing.getAnimationStyle() == null ? \"legacy\""));
        assertTrue(form.contains("ItemStack"));
        assertTrue(form.contains("ArtworkDraft"));
    }
}
