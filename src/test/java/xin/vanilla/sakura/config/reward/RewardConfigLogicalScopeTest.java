package xin.vanilla.sakura.config.reward;

import org.junit.*;
import xin.vanilla.sakura.reward.RewardList;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class RewardConfigLogicalScopeTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    private final AtomicBoolean client = new AtomicBoolean();
    private RewardConfig previous;
    @Before public void setup() {
        previous = RewardConfigManager.getRewardConfig();
        RewardConfigManager.setRewardConfig(new RewardConfig());
        RewardConfigManager.configureClientScope(client::get);
    }
    @After public void cleanup() {
        client.set(false); RewardConfigManager.configureClientScope(() -> false);
        RewardConfigManager.setRewardConfig(previous);
    }

    @Test public void clientConfigurationAndMutationsDoNotChangeServerModel() {
        RewardConfig server = RewardConfigManager.getRewardConfig();
        server.getMonthRewards().put("1", new RewardList());
        client.set(true);
        RewardConfig local = RewardConfigManager.getRewardConfig();
        assertNotSame(server, local);
        local.getMonthRewards().put("2", new RewardList());
        RewardConfigManager.addKeyName(xin.vanilla.sakura.enums.ERewardRule.MONTH_REWARD, "3", new RewardList());
        client.set(false);
        assertSame(server, RewardConfigManager.getRewardConfig());
        assertFalse(server.getMonthRewards().containsKey("2"));
        assertFalse(server.getMonthRewards().containsKey("3"));
    }

    @Test public void cacheSaveCannotAdvanceServerRevision() throws Exception {
        long generation = RewardConfigManager.getLotteryGeneration();
        client.set(true);
        RewardConfig local = RewardConfigManager.getRewardConfig();
        RewardConfigManager.persistRewardOption(candidate -> assertSame(local, candidate));
        assertEquals(generation, RewardConfigManager.getLotteryGeneration());
        client.set(false);
        RewardConfig server = RewardConfigManager.getRewardConfig();
        RewardConfigManager.persistRewardOption(candidate -> assertSame(server, candidate));
        assertEquals(generation + 1, RewardConfigManager.getLotteryGeneration());
    }

    @Test public void clientCacheCannotPublishAuthoritativeLotteryReplacement() {
        client.set(true);
        assertThrows(java.io.IOException.class, () -> RewardConfigManager.replaceLotteryPools(
                java.util.Collections.emptyList(), RewardConfigManager.getLotteryGeneration()));
    }

    @Test public void clientUndoCleanupCannotClearServerHistory() throws Exception {
        java.lang.reflect.Field field = RewardConfigManager.class.getDeclaredField("undoList");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<xin.vanilla.sakura.enums.ERewardRule, java.util.concurrent.ConcurrentLinkedDeque<String>> history =
                (java.util.Map<xin.vanilla.sakura.enums.ERewardRule, java.util.concurrent.ConcurrentLinkedDeque<String>>) field.get(null);
        java.util.concurrent.ConcurrentLinkedDeque<String> entries = new java.util.concurrent.ConcurrentLinkedDeque<>();
        entries.add("server-history");
        history.put(xin.vanilla.sakura.enums.ERewardRule.BASE_REWARD, entries);
        client.set(true); RewardConfigManager.clearUndoList();
        assertNotNull(history.get(xin.vanilla.sakura.enums.ERewardRule.BASE_REWARD));
        assertEquals("server-history", history.get(xin.vanilla.sakura.enums.ERewardRule.BASE_REWARD).peek());
        client.set(false); RewardConfigManager.clearUndoList();
    }
}
