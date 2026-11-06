package xin.vanilla.sakura.reward.lottery;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class LotteryCommandQueueTest {
    @Test
    public void delaysAndDebouncesWhileDroppingStaleConnections() {
        var queue = new LotteryCommandQueue<String>(3, 2);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        assertTrue(queue.offer(first, "first", 10));
        assertFalse(queue.offer(first, "duplicate", 11));
        assertTrue(queue.offer(second, "second", 11));
        assertFalse(queue.offer(UUID.randomUUID(), "over capacity", 11));
        assertNull(queue.poll(12, value -> true));
        assertNull(queue.poll(13, "second"::equals));
        assertEquals("second", queue.poll(14, "second"::equals));
        assertNull(queue.poll(15, value -> true));
        assertTrue(queue.offer(first, "shutdown", 15));
        queue.clear();
        assertNull(queue.poll(18, value -> true));
    }
}
