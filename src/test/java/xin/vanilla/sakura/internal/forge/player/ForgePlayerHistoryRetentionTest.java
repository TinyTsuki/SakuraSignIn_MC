package xin.vanilla.sakura.internal.forge.player;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.domain.player.HistoryRetentionPolicy;
import xin.vanilla.sakura.internal.forge.migration.MonthlySignInHistoryRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 历史详情损坏或清理失败时，玩家核心数据仍应继续加载。
 */
public class ForgePlayerHistoryRetentionTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cleanupFailureDoesNotEscapeIntoPlayerLoading() throws Exception {
        Path worldData = temporaryFolder.newFolder("world").toPath();
        UUID uuid = UUID.randomUUID();
        Path malformed = worldData.resolve("sakura_sign_in")
                .resolve("history")
                .resolve(uuid.toString())
                .resolve("2024-01.nbt");
        Files.createDirectories(malformed.getParent());
        Files.write(malformed, "not-nbt".getBytes(StandardCharsets.UTF_8));

        ForgePlayerSignInDataService.applyRetentionBestEffort(
                uuid,
                new MonthlySignInHistoryRepository(worldData),
                YearMonth.of(2024, 6),
                2,
                HistoryRetentionPolicy.STRIP_REWARD_DETAILS
        );
    }
}
