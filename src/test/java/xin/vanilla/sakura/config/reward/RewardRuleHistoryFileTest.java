package xin.vanilla.sakura.config.reward;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.platform.BaniraPathService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.RewardList;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class RewardRuleHistoryFileTest {
    @Rule public final xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private BaniraPlatform previousPlatform;
    private RewardConfig previousConfig;
    private static final ERewardRule RULE = ERewardRule.MONTH_REWARD;

    @Before public void setup() throws Exception {
        previousPlatform = BaniraPlatforms.get();
        Path root = temporary.newFolder("config").toPath();
        BaniraPathService paths = (BaniraPathService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPathService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("gameConfigPath")) return root;
                    throw new UnsupportedOperationException(method.toString());
                });
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pathService")) return paths;
                    return method.invoke(previousPlatform, args);
                }));
        previousConfig = RewardConfigManager.getRewardConfig();
        RewardConfigManager.setRewardConfig(new RewardConfig());
        RewardConfigManager.clearUndoList(); RewardConfigManager.clearRedoList();
    }

    @After public void cleanup() {
        RewardConfigManager.clearUndoList(); RewardConfigManager.clearRedoList();
        RewardConfigManager.setRewardConfig(previousConfig);
        BaniraPlatforms.install(previousPlatform);
    }

    @Test public void diskUndoAndRedoRestoreAnEmptyRule() {
        RewardConfigManager.addUndoRewardOption(RULE);
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", new RewardList());
        assertTrue(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().isEmpty());
        assertTrue(RewardConfigManager.redoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().containsKey("1"));
        assertFalse(RewardConfigManager.redoRewardOption(RULE));
    }

    private Path undoFile() throws Exception {
        try (Stream<Path> files = Files.list(RewardConfigManager.getConfigDirectory().resolve("history/undo/month_reward"))) {
            return files.findFirst().orElseThrow(() -> new AssertionError("No history file"));
        }
    }

    @Test public void corruptRecordDoesNotConsumeHistoryOrChangeModel() throws Exception {
        RewardConfigManager.addUndoRewardOption(RULE);
        Path file = undoFile(); byte[] valid = Files.readAllBytes(file);
        Files.write(file, "{broken".getBytes(StandardCharsets.UTF_8));
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", new RewardList());
        assertFalse(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().containsKey("1"));
        Files.write(file, valid);
        assertTrue(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().isEmpty());
    }

    @Test public void oppositeHistoryWriteFailureLeavesModelAndUndoAvailable() throws Exception {
        RewardConfigManager.addUndoRewardOption(RULE);
        Path blocked = RewardConfigManager.getConfigDirectory().resolve("history/redo");
        Files.write(blocked, "blocked".getBytes(StandardCharsets.UTF_8));
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", new RewardList());
        assertFalse(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().containsKey("1"));
        Files.delete(blocked);
        assertTrue(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().isEmpty());
    }
}
