package xin.vanilla.sakura.config.reward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import java.util.Collections;
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
            return files.findFirst().orElseThrow();
        }
    }

    @Test public void corruptRecordDoesNotConsumeHistoryOrChangeModel() throws Exception {
        RewardConfigManager.addUndoRewardOption(RULE);
        Path file = undoFile(); byte[] valid = Files.readAllBytes(file);
        Files.writeString(file, "{broken", StandardCharsets.UTF_8);
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", new RewardList());
        assertFalse(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().containsKey("1"));
        Files.write(file, valid);
        assertTrue(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().isEmpty());
    }

    @Test public void malformedLegacyRewardMustNotReplaceModelOrConsumeHistory() throws Exception {
        RewardConfig config = RewardConfigManager.getRewardConfig();
        config.getMonthRewards().put("1", new RewardList());
        RewardConfigManager.addUndoRewardOption(RULE);
        Path file = undoFile();
        byte[] valid = Files.readAllBytes(file);
        Files.writeString(file, "{\"1\":[{}]}", StandardCharsets.UTF_8);
        config.getMonthRewards().put("2", new RewardList());
        String before = new RewardConfigCodec().encode(config);
        assertFalse("Malformed legacy reward must not apply", RewardConfigManager.undoRewardOption(RULE));
        assertEquals(before, new RewardConfigCodec().encode(config));
        Files.write(file, valid);
        assertTrue("Same history entry must remain usable", RewardConfigManager.undoRewardOption(RULE));
        assertFalse(config.getMonthRewards().containsKey("2"));
    }

    @Test public void legacyMapWithCompleteRewardStillRestores() throws Exception {
        RewardList rewards = new RewardList();
        JsonObject content = new JsonObject();
        content.addProperty("value", 3);
        rewards.add(new xin.vanilla.sakura.reward.Reward(content,
                xin.vanilla.sakura.api.reward.SakuraRewardTypes.EXPERIENCE_POINT));
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", rewards);
        RewardConfigManager.addUndoRewardOption(RULE);
        Files.writeString(undoFile(), new Gson().toJson(Collections.singletonMap("1", rewards)), StandardCharsets.UTF_8);
        RewardConfigManager.getRewardConfig().getMonthRewards().clear();
        assertTrue(RewardConfigManager.undoRewardOption(RULE));
        assertEquals(rewards, RewardConfigManager.getRewardConfig().getMonthRewards().get("1"));
    }

    @Test public void oppositeHistoryWriteFailureLeavesModelAndUndoAvailable() throws Exception {
        RewardConfigManager.addUndoRewardOption(RULE);
        Path blocked = RewardConfigManager.getConfigDirectory().resolve("history/redo");
        Files.writeString(blocked, "blocked", StandardCharsets.UTF_8);
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", new RewardList());
        assertFalse(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().containsKey("1"));
        Files.delete(blocked);
        assertTrue(RewardConfigManager.undoRewardOption(RULE));
        assertTrue(RewardConfigManager.getRewardConfig().getMonthRewards().isEmpty());
    }
}
