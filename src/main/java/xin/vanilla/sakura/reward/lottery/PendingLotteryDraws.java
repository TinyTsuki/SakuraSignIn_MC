package xin.vanilla.sakura.reward.lottery;

import lombok.Value;
import java.util.*;

public final class PendingLotteryDraws {
    private static final long EXPIRES_MS = 10 * 60 * 1000L;
    private static final UUID COMMAND_SESSION = UUID.randomUUID();
    private static long sequence;
    private static final Map<UUID, Pending> VALUES = new HashMap<>();

    private PendingLotteryDraws() { }

    public static synchronized Pending get(UUID player) {
        expire(); return VALUES.get(player);
    }

    public static synchronized Pending put(UUID player) {
        expire();
        if (!VALUES.containsKey(player) && VALUES.size() >= 256)
            throw new IllegalArgumentException("Too many pending lottery displays");
        Pending pending = new Pending(COMMAND_SESSION + "-" + Math.incrementExact(sequence),
                UUID.randomUUID().toString(), System.currentTimeMillis());
        sequence++;
        VALUES.put(player, pending); return pending;
    }

    public static synchronized boolean acknowledge(UUID player, String operationId, String token) {
        Pending pending = get(player);
        if (pending == null || !pending.operationId.equals(operationId) || !pending.token.equals(token)) return false;
        VALUES.remove(player); return true;
    }

    public static synchronized void remove(UUID player) { VALUES.remove(player); }
    public static synchronized void clear() { VALUES.clear(); }
    private static void expire() {
        long now = System.currentTimeMillis();
        VALUES.entrySet().removeIf(entry -> now - entry.getValue().createdAt >= EXPIRES_MS);
    }

    @Value public static class Pending {
        String operationId;
        String token;
        long createdAt;
    }
}
