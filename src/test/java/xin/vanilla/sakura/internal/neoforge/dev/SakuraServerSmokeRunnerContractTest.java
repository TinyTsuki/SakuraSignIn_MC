package xin.vanilla.sakura.internal.neoforge.dev;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** 锁定专用服务端烟测只能由开发环境的显式变量启用。 */
public class SakuraServerSmokeRunnerContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void dedicatedServerSmokeStopsItselfAfterStartup() throws IOException {
        String runner = read(MAIN.resolve(
                "internal/neoforge/dev/SakuraServerSmokeRunner.java"));
        String entrypoint = read(MAIN.resolve(
                "internal/neoforge/NeoForgeSakuraEntrypoint.java"));

        assertTrue(runner.contains("SAKURA_SERVER_SMOKE"));
        assertTrue(runner.contains("BaniraEnvironment.isProduction()"));
        assertTrue(runner.contains("\"stop-after-start\""));
        assertTrue(runner.contains("BaniraEvents.Server.onStarted"));
        assertTrue(runner.contains("BaniraEvents.Server.onTick"));
        assertTrue(runner.contains("server.halt(false)"));
        assertTrue(runner.contains("Sakura dedicated server smoke PASS"));
        assertTrue(entrypoint.contains("SakuraServerSmokeRunner.register()"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
