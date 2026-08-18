package xin.vanilla.sakura.reward.lottery;

import lombok.Value;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 服务端暂存已确定但尚未由客户端动画确认的抽奖。 */
public final class PendingLotteryDraws {
    private static final long EXPIRES_MS = 10 * 60 * 1000L;
    private static final Map<UUID, Pending> VALUES = new HashMap<>();

    private PendingLotteryDraws() {
    }

    public static synchronized Pending get(ServerPlayer player) {
        Pending pending = VALUES.get(player.getUUID());
        if (pending != null && System.currentTimeMillis() - pending.createdAt > EXPIRES_MS) {
            VALUES.remove(player.getUUID());
            return null;
        }
        return pending;
    }

    public static synchronized Pending put(ServerPlayer player,
                                           LotteryDrawBatchResult result) {
        Pending pending = new Pending(UUID.randomUUID().toString(), result,
                System.currentTimeMillis());
        VALUES.put(player.getUUID(), pending);
        return pending;
    }

    public static synchronized LotteryDrawBatchResult claim(ServerPlayer player,
                                                             String token) {
        Pending pending = get(player);
        if (pending == null || !pending.token.equals(token)) return null;
        VALUES.remove(player.getUUID());
        return LotteryRewardService.claimPrepared(player, pending.result);
    }

    @Value
    public static class Pending {
        String token;
        LotteryDrawBatchResult result;
        long createdAt;
    }
}
