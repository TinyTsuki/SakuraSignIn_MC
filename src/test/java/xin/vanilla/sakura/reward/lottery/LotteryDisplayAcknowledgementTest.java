package xin.vanilla.sakura.reward.lottery;

import org.junit.After;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class LotteryDisplayAcknowledgementTest {
    @After public void clear() { PendingLotteryDraws.clear(); }
    @Test public void acknowledgementOnlyRemovesMatchingPlayersDisplayReceipt() {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        PendingLotteryDraws.Pending pending = PendingLotteryDraws.put(player);
        assertFalse(PendingLotteryDraws.acknowledge(other, pending.getOperationId(), pending.getToken()));
        assertFalse(PendingLotteryDraws.acknowledge(player, pending.getOperationId(), UUID.randomUUID().toString()));
        assertNotNull(PendingLotteryDraws.get(player));
        assertTrue(PendingLotteryDraws.acknowledge(player, pending.getOperationId(), pending.getToken()));
        assertFalse(PendingLotteryDraws.acknowledge(player, pending.getOperationId(), pending.getToken()));
        assertNull(PendingLotteryDraws.get(player));
    }
    @Test public void replacementDisplayCannotBeClosedByPreviousToken() {
        UUID player = UUID.randomUUID();
        PendingLotteryDraws.Pending previous = PendingLotteryDraws.put(player);
        PendingLotteryDraws.Pending current = PendingLotteryDraws.put(player);
        assertFalse(PendingLotteryDraws.acknowledge(player, previous.getOperationId(), previous.getToken()));
        assertSame(current, PendingLotteryDraws.get(player));
        PendingLotteryDraws.remove(player); assertNull(PendingLotteryDraws.get(player));
    }
}
