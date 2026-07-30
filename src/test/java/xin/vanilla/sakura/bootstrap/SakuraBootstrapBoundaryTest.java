package xin.vanilla.sakura.bootstrap;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定 1.16.5 启动结构，后续版本只替换 internal 加载器适配层。
 */
public class SakuraBootstrapBoundaryTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void entryOnlySelectsCommonAndLoaderBootstraps() {
        String entry = source(MAIN.resolve("SakuraSignIn.java"));
        assertTrue(entry.contains("SakuraCommonBootstrap.init()"));
        assertTrue(entry.contains("ForgeSakuraEntrypoint.init()"));
        assertTrue(entry.contains("SakuraClientBootstrap::init"));
        assertFalse(entry.contains("MinecraftForge"));
        assertFalse(entry.contains("FMLJavaModLoadingContext"));
        assertFalse(entry.contains("net.minecraft.client"));
        assertFalse(entry.contains("ExecutorService"));
    }

    @Test
    public void bootstrapsAreIdempotentAndKeepConfigBeforeNetwork() {
        String common = source(MAIN.resolve("SakuraCommonBootstrap.java"));
        String client = source(MAIN.resolve("client/SakuraClientBootstrap.java"));
        String forge = source(MAIN.resolve("internal/forge/ForgeSakuraEntrypoint.java"));

        assertTrue(common.contains("AtomicBoolean"));
        assertTrue(client.contains("AtomicBoolean"));
        assertTrue(forge.contains("AtomicBoolean"));
        assertTrue(common.indexOf("BaniraConfig.register") < common.indexOf("SakuraNetwork.initialize"));
        assertTrue(client.contains("BaniraClientEvents.ModLifecycle.onClientSetup"));
        assertTrue(client.contains("BaniraClientEvents.Client.onClientTick"));
        assertTrue(client.contains("BaniraClientEvents.Player.onClientLoggedOut"));
        assertTrue(client.contains("BaniraInput.registerKey"));
    }

    @Test
    public void forgeTypesStayInsideTheEntryShellAndInternalForge() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = source(path);
                if (!source.contains("net.minecraftforge")) {
                    return;
                }
                String relative = MAIN.relativize(path).toString().replace('\\', '/');
                assertTrue(path + " exposes Forge outside the adapter boundary",
                        "SakuraSignIn.java".equals(relative) || relative.startsWith("internal/forge/"));
            });
        }
    }

    @Test
    public void loaderAdaptersDoNotUseSleepingWorkerThreads() {
        String adapter = source(MAIN.resolve(
                "internal/forge/event/ForgeSakuraGameEventAdapter.java"));
        assertTrue(adapter.contains("BaniraScheduler"));
        assertFalse(adapter.contains("Thread.sleep"));
        assertFalse(adapter.contains("TimeUnit"));
        assertFalse(adapter.contains("ExecutorService"));
    }

    @Test
    public void productionCodeDoesNotDependOnBaniraInternals() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path ->
                    assertFalse(path + " imports Banira internal API",
                            source(path).contains("xin.vanilla.banira.internal")));
        }
    }

    private static String source(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
