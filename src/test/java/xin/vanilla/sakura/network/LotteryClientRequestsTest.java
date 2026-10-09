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

    private static LotteryQuote ready(LotteryQuoteRequest request) {
        return new LotteryQuote(request, LotteryQuote.Status.READY, request.getGeneration(), 10, "paper", TOKEN);
    }
}
