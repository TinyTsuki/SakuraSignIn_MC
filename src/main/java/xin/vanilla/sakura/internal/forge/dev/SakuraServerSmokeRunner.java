package xin.vanilla.sakura.internal.forge.dev;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.banira.api.BaniraEnvironment;
import xin.vanilla.banira.api.event.BaniraEvents;

import java.util.concurrent.atomic.AtomicInteger;

/** 显式开发烟测在服务端稳定运行若干 tick 后触发正常关闭。 */
public final class SakuraServerSmokeRunner {
    public static final String ENVIRONMENT_KEY = "SAKURA_SERVER_SMOKE";
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicInteger TICKS_AFTER_START = new AtomicInteger(-1);

    private SakuraServerSmokeRunner() {
    }

    public static void register() {
        if (BaniraEnvironment.isProduction()
                || !"stop-after-start".equalsIgnoreCase(System.getenv(ENVIRONMENT_KEY))) {
            return;
        }
        BaniraEvents.Server.onStarted(event -> TICKS_AFTER_START.set(0));
        BaniraEvents.Server.onTick(event -> {
            int ticks = TICKS_AFTER_START.get();
            if (ticks < 0 || TICKS_AFTER_START.incrementAndGet() < 20) {
                return;
            }
            TICKS_AFTER_START.set(-1);
            MinecraftServer server = event.serverAs(MinecraftServer.class);
            LOGGER.info("Sakura dedicated server smoke PASS");
            server.halt(false);
        });
    }
}
