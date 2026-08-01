package xin.vanilla.sakura.internal.forge.player;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.stats.Stats;
/** Reads the vanilla lifetime play-time statistic on Forge 1.16.5. */
public final class ForgePlayerOnlineTime {
    private ForgePlayerOnlineTime() {
    }

    public static int playTicks(Object player) {
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        return serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_ONE_MINUTE));
    }
}
