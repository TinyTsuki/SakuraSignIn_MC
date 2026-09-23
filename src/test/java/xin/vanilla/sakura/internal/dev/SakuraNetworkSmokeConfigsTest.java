package xin.vanilla.sakura.internal.dev;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.internal.fabric.config.FabricBaniraConfigService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SakuraNetworkSmokeConfigsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private BaniraPlatform previous;
    private ConfigHolder holder;
    private Path directory;
    private Path file;

    @BeforeClass public static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Before public void register() throws Exception {
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
        previous = BaniraPlatforms.get();
        directory = temporary.newFolder().toPath();
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("configService")) return FabricBaniraConfigService.INSTANCE;
                    if (method.getName().equals("configDir")) return directory;
                    throw new UnsupportedOperationException(method.toString());
                }));
        FabricBaniraConfigService.INSTANCE.register(ClientConfig.class, "sakura_sign_in");
        holder = (ConfigHolder) FabricBaniraConfigService.INSTANCE.handle(ClientConfig.class);
        file = directory.resolve(holder.getConfigName() + ".toml");
    }

    @After public void restore() { BaniraPlatforms.install(previous); }

    @Test public void missingFileFailsWithoutCreatingIt() throws Exception {
        Files.delete(file);
        assertThrows(IOException.class, () -> SakuraNetworkSmokeConfigs.verify(holder, directory, "phase-one"));
        assertFalse(Files.exists(file));
    }

    @Test public void missingDefaultFieldCannotBeFilledFromDefaults() throws Exception {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(lines.removeIf(line -> line.startsWith("specialVariant =")));
        Files.write(file, lines, StandardCharsets.UTF_8);
        assertRejectedWithoutMutation();
    }

    @Test public void invalidDefaultFieldCannotBeFilledFromDefaults() throws Exception {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("specialVariant =")));
        Files.write(file, lines.stream().map(line -> line.startsWith("specialVariant =")
                ? "specialVariant = invalid" : line).collect(Collectors.toList()), StandardCharsets.UTF_8);
        assertRejectedWithoutMutation();
    }

    @Test public void completeFilePreservesQuotedRulesAndRestartCheckpoint() throws Exception {
        List<String> rules = Arrays.asList("tick, clazz -> tick >= 5", "resource -> resource == 'a#b=c'", "quote\" and \\ escape");
        holder.set("signKeys.lastMonth", rules);
        holder.save();
        byte[] before = Files.readAllBytes(file);
        assertEquals(18, SakuraNetworkSmokeConfigs.verify(holder, directory, "phase-one").size());
        assertEquals(18, SakuraNetworkSmokeConfigs.verify(holder, directory, "phase-two").size());
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(rules, holder.get("signKeys.lastMonth"));
    }

    private void assertRejectedWithoutMutation() throws Exception {
        byte[] before = Files.readAllBytes(file);
        Object live = holder.get("display.specialVariant");
        assertThrows(IllegalStateException.class, () -> SakuraNetworkSmokeConfigs.verify(holder, directory, "phase-one"));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(live, holder.get("display.specialVariant"));
    }
}
