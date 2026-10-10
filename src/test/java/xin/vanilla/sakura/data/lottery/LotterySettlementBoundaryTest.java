package xin.vanilla.sakura.data.lottery;

import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class LotterySettlementBoundaryTest {
    @Test public void finalGrantDoesNotReleasePlayerBeforeQuotaIsSaved() {
        LotteryOperation operation = LotteryOperationTest.prepared(1).paying()
                .paid(LotteryOperationTest.receipt()).selectRewards()
                .granting().grantSucceeded().granting().grantSucceeded();
        assertFalse("Quota settlement still pending", operation.isTerminal());
        assertEquals(2, operation.getGrantedCount());
    }
    @Test public void extremeWeightExponentCannotAllocateUnboundedArithmetic() {
        LotteryPool pool = LotteryOperationTest.pool();
        pool.getRewards().get(0).setProbability(new BigDecimal("1e100000000"));
        LotteryOperationTest.reject(() -> LotteryOperation.prepare(LotteryOperationTest.PLAYER,
                LotteryOperationTest.prepared(1).getRequest(), pool, 2, 1, 100,
                "dddddddd-dddd-dddd-dddd-dddddddddddd"));
    }
    @Test public void paidRollbackRequiresPersistableWriteAheadIntent() {
        LotteryOperationTest.reject(() -> LotteryOperationTest.prepared(1).paying()
                .paid(LotteryOperationTest.receipt()).rolledBack());
    }
}
