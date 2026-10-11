package xin.vanilla.sakura.platform;

import xin.vanilla.sakura.data.lottery.LotteryOperation;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface LotteryOperationRepository {
    LotteryOperation load(UUID playerId, String operationId) throws IOException;
    List<LotteryOperation> list(UUID playerId) throws IOException;
    List<LotteryOperation> incomplete(UUID playerId) throws IOException;
    void write(LotteryOperation operation) throws IOException;
    void flush() throws IOException;
}
