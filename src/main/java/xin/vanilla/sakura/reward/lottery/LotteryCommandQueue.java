package xin.vanilla.sakura.reward.lottery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

final class LotteryCommandQueue<T> {
    private record Entry<T>(T value, long dueAt) {
    }

    private final Map<UUID, Entry<T>> entries = new LinkedHashMap<>();
    private final long delay;
    private final int capacity;

    LotteryCommandQueue(long delay, int capacity) {
        this.delay = delay;
        this.capacity = capacity;
    }

    boolean offer(UUID player, T value, long now) {
        if (entries.size() >= capacity || entries.containsKey(player)) return false;
        entries.put(player, new Entry<>(value, now + delay));
        return true;
    }

    T poll(long now, Predicate<T> current) {
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry<T> entry = iterator.next();
            if (!current.test(entry.value)) {
                iterator.remove();
                continue;
            }
            if (now - entry.dueAt < 0) continue;
            iterator.remove();
            return entry.value;
        }
        return null;
    }

    void clear() {
        entries.clear();
    }
}
