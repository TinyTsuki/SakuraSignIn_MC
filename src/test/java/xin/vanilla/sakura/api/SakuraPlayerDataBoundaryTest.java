package xin.vanilla.sakura.api;

import org.junit.Test;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.migration.LegacyMigrationResult;
import xin.vanilla.sakura.platform.SakuraPlayerDataService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * 锁定业务调用面，加载器实现只能存在于 internal 包。
 */
public class SakuraPlayerDataBoundaryTest {

    @Test
    public void facadeDelegatesWithoutExposingLoaderTypes() {
        Object player = new Object();
        IPlayerSignInData data = new PlayerSignInData();
        AtomicReference<Object> requested = new AtomicReference<>();
        SakuraPlayerData.install(new NoOpService() {
            @Override
            public IPlayerSignInData get(Object value) {
                requested.set(value);
                return data;
            }
        });

        assertSame(data, SakuraPlayerData.get(player));
        assertSame(player, requested.get());
    }

    @Test
    public void publicBoundaryDoesNotImportLoaderImplementation() throws Exception {
        assertSourcesDoNotContain(Paths.get("src/main/java/xin/vanilla/sakura/api"),
                "xin.vanilla.sakura.internal.forge");
        assertSourcesDoNotContain(Paths.get("src/main/java/xin/vanilla/sakura/platform"),
                "xin.vanilla.sakura.internal.forge");
        assertSourcesDoNotContain(Paths.get("src/main/java/xin/vanilla/sakura/api"),
                "xin.vanilla.sakura.internal.fabric");
        assertSourcesDoNotContain(Paths.get("src/main/java/xin/vanilla/sakura/platform"),
                "xin.vanilla.sakura.internal.fabric");
        assertSourcesDoNotContain(Paths.get("src/main/java/xin/vanilla/sakura"),
                "net.minecraftforge.common.capabilities");
    }

    private static void assertSourcesDoNotContain(Path root, String forbidden) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    assertFalse(path + " contains " + forbidden, source.contains(forbidden));
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private abstract static class NoOpService implements SakuraPlayerDataService {
        @Override
        public void setClient(UUID playerUuid, IPlayerSignInData data) {
        }

        @Override
        public LegacyMigrationResult migrateAndLoad(Object serverPlayer) {
            return LegacyMigrationResult.NO_LEGACY_DATA;
        }

        @Override
        public void save(Object serverPlayer) {
        }

        @Override
        public void saveAndSync(Object serverPlayer) {
        }

        @Override
        public void sync(Object serverPlayer) {
        }

        @Override
        public void removeServer(UUID playerUuid) {
        }

        @Override
        public void clearClient() {
        }

        @Override
        public void saveAllAndClear() {
        }
    }
}
