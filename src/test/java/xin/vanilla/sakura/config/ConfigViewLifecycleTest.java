package xin.vanilla.sakura.config;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigValueStore;
import xin.vanilla.banira.internal.fabric.config.FabricBaniraConfigService;
import xin.vanilla.banira.platform.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static org.junit.Assert.*;

public class ConfigViewLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Rule public org.junit.rules.ExternalResource platform = ConfigBaselineFixture.platformScope();
    @BeforeClass public static void bootstrap() { net.minecraft.server.Bootstrap.bootStrap(); }
    @Test public void retainedHistoryFollowRealFileReloadAndRebinding() throws Exception { verify(false); }
    @Test public void retainedClientCategoryFollowsRealFileReloadAndRebinding() throws Exception { verify(true); }

    private void verify(boolean client) throws Exception {
        Path directory = temporary.newFolder().toPath();
        install(directory, FabricBaniraConfigService.INSTANCE);
        Class<?> type = client ? ClientConfig.class : CommonConfig.class;
        String key = client ? "signKeys.lastMonth" : "history.retentionMonths";
        FabricBaniraConfigService.INSTANCE.register(type, "sakura_sign_in");
        ConfigHolder original = holder(type);
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
        Object initial = read.get();
        Path file = directory.resolve(original.getConfigName() + ".toml");
        for (int i = 0; i < 20; i++) {
            Object changed = client ? Arrays.asList("A,B", "CTRL+C" + i) : i + 1;
            ConfigValueStore external = disk(file, original);
            external.set(key, changed);
            external.save();
            FabricBaniraConfigService.INSTANCE.register(type, "sakura_sign_in");
            assertNotSame(original, holder(type));
            assertEquals(changed, read.get());
            assertEquals(initial, original.get(key));
        }
        Object saved = client ? Collections.singletonList("SHIFT+P") : 12;
        write.accept(saved);
        ConfigHolder latest = holder(type);
        latest.save();
        assertEquals(saved, disk(file, latest).get(key));
        ConfigBaselineFixture.bind(type, null);
        assertEquals(initial, read.get());
        write.accept(initial);
        assertEquals(saved, latest.get(key));
        install(directory, FabricBaniraConfigService.INSTANCE);
        assertEquals(saved, read.get());
    }

    @Test public void declarationBeansRetainFluentAccessorsWithoutExposingRootFields() throws Exception {
        CommonConfig.HistoryCategory history = new CommonConfig.HistoryCategory();
        assertSame(history, history.retentionMonths(12));
        assertEquals(12, history.retentionMonths());
        ClientConfig.SignKeysCategory keys = new ClientConfig.SignKeysCategory();
        List<String> chords = Collections.singletonList("A,B");
        assertSame(keys, keys.lastMonth(chords));
        assertEquals(chords, keys.lastMonth());
        for (Class<?> root : Arrays.asList(CommonConfig.class, ClientConfig.class)) {
            for (Field field : root.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                for (Method method : root.getDeclaredMethods()) {
                    assertFalse("Declaration field exposed: " + field.getName(),
                            method.getName().equals(field.getName()) && !Modifier.isStatic(method.getModifiers()));
                }
            }
        }
    }

    private static ConfigHolder holder(Class<?> type) {
        return (ConfigHolder) FabricBaniraConfigService.INSTANCE.handle(type);
    }
    private static ConfigValueStore disk(Path path, ConfigHolder holder) throws Exception {
        Class<?> type = Class.forName("xin.vanilla.banira.internal.fabric.config.FabricConfigValueStore");
        Constructor<?> constructor = type.getDeclaredConstructor(Path.class, List.class);
        constructor.setAccessible(true);
        return (ConfigValueStore) constructor.newInstance(path, holder.getDescriptors());
    }
    private static void install(Path directory, BaniraConfigService service) {
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(
                ConfigViewLifecycleTest.class.getClassLoader(), new Class<?>[]{BaniraPlatform.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("configService")) return service;
                    if (method.getName().equals("configDir")) return directory;
                    throw new UnsupportedOperationException(method.toString());
                }));
    }
}
