package xin.vanilla.sakura.data.lottery;

import lombok.Value;
import java.util.UUID;

@Value
public class LotteryDrawRequest {
    LotteryQuoteRequest request;
    String quoteToken;

    public LotteryDrawRequest(UUID session, long operationId, long generation, String poolId,
                              int count, String quoteToken) {
        this.request = new LotteryQuoteRequest(session, operationId, generation, poolId, count);
        this.quoteToken = LotteryProtocolLimits.token(quoteToken);
    }

    public String getOperationId() {
        return request.getSession() + "-" + request.getRequestId();
    }
}
