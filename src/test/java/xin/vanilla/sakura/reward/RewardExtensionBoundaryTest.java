package xin.vanilla.sakura.reward;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/** 最终奖励扩展边界，不允许旧枚举和 parser API 重新进入生产代码。 */
public class RewardExtensionBoundaryTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void productionUsesOnlyNamespacedRewardRegistrations() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = read(path);
                reject(violations, path, source, "ERewardType");
                reject(violations, path, source, "RewardParser");
                reject(violations, path, source, ".getType().ordinal");
                reject(violations, path, source, "reward.getType() ==");
                if (path.endsWith("RewardOptionScreen.java")) {
                    reject(violations, path, source, "equalsIgnoreCase(selectedString)");
                }
            });
        }

        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    @Test
    public void commonRewardLayersDoNotImportClientTypes() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String relative : new String[]{"api/reward", "reward", "config/reward",
                "internal/server"}) {
            Path root = MAIN.resolve(relative);
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().replace('\\', '/').contains(
                                "/api/reward/client/"))
                        .forEach(path -> {
                            String source = read(path);
                            reject(violations, path, source, "import net.minecraft.client");
                            reject(violations, path, source,
                                    "import xin.vanilla.sakura.api.reward.client");
                        });
            }
        }
        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    private static void reject(List<String> violations, Path path, String source,
                               String forbidden) {
        if (source.contains(forbidden)) {
            violations.add(path + " contains " + forbidden);
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
