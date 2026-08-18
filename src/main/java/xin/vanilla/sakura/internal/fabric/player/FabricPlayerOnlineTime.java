package xin.vanilla.sakura.internal.fabric.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
/** 读取原版累计在线时长统计，不额外维护每 tick 数据。 */
public final class FabricPlayerOnlineTime {
    private FabricPlayerOnlineTime() {
    }

    public static int playTicks(Object player) {
        ServerPlayer serverPlayer = (ServerPlayer) player;
        return serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_ONE_MINUTE));
    }
}
