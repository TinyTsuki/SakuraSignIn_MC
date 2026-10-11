package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.network.lottery.LotteryPoolReceiver;
import xin.vanilla.sakura.reward.RewardList;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class LotteryPoolTransferTest {
    @Test public void fragmentsReassembleOutOfOrderWithRevisionAndFullMetadata() {
        LotteryPoolSyncPacket source = document(7);
        List<LotteryPoolSyncPacket> parts = source.splitPacket();
        assertTrue(parts.size() > 1);
        Collections.reverse(parts);
        LotteryPoolReceiver receiver = new LotteryPoolReceiver(() -> 0);
        Object connection = new Object(); LotteryPoolSyncPacket complete = null;
        for (LotteryPoolSyncPacket part : parts) {
            assertTrue(part.getFragment().length() <= LotteryProtocolLimits.MAX_FRAGMENT_CHARS);
            assertTrue(LotteryProtocolLimits.bytes(part.getFragment()) <= LotteryProtocolLimits.MAX_FRAGMENT_BYTES);
            LotteryPoolSyncPacket result = receiver.accept(connection, part);
            if (result != null) complete = result;
        }
        assertNotNull(complete); assertEquals(7, complete.getGeneration());
        assertEquals(source.getPools(), complete.getPools());
        assertEquals(0, receiver.size());
    }

    @Test public void identicalTransferIdsCannotMixTwoConnections() {
        List<LotteryPoolSyncPacket> a = document(7).splitPacket();
        List<LotteryPoolSyncPacket> b = document(8).splitPacket();
        b.forEach(part -> part.setId(a.get(0).getId()));
        LotteryPoolReceiver receiver = new LotteryPoolReceiver(() -> 0);
        Object first = new Object(), second = new Object();
        assertNull(receiver.accept(first, a.get(0)));
        assertNull(receiver.accept(second, b.get(1)));
        assertEquals(2, receiver.size());
        LotteryPoolSyncPacket complete = null;
        for (int i = 1; i < a.size(); i++) complete = receiver.accept(first, a.get(i));
        assertNotNull(complete); assertEquals(7, complete.getGeneration());
        assertEquals(1, receiver.size());
    }

    @Test public void duplicateAndExpiredTransfersDoNotPublishPartialData() {
        AtomicLong clock = new AtomicLong();
        LotteryPoolReceiver receiver = new LotteryPoolReceiver(clock::get);
        Object connection = new Object(); List<LotteryPoolSyncPacket> parts = document(7).splitPacket();
        receiver.accept(connection, parts.get(0));
        assertNull(receiver.accept(connection, parts.get(0))); assertEquals(0, receiver.size());
        receiver.accept(connection, parts.get(0));
        clock.set(11_000_000_000L); receiver.expire(); assertEquals(0, receiver.size());
        receiver.clear(); assertEquals(0, receiver.size());
    }

    @Test public void mixedRevisionsAndMalformedHeadersAreRejected() {
        List<LotteryPoolSyncPacket> a = document(7).splitPacket();
        List<LotteryPoolSyncPacket> b = document(8).splitPacket();
        b.forEach(part -> part.setId(a.get(0).getId()));
        assertThrows(IllegalArgumentException.class, () -> a.get(0).mergePackets(Arrays.asList(a.get(0), b.get(1))));
        a.get(0).setTotal(513);
        assertNull(new LotteryPoolReceiver(() -> 0).accept(new Object(), a.get(0)));
    }

    @Test public void decoderCannotAllocateUnboundedFragments() {
        xin.vanilla.sakura.network.TestBaniraPacketBuffer buffer = new xin.vanilla.sakura.network.TestBaniraPacketBuffer();
        buffer.writeUtf(UUID.randomUUID().toString()); buffer.writeInt(513); buffer.writeInt(0);
        buffer.writeLong(0); buffer.writeBoolean(false); buffer.writeUtf("{}");
        assertThrows(IllegalArgumentException.class, () -> new LotteryPoolSyncPacket(buffer));
    }

    @Test public void JSONDepthIsBoundedBeforeGsonObjectParsing() {
        String json = "{\"x\":" + repeat("[", 65) + "0" + repeat("]", 65) + "}";
        assertThrows(IllegalArgumentException.class, () -> LotteryProtocolLimits.json(json, 1_048_576));
        assertThrows(IllegalArgumentException.class, () -> LotteryProtocolLimits.json("{}{}", 1_048_576));
    }

    private static LotteryPoolSyncPacket document(long generation) {
        List<LotteryPool> pools = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LotteryPool pool = new LotteryPool("pool_" + i, "Pool " + i, LotteryLimitPolicy.DAILY, 1, 0, new RewardList());
            pool.setDescription(repeat("\u7231", 2046) + "\ud83c\udf38"); pools.add(pool);
        }
        return new LotteryPoolSyncPacket(pools, generation);
    }
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(); for (int i = 0; i < count; i++) result.append(value); return result.toString();
    }
}
