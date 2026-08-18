package xin.vanilla.sakura.internal.neoforge;

import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.internal.neoforge.event.NeoForgeSakuraGameEventAdapter;
import xin.vanilla.sakura.internal.neoforge.dev.SakuraServerSmokeRunner;
import xin.vanilla.sakura.internal.neoforge.player.NeoForgePlayerSignInDataService;
import xin.vanilla.sakura.internal.neoforge.player.NeoForgePlayerOnlineTime;
import xin.vanilla.sakura.data.time.SakuraOnlineTime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NeoForge 专属能力安装入口，公共代码不直接依赖其实现。
 */
public final class NeoForgeSakuraEntrypoint {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private NeoForgeSakuraEntrypoint() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        SakuraPlayerData.install(NeoForgePlayerSignInDataService.INSTANCE);
        SakuraOnlineTime.install(NeoForgePlayerOnlineTime::playTicks);
        NeoForgeSakuraGameEventAdapter.register();
        SakuraServerSmokeRunner.register();
    }
}
