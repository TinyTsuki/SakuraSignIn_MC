package xin.vanilla.sakura.config.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.platform.BaniraPathService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.RewardList;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class RewardHistoryPersistenceTest {
    @Rule public final xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private BaniraPlatform previousPlatform;
    private RewardConfig previousConfig;

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
        RewardConfigManager.clearUndoList();
        RewardConfigManager.clearRedoList();
    }

    @After public void cleanup() {
        RewardConfigManager.clearUndoList();
        RewardConfigManager.clearRedoList();
        RewardConfigManager.setRewardConfig(previousConfig);
        BaniraPlatforms.install(previousPlatform);
    }

    private Path undoFile(ERewardRule rule) throws Exception {
        Path directory = RewardConfigManager.getConfigDirectory().resolve("history/undo")
                .resolve(rule.name().toLowerCase(java.util.Locale.ROOT));
        try (Stream<Path> files = Files.list(directory)) {
            return files.findFirst().orElseThrow(() -> new AssertionError("No history file"));
        }
    }

    @Test public void personalDateHistoryPersistsCompleteMetadata() throws Exception {
        RewardConfig config = RewardConfigManager.getRewardConfig();
        config.getPersonalDatePresets().add(new PersonalDatePreset("birthday", "Birthday",
                PersonalDateRecurrence.YEARLY, Collections.singletonList(CalendarIds.GREGORIAN),
                2, PersonalDateDeliveryMode.SIGN_IN, 3, 4, new RewardList()));
        RewardConfigManager.addUndoRewardOption(ERewardRule.PERSONAL_DATE_REWARD);
        String json = new String(Files.readAllBytes(undoFile(ERewardRule.PERSONAL_DATE_REWARD)), StandardCharsets.UTF_8);
        JsonObject history = new JsonParser().parse(json).getAsJsonObject();
        assertTrue("Full configuration snapshot required", history.has("configuration"));
        RewardConfig restored = new RewardConfigCodec().decode(history.get("configuration").toString());
        assertEquals(new RewardConfigCodec().encode(config), new RewardConfigCodec().encode(restored));
    }

    @Test public void corruptRecordRemainsAvailableAfterRepair() throws Exception {
        ERewardRule rule = ERewardRule.MONTH_REWARD;
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", new RewardList());
        RewardConfigManager.addUndoRewardOption(rule);
        Path file = undoFile(rule);
        byte[] valid = Files.readAllBytes(file);
        Files.write(file, "{broken".getBytes(StandardCharsets.UTF_8));
        try {
            RewardConfigManager.getUnDoRewardOption(rule);
        } catch (RuntimeException failure) {
            // The retry must still refer to the same history entry.
        }
        Files.write(file, valid);
        assertTrue("Failed reads must not consume history", RewardConfigManager.getUnDoRewardOption(rule).containsKey("1"));
    }

    @Test public void ordinaryHistoryStillReadsRewardLists() {
        ERewardRule rule = ERewardRule.MONTH_REWARD;
        RewardConfigManager.getRewardConfig().getMonthRewards().put("1", new RewardList());
        RewardConfigManager.addUndoRewardOption(rule);
        assertEquals(Collections.singleton("1"), RewardConfigManager.getUnDoRewardOption(rule).keySet());
    }
}
