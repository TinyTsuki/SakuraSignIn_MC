package xin.vanilla.sakura.data.lottery;

import lombok.Value;

@Value
public class LotteryQuote {
    public enum Status { READY, STALE_GENERATION, FORBIDDEN, POOL_NOT_FOUND, PAYMENT_UNAVAILABLE }
    LotteryQuoteRequest request;
    Status status;
    long generation;
    int availableCount;
    String paymentSummary;
    String quoteToken;

    public LotteryQuote(LotteryQuoteRequest request, Status status, long generation, int availableCount,
                        String paymentSummary, String quoteToken) {
        if (request == null || status == null || generation < 0 || availableCount < 0 || availableCount > 100)
            throw new IllegalArgumentException("Invalid lottery quote");
        this.paymentSummary = LotteryProtocolLimits.text(paymentSummary, 4096);
        if (status == Status.READY) {
            if (generation != request.getGeneration() || availableCount < 1
                    || request.getCount() > availableCount) throw new IllegalArgumentException("Invalid ready quote");
            LotteryProtocolLimits.token(quoteToken);
        } else if (availableCount != 0 || !"".equals(quoteToken)) {
            throw new IllegalArgumentException("Failed quote cannot authorize payment");
        }
        this.request = request; this.status = status; this.generation = generation;
        this.availableCount = availableCount; this.quoteToken = quoteToken;
    }

    public boolean isReady() { return status == Status.READY; }
}
