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
    private int quoteCount = 1;
    private java.util.List<Integer> allowed = java.util.List.of(1);

    private record DrawIntent(int count, String confirmedFee) {
    }

    private DrawIntent intent;
    private long generation = -1, lastRequestAt, windowSampleAt;
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
        select(poolId, count, true);
    }

    public void select(String poolId, int count, boolean singleAllowed) {
        select(poolId, singleAllowed && count != 1 ? java.util.List.of(count, 1) : java.util.List.of(count));
    }

    public void select(String poolId, java.util.List<Integer> counts) {
        if (this.poolId.equals(poolId) && allowed.equals(counts)) return;
        if (!this.poolId.equals(poolId)) summary = null;
        this.poolId = poolId;
        allowed = java.util.List.copyOf(counts);
        count = counts.isEmpty() ? 1 : counts.get(0);
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
            quoteCount = count;
            needsQuote = true;
            refreshing = false;
        }
        if (poolId.isEmpty() || generation < 0 || isDrawing()) return;
        if (needsQuote) {
            request(quoteCount, false);
            return;
        }
        LotteryQuote quote = requests.getQuote();
        if (quote != null && quote != seen && quote.getRequest().getPoolId().equals(poolId)) {
            seen = quote;
            windowSampleAt = millis.getAsLong();
            if (quote.getGeneration() == generation && (quote.isReady()
                    || quote.getStatus() == LotteryQuote.Status.PAYMENT_UNAVAILABLE
                    || quote.getStatus() == LotteryQuote.Status.POOL_CLOSED))
                summary = quote.getPaymentSummary();
            if (intent != null) {
                DrawIntent requested = intent;
                intent = null;
                if (quote.isReady() && quote.getRequest().getCount() == requested.count()
                        && (requested.confirmedFee() == null || requested.confirmedFee().equals(quote.getPaymentSummary())))
                    draw(requested.count());
            }
            int smallest = allowed.stream().mapToInt(Integer::intValue).min().orElse(count);
            if (quote.getStatus() == LotteryQuote.Status.PAYMENT_UNAVAILABLE && quote.getRequest().getCount() != smallest) {
                quoteCount = smallest;
                request(smallest, false);
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

    public void click(int requested) {
        if (!canDraw(requested)) return;
        startDraw(requested, null);
    }

    public void clickConfirmed(int requested, String fee) {
        LotteryQuote quote = requests.getQuote();
        if (fee == null || !canOfferDraw(requested) || !fee.equals(quote.getPaymentSummary())) return;
        startDraw(requested, fee);
    }

    private void startDraw(int requested, String fee) {
        LotteryQuoteRequest pending = requests.getPending();
        if (pending == null && requests.getQuote().getRequest().getCount() == requested) draw(requested);
        else {
            intent = new DrawIntent(requested, fee);
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
        return canDraw(requested) || isRefreshing() && permits(requests.getQuote(), requested);
    }

    public boolean isRefreshing() {
        return refreshing && !needsQuote && !isDrawing() && intent == null && requests.getPending() != null
                && seen != null && seen == requests.getQuote() && seen.getGeneration() == requests.getGeneration()
                && millis.getAsLong() - windowSampleAt < 2 * REFRESH_MILLIS;
    }

    private boolean permits(LotteryQuote quote, int requested) {
        return quote != null && quote.isReady() && quote.getGeneration() == requests.getGeneration()
                && quote.getRequest().getPoolId().equals(poolId)
                && allowed.contains(requested)
                && (quote.getRequest().getCount() == requested || requested > 0 && requested <= quote.getAvailableCount());
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

    public long windowMinutes() {
        if (seen == null || seen.getRemainingWindowMillis() == 0) return 0;
        long remaining = seen.getRemainingWindowMillis() - Math.max(0, millis.getAsLong() - windowSampleAt);
        return remaining <= 0 ? 0 : (remaining - 1) / 60_000 + 1;
    }

    public boolean opensNext() {
        return seen != null && seen.isOpensNext();
    }

    public LotteryQuote quote() {
        return requests.getQuote();
    }
}
