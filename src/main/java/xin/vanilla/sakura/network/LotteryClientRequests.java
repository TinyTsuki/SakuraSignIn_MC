package xin.vanilla.sakura.network;

import xin.vanilla.sakura.data.lottery.*;
import java.util.UUID;
import java.util.function.Supplier;

public final class LotteryClientRequests {
    private final Supplier<UUID> sessions;
    private UUID session;
    private long sequence, generation = -1;
    private LotteryQuoteRequest pending;
    private LotteryQuote quote;

    public LotteryClientRequests(Supplier<UUID> sessions) {
        this.sessions = sessions; clear();
    }

    public void clear() {
        session = java.util.Objects.requireNonNull(sessions.get()); sequence = 0; generation = -1;
        pending = null; quote = null;
    }

    public long getGeneration() { return generation; }

    public boolean observeGeneration(long value) {
        if (value < 0 || value < generation) return false;
        if (generation != value) { generation = value; pending = null; quote = null; }
        return true;
    }

    public LotteryQuoteRequest request(String poolId, int count) {
        pending = new LotteryQuoteRequest(session, Math.incrementExact(sequence), generation, poolId, count);
        sequence = pending.getRequestId();
        return pending;
    }

    public boolean accept(LotteryQuote incoming) {
        if (incoming == null || pending == null || !pending.equals(incoming.getRequest())
                || incoming.getGeneration() < generation) return false;
        generation = incoming.getGeneration(); pending = null; quote = incoming;
        return true;
    }

    public LotteryDrawRequest draw(String poolId, int count) {
        if (pending != null || quote == null || !quote.isReady() || quote.getGeneration() != generation
                || !quote.getRequest().getPoolId().equals(poolId) || quote.getRequest().getCount() != count) return null;
        LotteryDrawRequest draw = new LotteryDrawRequest(session, Math.incrementExact(sequence), generation,
                poolId, count, quote.getQuoteToken());
        sequence = draw.getRequest().getRequestId(); quote = null;
        return draw;
    }
}
