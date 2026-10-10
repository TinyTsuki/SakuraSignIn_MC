package xin.vanilla.sakura.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.config.ConfigCategoryTitleSpec;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigValueStore;
import xin.vanilla.banira.common.config.annotation.Config;
import xin.vanilla.banira.internal.forge.config.ForgeConfigAdapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class ConfigViewLifecycleTest {
    @org.junit.Rule public org.junit.rules.ExternalResource platform = ConfigBaselineFixture.platformScope();
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Test public void retainedHistoryFollowsFileReloadAndRebinding() throws Exception { verify(false); }
    @Test public void retainedClientCategoryFollowsFileReloadAndRebinding() throws Exception { verify(true); }

    @Test public void clientRewardScopeIsRegisteredAfterConfigSetup() throws Exception {
        String source = new String(Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/xin/vanilla/sakura/client/SakuraClientBootstrap.java")), StandardCharsets.UTF_8);
        int setup = source.indexOf("BaniraClientEvents.ModLifecycle.onClientSetup(event -> {");
        int next = source.indexOf("BaniraClientEvents.Client.onClientTick", setup);
        assertTrue(setup >= 0 && next > setup);
        assertFalse(source.substring(0, setup).contains("RewardConfigManager.configureClientScope"));
        assertTrue(source.substring(setup, next).contains("RewardConfigManager.configureClientScope"));
    }

    @Test public void rewardScopeReadsRequireLoadedForgeConfiguration() throws Exception {
        try (FileFixture fixture = new FileFixture(CommonConfig.class,
                temporary.newFile("scope.toml").toPath(), false)) {
            ConfigBaselineFixture.bind(CommonConfig.class, fixture.holder);
            assertThrows(IllegalStateException.class, () -> CommonConfig.get().reward().continuousRewardsRepeatable());
            assertThrows(IllegalStateException.class, () ->
                    xin.vanilla.sakura.config.reward.RewardConfigManager.configureClientScope(() -> false));
            fixture.load();
            xin.vanilla.sakura.config.reward.RewardConfigManager.configureClientScope(() -> false);
        } finally { ConfigBaselineFixture.bind(CommonConfig.class, null); }
    }

    private void verify(boolean client) throws Exception {
        Class<?> type = client ? ClientConfig.class : CommonConfig.class;
        String key = client ? "signKeys.lastMonth" : "history.retentionMonths";
        Object externalValue = client ? Arrays.asList("A,B", "CTRL+C") : 6;
        Object writtenValue = client ? Collections.singletonList("SHIFT+P") : 12;
        try (FileFixture first = new FileFixture(type, temporary.newFile("first.toml").toPath());
             FileFixture second = new FileFixture(type, temporary.newFile("second.toml").toPath())) {
            ConfigBaselineFixture.bind(type, first.holder);
            Supplier<Object> read;
            Consumer<Object> write;
            if (client) {
                ClientConfigView.SignKeysView retained = ClientConfigView.get().signKeys();
                read = retained::lastMonth;
                write = value -> retained.lastMonth((List<String>) value);
            } else {
                CommonConfigView.HistoryView retained = CommonConfigView.get().history();
                read = retained::retentionMonths;
                write = value -> retained.retentionMonths((Integer) value);
            }
            Object original = read.get();
            List<Object> reloads = new ArrayList<>();
            first.holder.onReloaded(paths -> reloads.add(read.get()));
            CommentedConfig disk = parse(first.path);
            disk.set(key, externalValue);
            Files.write(first.path, TomlFormat.instance().createWriter().writeToString(disk).getBytes(StandardCharsets.UTF_8));
            first.file.load();
            first.invoke("acceptReload", new Class<?>[0]);
            assertEquals(externalValue, read.get());
            assertEquals(Collections.singletonList(externalValue), reloads);

            ConfigBaselineFixture.bind(type, second.holder);
            assertEquals(original, read.get());
            write.accept(writtenValue);
            assertEquals(writtenValue, second.holder.get(key));
            assertEquals(externalValue, first.holder.get(key));
            second.holder.save();
            assertEquals(writtenValue, parse(second.path).get(key));
            ConfigBaselineFixture.bind(type, null);
            assertEquals(original, read.get());
            write.accept(externalValue);
            assertEquals(writtenValue, second.holder.get(key));
            ConfigBaselineFixture.bind(type, first.holder);
            assertEquals(externalValue, read.get());
        } finally { ConfigBaselineFixture.bind(type, null); }
    }

    private static CommentedConfig parse(Path path) throws Exception {
        return new TomlParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    // Reflection stays in the test: exercise the real backend without publishing its internals.
    private static final class FileFixture implements AutoCloseable {
        final Path path;
        final Object backend;
        final ConfigHolder holder;
        final CommentedFileConfig file;
        final ForgeConfigSpec spec;

        FileFixture(Class<?> type, Path path) throws Exception {
            this(type, path, true);
        }

        FileFixture(Class<?> type, Path path, boolean loaded) throws Exception {
            this.path = path;
            ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
            List<ConfigEntryDescriptor> descriptors = new ArrayList<>();
            Map<String, ForgeConfigSpec.ConfigValue<?>> values = new LinkedHashMap<>();
            Map<String, String> tooltips = new LinkedHashMap<>();
            Map<String, ConfigCategoryTitleSpec> titles = new LinkedHashMap<>();
            Method scan = ForgeConfigAdapter.class.getDeclaredMethod("buildFromClass", ForgeConfigSpec.Builder.class,
                    Class.class, String.class, List.class, Map.class, Map.class, Map.class);
            scan.setAccessible(true);
            scan.invoke(null, builder, type, "", descriptors, values, tooltips, titles);
            spec = builder.build();
            Class<?> backendType = Class.forName("xin.vanilla.banira.internal.forge.config.ForgeConfigValueStore");
            Constructor<?> constructor = backendType.getDeclaredConstructor(ForgeConfigSpec.class, Map.class);
            constructor.setAccessible(true);
            backend = constructor.newInstance(spec, values);
            Config config = type.getAnnotation(Config.class);
            holder = ConfigHolder.create("fixture", config.name(), config.type(), (ConfigValueStore) backend,
                    descriptors, tooltips, titles);
            invoke("setHolder", new Class<?>[]{ConfigHolder.class}, holder);
            file = (CommentedFileConfig) invoke("wrap", new Class<?>[]{CommentedFileConfig.class},
                    CommentedFileConfig.builder(path).sync().build());
            if (loaded) load();
        }

        void load() {
            file.load();
            spec.setConfig(file);
            holder.acceptInitialExternalLoad();
        }

        Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
            Method method = backend.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(backend, args);
        }

        public void close() { file.close(); }
    }
}
