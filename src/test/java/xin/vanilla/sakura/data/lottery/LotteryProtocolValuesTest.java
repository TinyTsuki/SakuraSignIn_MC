package xin.vanilla.sakura.data.lottery;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class LotteryProtocolValuesTest {
    private static final UUID SESSION = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String TOKEN = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test public void requestsBindSessionCounterRevisionPoolAndCount() {
        LotteryQuoteRequest request = new LotteryQuoteRequest(SESSION, 3, 7, "daily", -1);
        assertEquals(SESSION, request.getSession());
        assertEquals(3, request.getRequestId());
        assertEquals(7, request.getGeneration());
        assertEquals("daily", request.getPoolId());
        assertEquals(-1, request.getCount());
        LotteryDrawRequest draw = new LotteryDrawRequest(SESSION, 4, 7, "daily", 5, TOKEN);
        assertEquals(SESSION + "-4", draw.getOperationId());
        assertEquals(TOKEN, draw.getQuoteToken());
    }

    @Test public void invalidRequestValuesCannotReachHandlers() {
        assertThrows(IllegalArgumentException.class, () -> new LotteryQuoteRequest(null, 1, 0, "daily", 1));
        assertThrows(IllegalArgumentException.class, () -> new LotteryQuoteRequest(SESSION, 0, 0, "daily", 1));
        assertThrows(IllegalArgumentException.class, () -> new LotteryQuoteRequest(SESSION, 1, -1, "daily", 1));
        assertThrows(IllegalArgumentException.class, () -> new LotteryQuoteRequest(SESSION, 1, 0, "../daily", 1));
        for (int count : new int[]{-2, 0, 101, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new LotteryQuoteRequest(SESSION, 1, 0, "daily", count));
        assertThrows(IllegalArgumentException.class, () -> new LotteryDrawRequest(SESSION, 1, 0, "daily", 1, ""));
    }

    @Test public void onlyReadyQuotesCarryAuthorization() {
        LotteryQuoteRequest request = new LotteryQuoteRequest(SESSION, 3, 7, "daily", 5);
        LotteryQuote quote = new LotteryQuote(request, LotteryQuote.Status.READY, 7, 10, "5 paper", TOKEN);
        assertTrue(quote.isReady());
        assertThrows(IllegalArgumentException.class, () -> new LotteryQuote(request,
                LotteryQuote.Status.READY, 7, 4, "", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> new LotteryQuote(request,
                LotteryQuote.Status.READY, 6, 10, "", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> new LotteryQuote(request,
                LotteryQuote.Status.PAYMENT_UNAVAILABLE, 7, 0, "", TOKEN));
        assertFalse(new LotteryQuote(request, LotteryQuote.Status.STALE_GENERATION, 8, 0, "", "").isReady());
    }

    @Test public void operationKeysAreCanonicalAndSafeForStorage() {
        LotteryProtocolLimits.operationId(SESSION + "-1");
        for (String id : new String[]{"../result", SESSION + "-0", SESSION + "-01",
                SESSION + "-9223372036854775808", "not-a-session-1"})
            assertThrows(IllegalArgumentException.class, () -> LotteryProtocolLimits.operationId(id));
    }
}
