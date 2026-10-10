package xin.vanilla.sakura.client.gui.lottery;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.network.LotteryClientRequests;
import java.util.*;
import static org.junit.Assert.*;

public class LotteryPaymentViewModelTest {
    final LotteryClientRequests requests = new LotteryClientRequests(UUID::randomUUID);
    final List<LotteryQuoteRequest> quotes = new ArrayList<>();
    final List<LotteryDrawRequest> draws = new ArrayList<>();
    long millis;
    LotteryPaymentViewModel model() {
        requests.observeGeneration(1);
        return new LotteryPaymentViewModel(requests, () -> millis, quotes::add, draws::add);
    }
    LotteryQuote ready(LotteryQuoteRequest request) {
        return new LotteryQuote(request, LotteryQuote.Status.READY, request.getGeneration(), 10,
                "xpPoints(2)", UUID.randomUUID().toString());
    }
    @Test public void refreshingKeepsConfirmedSummaryButDisablesAuthorization() {
        LotteryPaymentViewModel view = model(); view.select("daily", 5); view.tick();
        requests.accept(ready(quotes.get(0))); view.tick();
        assertEquals("xpPoints(2)", view.summary()); assertTrue(view.canDraw(5));
        millis = 3000; view.tick();
        assertEquals("xpPoints(2)", view.summary()); assertFalse(view.canDraw(5));
        assertEquals(2, quotes.size());
    }
    @Test public void hundredFramesCannotFloodQuoteRequests() {
        LotteryPaymentViewModel view = model(); view.select("daily", 1);
        for(int i=0;i<100;i++){millis=i*10;view.tick();}
        assertEquals(1,quotes.size()); assertTrue(draws.isEmpty());
    }
    @Test public void singleActionFromBatchModeUsesOneCorrelatedQuoteAndDraw() {
        LotteryPaymentViewModel view = model(); view.select("daily",5); view.tick();
        requests.accept(ready(quotes.get(0))); view.tick();
        view.click(1); assertTrue(draws.isEmpty());
        assertEquals(1, quotes.get(1).getCount());
        requests.accept(ready(quotes.get(1))); view.tick(); view.tick();
        assertEquals(1,draws.size()); assertEquals(1,draws.get(0).getRequest().getCount());
        view.click(1); assertEquals(1,draws.size()); assertTrue(view.isDrawing());
    }
    @Test public void changedPoolCancelsUnsentSingleIntentAndOldResponse() {
        LotteryPaymentViewModel view = model(); view.select("daily",5);view.tick();
        requests.accept(ready(quotes.get(0)));view.tick();view.click(1);
        LotteryQuoteRequest previous = quotes.get(1);
        view.select("other",1); view.tick();
        assertFalse(requests.accept(ready(previous)));view.tick();assertTrue(draws.isEmpty());
    }
    @Test public void closeAndGenerationChangeCancelUnsentPayment() {
        LotteryPaymentViewModel view=model();view.select("daily",5);view.tick();
        requests.accept(ready(quotes.get(0)));view.tick();view.click(1);view.cancelIntent();
        requests.accept(ready(quotes.get(1)));view.tick();assertTrue(draws.isEmpty());
        view.click(1); assertEquals(1,draws.size());
        assertFalse(requests.finishDraw(UUID.randomUUID()+"-1"));
        assertTrue(requests.finishDraw(draws.get(0).getOperationId()));
        requests.observeGeneration(2);view.tick();assertFalse(view.canDraw(1));
    }
    @Test public void failureOutcomeClearsWaitingWithoutRetryingDraw() {
        LotteryPaymentViewModel view=model();view.select("daily",1);view.tick();
        requests.accept(ready(quotes.get(0)));view.tick();view.click(1);
        requests.finishDraw(draws.get(0).getOperationId());view.tick();
        assertFalse(view.isDrawing());assertEquals(1,draws.size());
    }
    @Test public void newPoolDoesNotDisplayPreviousPoolCost() {
        LotteryPaymentViewModel view=model();view.select("daily",1);view.tick();
        requests.accept(ready(quotes.get(0)));view.tick();view.select("other",1);
        assertNull(view.summary());assertFalse(view.canDraw(1));
    }
    @Test public void changedRevisionCancelsUnsentSingleIntent() {
        LotteryPaymentViewModel view=model();view.select("daily",5);view.tick();
        requests.accept(ready(quotes.get(0)));view.tick();view.click(1);
        requests.observeGeneration(2);view.tick();
        requests.accept(ready(quotes.get(quotes.size()-1)));view.tick();assertTrue(draws.isEmpty());
    }
    @Test public void expiredDisplayedQuoteCannotAuthorizeClickBeforeNextTick() {
        LotteryPaymentViewModel view=model();view.select("daily",1);view.tick();
        requests.accept(ready(quotes.get(0)));view.tick();millis=3001;
        view.click(1);assertTrue(draws.isEmpty());assertFalse(view.canDraw(1));
    }
    @Test public void unaffordableBatchStillQuotesOneToEnableSingleDraw() {
        LotteryPaymentViewModel view=model();view.select("daily",5);view.tick();
        requests.accept(new LotteryQuote(quotes.get(0),LotteryQuote.Status.PAYMENT_UNAVAILABLE,1,0,"",""));
        view.tick();assertEquals(2,quotes.size());assertEquals(1,quotes.get(1).getCount());
        requests.accept(new LotteryQuote(quotes.get(1),LotteryQuote.Status.READY,1,4,"xpPoints(2)",UUID.randomUUID().toString()));
        view.tick();assertTrue(view.canDraw(1));assertFalse(view.canDraw(5));
    }
}
