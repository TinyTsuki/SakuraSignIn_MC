package xin.vanilla.sakura.internal.forge;

import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.internal.forge.event.ForgeSakuraGameEventAdapter;
import xin.vanilla.sakura.internal.forge.player.ForgePlayerSignInDataService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forge 专属能力安装入口，公共代码不直接依赖其实现。
 */
public final class ForgeSakuraEntrypoint {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private ForgeSakuraEntrypoint() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        SakuraPlayerData.install(ForgePlayerSignInDataService.INSTANCE);
        ForgeSakuraGameEventAdapter.register();
    }
}
