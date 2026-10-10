package xin.vanilla.sakura.data.lottery;

import lombok.Value;
import java.util.UUID;

@Value
public class LotteryQuoteRequest {
    UUID session;
    long requestId;
    long generation;
    String poolId;
    int count;

    public LotteryQuoteRequest(UUID session, long requestId, long generation, String poolId, int count) {
        if (session == null || requestId < 1 || generation < 0 || poolId == null
                || !poolId.matches("[a-z0-9_.-]{1,64}") || count != -1 && (count < 1 || count > 100))
            throw new IllegalArgumentException("Invalid lottery request");
        this.session = session; this.requestId = requestId; this.generation = generation;
        this.poolId = poolId; this.count = count;
    }
}
