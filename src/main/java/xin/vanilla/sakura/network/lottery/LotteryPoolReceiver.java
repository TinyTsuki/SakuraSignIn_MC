package xin.vanilla.sakura.network.lottery;

import xin.vanilla.sakura.data.lottery.LotteryProtocolLimits;
import xin.vanilla.sakura.network.packet.LotteryPoolSyncPacket;
import java.util.*;
import java.util.function.LongSupplier;

public final class LotteryPoolReceiver {
    private static final long TTL = 10_000_000_000L;
    private final LongSupplier clock;
    private final Map<Object, Assembly> assemblies = new IdentityHashMap<>();
    private int bufferedBytes;

    public LotteryPoolReceiver(LongSupplier clock) { this.clock = clock; }

    public synchronized LotteryPoolSyncPacket accept(Object connection, LotteryPoolSyncPacket packet) {
        expire();
        if (connection == null || packet == null || !packet.hasValidHeader()) return null;
        Assembly assembly = assemblies.get(connection);
        if (assembly == null || !assembly.id.equals(packet.getId())) {
            if (assembly != null && packet.getGeneration() < assembly.generation) return null;
            if (assembly == null && assemblies.size() >= 32) return null;
            remove(connection); assembly = new Assembly(packet, clock.getAsLong()); assemblies.put(connection, assembly);
        }
        if (assembly.generation != packet.getGeneration() || assembly.total != packet.getTotal()
                || assembly.upload != packet.isConfigurationUpload()
                || assembly.parts.containsKey(packet.getSort())) {
            remove(connection); return null;
        }
        int bytes = LotteryProtocolLimits.bytes(packet.getFragment());
        if (bytes > LotteryProtocolLimits.MAX_FRAGMENT_BYTES
                || assembly.bytes > packet.maximumBytes() - bytes || bufferedBytes > 16_777_216 - bytes) {
            remove(connection); return null;
        }
        assembly.bytes += bytes; bufferedBytes += bytes; assembly.parts.put(packet.getSort(), packet);
        if (assembly.parts.size() != assembly.total) return null;
        remove(connection);
        return packet.mergePackets(new ArrayList<>(assembly.parts.values()));
    }

    public synchronized void expire() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<Object, Assembly>> iterator = assemblies.entrySet().iterator();
        while (iterator.hasNext()) {
            Assembly value = iterator.next().getValue();
            if (now - value.started >= TTL) { bufferedBytes -= value.bytes; iterator.remove(); }
        }
    }
    public synchronized void remove(Object connection) {
        Assembly value = assemblies.remove(connection); if (value != null) bufferedBytes -= value.bytes;
    }
    public synchronized void clear() { assemblies.clear(); bufferedBytes = 0; }
    public synchronized int size() { return assemblies.size(); }

    private static final class Assembly {
        final String id; final long generation, started; final int total;
        final boolean upload;
        final Map<Integer, LotteryPoolSyncPacket> parts = new TreeMap<>(); int bytes;
        Assembly(LotteryPoolSyncPacket packet, long now) {
            id = packet.getId(); generation = packet.getGeneration(); total = packet.getTotal(); started = now;
            upload = packet.isConfigurationUpload();
        }
    }
}
