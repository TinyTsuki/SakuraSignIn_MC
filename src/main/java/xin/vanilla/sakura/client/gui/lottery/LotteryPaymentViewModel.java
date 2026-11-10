package xin.vanilla.sakura.client.gui.lottery;

import xin.vanilla.sakura.data.lottery.LotteryDrawRequest;
import xin.vanilla.sakura.data.lottery.LotteryQuote;
import xin.vanilla.sakura.data.lottery.LotteryQuoteRequest;
import xin.vanilla.sakura.network.LotteryClientRequests;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class LotteryPaymentViewModel {
    private static final long REFRESH_MILLIS = 3000;
    private final LotteryClientRequests requests;
    private final LongSupplier millis;
    private final Consumer<LotteryQuoteRequest> sendQuote;
    private final Consumer<LotteryDrawRequest> sendDraw;
    private String poolId = "", summary;
    private int count = 1;
    private int fallbackCount = 1;
    private int quoteCount = 1;
    private Integer intent;
    private String intentFee;
    private long generation = -1, lastRequestAt, receivedAt;
    private boolean needsQuote, refreshing;
    private LotteryQuote seen;

    public LotteryPaymentViewModel(LotteryClientRequests requests, LongSupplier millis,
                                   Consumer<LotteryQuoteRequest> sendQuote, Consumer<LotteryDrawRequest> sendDraw) {
        this.requests = requests;
        this.millis = millis;
        this.sendQuote = sendQuote;
        this.sendDraw = sendDraw;
    }

    public void select(String poolId, int count) {
        select(poolId, count, 1);
    }

    public void select(String poolId, int count, int fallbackCount) {
        if (this.poolId.equals(poolId) && this.count == count && this.fallbackCount == fallbackCount) return;
        if (!this.poolId.equals(poolId)) summary = null;
        this.poolId = poolId;
        this.count = count;
        this.fallbackCount = fallbackCount;
        quoteCount = count;
        intent = null;
        seen = null;
        needsQuote = true;
        refreshing = false;
    }

    public void tick() {
        if (generation != requests.getGeneration()) {
            generation = requests.getGeneration();
            intent = null;
            seen = null;
            summary = null;
            refreshing = false;
            quoteCount = count;
            needsQuote = true;
        }
        if (poolId.isEmpty() || generation < 0 || isDrawing()) return;
        if (needsQuote) {
            request(quoteCount, false);
            return;
        }
        LotteryQuote quote = requests.getQuote();
        if (quote != null && quote != seen && quote.getRequest().getPoolId().equals(poolId)) {
            seen = quote;
            receivedAt = millis.getAsLong();
            if (quote.isReady()) summary = quote.getPaymentSummary();
            if (intent != null) {
                int requested = intent;
                intent = null;
                if (quote.isReady() && quote.getRequest().getCount() == requested
                        && intentFee.equals(quote.getPaymentSummary())) draw(requested);
            }
            if (quote.getStatus() == LotteryQuote.Status.PAYMENT_UNAVAILABLE
                    && quote.getRequest().getCount() != fallbackCount) {
                quoteCount = fallbackCount;
                request(fallbackCount, false);
                return;
            }
        }
        if (!isDrawing() && millis.getAsLong() - lastRequestAt >= REFRESH_MILLIS) {
            intent = null;
            request(quoteCount, quote != null && quote == seen);
        }
    }

    private void request(int requested, boolean background) {
        LotteryQuoteRequest request = requests.request(poolId, requested);
        if (request != null) {
            needsQuote = false;
            refreshing = background;
            lastRequestAt = millis.getAsLong();
            sendQuote.accept(request);
        }
    }

    public void clickConfirmed(int requested, String fee) {
        LotteryQuote quote = requests.getQuote();
        if (fee == null || !canOfferDraw(requested) || !fee.equals(quote.getPaymentSummary())) return;
        click(requested);
    }

    public void click(int requested) {
        if (!canOfferDraw(requested)) return;
        LotteryQuote quote = requests.getQuote();
        LotteryQuoteRequest pending = requests.getPending();
        if (pending == null && quote.getRequest().getCount() == requested) draw(requested);
        else {
            intent = requested;
            intentFee = quote.getPaymentSummary();
            if (pending == null || pending.getCount() != requested) request(requested, false);
        }
    }

    private void draw(int requested) {
        LotteryDrawRequest request = requests.draw(poolId, requested);
        if (request != null) {
            intent = null;
            sendDraw.accept(request);
        }
    }

    public String summary() {
        return summary;
    }

    public boolean canDraw(int requested) {
        LotteryQuote quote = requests.getQuote();
        return !needsQuote && !isDrawing() && intent == null && requests.getPending() == null
                && millis.getAsLong() - lastRequestAt < REFRESH_MILLIS
                && permits(quote, requested);
    }

    public boolean canOfferDraw(int requested) {
        return canDraw(requested) || isRefreshing() && permits(seen, requested);
    }

    public boolean isWaitingForQuote() {
        return isPending() && !isRefreshing();
    }

    public boolean isRefreshing() {
        return refreshing && !needsQuote && !isDrawing() && intent == null && requests.getPending() != null
                && seen != null && seen == requests.getQuote() && seen.getGeneration() == requests.getGeneration()
                && millis.getAsLong() - receivedAt < 2 * REFRESH_MILLIS;
    }

    private boolean permits(LotteryQuote quote, int requested) {
        return quote != null && quote.isReady() && quote.getGeneration() == requests.getGeneration()
                && quote.getRequest().getPoolId().equals(poolId)
                && (quote.getRequest().getCount() == requested
                || requested > 0 && requested <= quote.getAvailableCount());
    }

    public boolean isDrawing() {
        return requests.isDrawing();
    }

    public void cancelIntent() {
        intent = null;
    }

    public boolean isPending() {
        return needsQuote || intent != null || requests.getPending() != null;
    }

    public LotteryQuote quote() {
        return requests.getQuote();
    }

    public long windowMinutes() {
        if (seen == null || seen != requests.getQuote() || seen.getGeneration() != requests.getGeneration()) return 0;
        long remaining = seen.getRemainingWindowMillis() - Math.max(0, millis.getAsLong() - receivedAt);
        return remaining <= 0 ? 0 : (remaining - 1) / 60_000 + 1;
    }

    public boolean opensNext() {
        return windowMinutes() > 0 && seen.isOpensNext();
    }
}
