package xin.vanilla.sakura.network;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.*;
import java.util.UUID;
import static org.junit.Assert.*;

public class LotteryClientRequestsTest {
    private static final UUID SESSION = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String TOKEN = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test public void staleOutOfOrderAndForeignSessionQuotesAreIgnored() {
        LotteryClientRequests state = new LotteryClientRequests(() -> SESSION);
        state.observeGeneration(7);
        LotteryQuoteRequest old = state.request("daily", 1);
        LotteryQuoteRequest current = state.request("daily", 5);
        assertFalse(state.accept(ready(old)));
        assertFalse(state.accept(ready(new LotteryQuoteRequest(UUID.randomUUID(), current.getRequestId(), 7, "daily", 5))));
        assertTrue(state.accept(ready(current)));
        assertNotNull(state.draw("daily", 5));
        assertNull(state.draw("daily", 1));
        state.observeGeneration(8);
        assertNull(state.draw("daily", 5));
        assertFalse(state.observeGeneration(6));
    }

    @Test public void clearChangesSessionAndRejectsDelayedResponses() {
        final UUID[] session = {SESSION};
        LotteryClientRequests state = new LotteryClientRequests(() -> session[0]);
        state.observeGeneration(7);
        LotteryQuoteRequest request = state.request("daily", 1);
        session[0] = UUID.randomUUID();
        state.clear();
        assertFalse(state.accept(ready(request)));
        assertEquals(-1, state.getGeneration());
        assertNull(state.draw("daily", 1));
    }

    @Test public void pendingRefreshCannotAuthorizeDraw() {
        LotteryClientRequests state = new LotteryClientRequests(() -> SESSION);
        state.observeGeneration(7);
        assertTrue(state.accept(ready(state.request("daily", 1))));
        state.request("daily", 1);
        assertNull(state.draw("daily", 1));
    }
    @Test public void newQuoteCannotAuthorizeAnotherDrawWhilePreviousOperationIsUnconfirmed() {
        LotteryClientRequests state = new LotteryClientRequests(() -> SESSION);
        state.observeGeneration(7);
        state.accept(ready(state.request("daily", 1)));
        assertNotNull(state.draw("daily", 1));
        LotteryQuoteRequest next = state.request("daily", 1);
        if (next != null) state.accept(ready(next));
        assertNull(state.draw("daily", 1));
    }

    @Test public void repeatedRevealsStayIdempotentUntilClientSessionIsCleared() throws Exception {
        LotteryClientRequests state = new LotteryClientRequests(() -> SESSION);
        java.lang.reflect.Method accept = LotteryClientRequests.class.getMethod("acceptReveal", String.class);
        String first = SESSION + "-1", second = SESSION + "-2";
        assertEquals(true, accept.invoke(state, first));
        assertEquals(false, accept.invoke(state, first));
        state.finishDraw(first);
        state.observeGeneration(10);
        assertEquals(true, accept.invoke(state, second));
        assertEquals(false, accept.invoke(state, first));
        assertEquals(false, accept.invoke(state, second));
        state.clear();
        assertEquals(true, accept.invoke(state, first));
    }

    @Test public void revealHistoryIsBoundedAndRejectsEmptyIdentifiers() throws Exception {
        LotteryClientRequests state = new LotteryClientRequests(() -> SESSION);
        java.lang.reflect.Method accept = LotteryClientRequests.class.getMethod("acceptReveal", String.class);
        assertEquals(false, accept.invoke(state, new Object[]{null}));
        assertEquals(false, accept.invoke(state, ""));
        for (int i = 0; i < 101; i++) assertEquals(true, accept.invoke(state, SESSION + "-" + i));
        java.lang.reflect.Field history = LotteryClientRequests.class.getDeclaredField("seenReveals");
        history.setAccessible(true);
        assertEquals(100, ((java.util.Set<?>) history.get(state)).size());
        assertEquals(false, accept.invoke(state, SESSION + "-100"));
    }

    @Test public void clientDisplayEntryUsesTheSessionDeduplicator() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/xin/vanilla/sakura/network/ClientProxy.java")), java.nio.charset.StandardCharsets.UTF_8);
        int begin = source.indexOf("public static void handleLotteryReveal");
        int end = source.indexOf("public static void handleLotteryOutcome", begin);
        assertTrue(source.substring(begin, end).contains("acceptReveal(packet.getOperationId())"));
    }

    private static LotteryQuote ready(LotteryQuoteRequest request) {
        return new LotteryQuote(request, LotteryQuote.Status.READY, request.getGeneration(), 10, "paper", TOKEN);
    }
}
