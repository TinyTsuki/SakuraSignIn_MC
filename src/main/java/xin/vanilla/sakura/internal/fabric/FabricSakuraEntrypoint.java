package xin.vanilla.sakura.internal.fabric;

import net.fabricmc.api.ModInitializer;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.api.SakuraPlayerData;
import xin.vanilla.sakura.internal.fabric.event.FabricSakuraGameEventAdapter;
import xin.vanilla.sakura.internal.fabric.dev.SakuraServerSmokeRunner;
import xin.vanilla.sakura.internal.fabric.player.FabricPlayerSignInDataService;
import xin.vanilla.sakura.internal.fabric.player.FabricPlayerOnlineTime;
import xin.vanilla.sakura.data.time.SakuraOnlineTime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fabric 公共入口，只负责安装加载器实现并启动共享业务。
 */
public final class FabricSakuraEntrypoint implements ModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    @Override
    public void onInitialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        SakuraSignIn.bootstrapCommon();
        SakuraPlayerData.install(FabricPlayerSignInDataService.INSTANCE);
        SakuraOnlineTime.install(FabricPlayerOnlineTime::playTicks);
        FabricSakuraGameEventAdapter.register();
        SakuraServerSmokeRunner.register();
    }
}
