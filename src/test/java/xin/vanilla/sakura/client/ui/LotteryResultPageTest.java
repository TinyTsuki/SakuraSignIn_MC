package xin.vanilla.sakura.client.ui;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.api.client.ui.UiRowLayout;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.network.packet.LotteryRevealPacket;
import xin.vanilla.sakura.reward.Reward;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class LotteryResultPageTest {
    @org.junit.Rule
    public final xin.vanilla.sakura.test.NativeClientRule nativeClient = new xin.vanilla.sakura.test.NativeClientRule();

    @Rule
    public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();

    @BeforeClass
    public static void bootstrap() {
        SignInUiParityTest.bootstrap();
    }

    private static LotteryRevealPacket packet(int count) {
        return new LotteryRevealPacket(UUID.randomUUID() + "-1", UUID.randomUUID().toString(), "Pool name",
                Collections.nCopies(count, Reward.getDefault()), List.of(Reward.getDefault()), LotteryPreviewMode.ALL);
    }

    @Test
    public void approvedTemplateUsesLocalStylesAndSharedHeader() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery-result.html"), StandardCharsets.UTF_8);
        for (String id : List.of("brand", "close", "subtitle", "section", "result_count", "result_area", "result_single", "tile47"))
            assertTrue(id, html.contains("id=\"" + id + "\""));
        assertTrue(html.contains("href=\"lottery-result.css\""));
        assertFalse(html.contains("href=\"lottery.css\""));
        assertFalse(html.contains("<script"));
    }

    @Test
    public void stylesKeepTheScopedBudgetAfterWindowsCheckout() throws Exception {
        int bytes = 0, lotteryPageBytes = 0;
        for (var entry : UiBoundaryChecks.ownResources("sakura_sign_in").entrySet()) {
            if (!entry.getKey().endsWith(".css")) continue;
            int size = new String(entry.getValue(), StandardCharsets.UTF_8).replace("\r\n", "\n")
                    .replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8).length;
            bytes += size;
            if (entry.getKey().equals("lottery.css")) lotteryPageBytes = size;
        }
        assertTrue("Expected lottery stylesheet in the Windows budget check", lotteryPageBytes > 0);
        assertTrue(bytes <= UiBoundaryChecks.stylesBudget(lotteryPageBytes));
    }

    @Test
    public void resultCardSelectorsMatchAnonymousRowNodes() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/assets/apricityui/apricity/sakura_sign_in/lottery-result.css"));
        for (int child : new int[]{1, 4, 5})
            assertTrue(css.contains(".result-card>.paragraph:nth-child(" + child + ")"));
        assertFalse(css.contains("[id$="));
    }

    @Test
    public void headerAndSingleResultKeepOriginalClaimLifecycle() {
        int[] claims = {0};
        var session = new LotteryRevealUiSession(packet(1), LotteryAnimationStyle.INSTANT, () -> 0, () -> true, (key, scan) -> key == 69, () -> claims[0]++);
        var view = new RecordingUiView();
        session.bind(view);
        assertNotNull(view.icons.get("brand"));
        assertNotNull(view.actions.get("close"));
        assertTrue(view.enabled.getOrDefault("result_single", false));
        assertFalse(view.enabled.getOrDefault("content", true));
        assertEquals(0, claims[0]);
        session.tick();
        assertEquals(1, claims[0]);
        view.actions.get("close").run();
        assertEquals(1, view.closes);
        session.close(UiCloseReason.USER_CLOSE);
        assertEquals(1, claims[0]);
    }

    @Test
    public void hundredResultsKeepTheTailAndReadOnlyCardContent() {
        var session = new LotteryRevealUiSession(packet(100), LotteryAnimationStyle.INSTANT, () -> 0, () -> true, (key, scan) -> key == 69, () -> {
        });
        var view = new RecordingUiView();
        session.bind(view);
        assertEquals(34, view.providers.get("fields").size());
        assertTrue(view.rows instanceof UiRowLayout);
        assertEquals(60, ((UiRowLayout) view.rows).rowHeight());
        var row = new RecordingUiView();
        view.providers.get("fields").bind(row, 33);
        assertEquals("", row.texts.get("index").toString());
        assertTrue(view.providers.get("fields").key(33).endsWith(":99"));
        assertNotNull(row.texts.get("type"));
        assertTrue(row.actions.isEmpty());
        assertEquals(40, view.limit);
    }

    @Test
    public void responsiveColumnsAndNativeHandoffPreserveScrollAnchor() {
        var session = new LotteryRevealUiSession(packet(100), LotteryAnimationStyle.INSTANT, () -> 0, () -> true, (key, scan) -> key == 69, () -> {
        });
        var view = new RecordingUiView();
        view.state.rowPosition(12);
        session.bind(view);
        assertEquals(4, view.state.rowPosition(), 0);
        view.geometry.put("result_area", new ScreenCoordinate(0, 0, 300, 200));
        session.tick();
        assertEquals(50, view.providers.get("fields").size());
        assertEquals(6, view.state.rowPosition(), 0);
        session.detach(UiDetachReason.SWITCH);
        assertEquals(12, view.state.rowPosition(), 0);
        var nativeView = new RecordingUiView();
        nativeView.nativeView = true;
        nativeView.state.rowPosition(12);
        session.bind(nativeView);
        assertTrue(nativeView.actions.isEmpty());
        session.detach(UiDetachReason.SWITCH);
    }

    @Test
    public void disconnectedActionsCannotClaimAndStaleCloseCannotReachNewView() {
        boolean[] connected = {true};
        int[] claims = {0};
        var session = new LotteryRevealUiSession(packet(1), LotteryAnimationStyle.STRIP, () -> 0, () -> connected[0], (key, scan) -> key == 69, () -> claims[0]++);
        var view = new RecordingUiView();
        session.bind(view);
        Runnable close = view.actions.get("close");
        assertNotNull(close);
        connected[0] = false;
        session.tick();
        assertEquals(Boolean.FALSE, view.enabled.get("action"));
        assertEquals(Boolean.FALSE, view.enabled.get("close"));
        close.run();
        assertEquals(0, claims[0]);
        assertEquals(0, view.closes);
        connected[0] = true;
        session.detach(UiDetachReason.RELOAD);
        var next = new RecordingUiView();
        session.bind(next);
        close.run();
        assertEquals(0, next.closes);
    }

    @Test
    public void presentationFramesPadWholeTilesAndNeverMarkStripWinner() throws Exception {
        var method = Arrays.stream(LotteryRevealUiSession.class.getMethods()).filter(m -> m.getName().equals("presentationFrame")).findFirst().orElse(null);
        assertNotNull("AUI frame needs full-card insets", method);
        for (double width : new double[]{110, 300, 536})
            for (double height : new double[]{60, 140, 188}) {
                @SuppressWarnings("unchecked") var wheel = (List<LotteryRevealUiSession.Slot>) method.invoke(null, LotteryAnimationStyle.ROULETTE, 6500L, width, height, 12);
                for (var s : wheel)
                    assertTrue(s.x() - 4 >= 10 && s.y() - 8 >= 10 && s.x() + 20 <= width - 10 && s.y() + 24 <= height - 10);
                @SuppressWarnings("unchecked") var strip = (List<LotteryRevealUiSession.Slot>) method.invoke(null, LotteryAnimationStyle.STRIP, 0L, width, height, 12);
                assertTrue(strip.stream().noneMatch(LotteryRevealUiSession.Slot::active));
            }
    }
}
