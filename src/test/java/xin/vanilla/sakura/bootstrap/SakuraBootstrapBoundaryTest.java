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
        assertTrue(entry.contains("bootstrapCommon()"));
        assertFalse(entry.contains("FabricSakuraEntrypoint"));
        assertFalse(entry.contains("SakuraClientBootstrap"));
        assertFalse(entry.contains("MinecraftForge"));
        assertFalse(entry.contains("FMLJavaModLoadingContext"));
        assertFalse(entry.contains("net.minecraft.client"));
        assertFalse(entry.contains("ExecutorService"));
    }

    @Test
    public void bootstrapsAreIdempotentAndKeepConfigBeforeNetwork() {
        String common = source(MAIN.resolve("SakuraSignIn.java"));
        String client = source(MAIN.resolve("client/SakuraClientBootstrap.java"));
        String fabric = source(MAIN.resolve("internal/fabric/FabricSakuraEntrypoint.java"));

        assertTrue(common.contains("AtomicBoolean"));
        assertTrue(client.contains("AtomicBoolean"));
        assertTrue(fabric.contains("AtomicBoolean"));
        assertTrue(fabric.contains("SakuraSignIn.bootstrapCommon()"));
        assertTrue(common.contains("BuiltInRewardTypes.register()"));
        assertTrue(common.contains("BuiltInRewardRulePermissions.registerVirtualPermissions()"));
        assertTrue(common.contains("event.enqueueWork(SakuraRewards::freeze)"));
        assertTrue(common.indexOf("BuiltInRewardTypes.register()")
                < common.indexOf("BaniraConfig.register"));
        assertTrue(common.indexOf("BaniraConfig.register") < common.indexOf("SakuraNetwork.initialize"));
        assertTrue(client.contains("BaniraClientEvents.ModLifecycle.onClientSetup"));
        assertTrue(client.contains("BaniraClientEvents.Client.onClientTick"));
        assertTrue(client.contains("BaniraClientEvents.Player.onClientLoggedOut"));
        assertTrue(client.contains("BaniraInput.registerKey"));
        assertTrue(client.contains("GLFWKey.GLFW_KEY_UNKNOWN"));
    }

    @Test
    public void commonBootstrapIsNotWrappedInAOneShotClass() {
        assertFalse(Files.exists(MAIN.resolve("SakuraCommonBootstrap.java")));
    }

    @Test
    public void productionDoesNotImportForgeRuntimeTypes() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = source(path);
                assertFalse(path + " imports Forge runtime", source.contains("net.minecraftforge"));
            });
        }
    }

    @Test
    public void loaderAdaptersDoNotUseSleepingWorkerThreads() {
        String adapter = source(MAIN.resolve(
                "internal/fabric/event/FabricSakuraGameEventAdapter.java"));
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
