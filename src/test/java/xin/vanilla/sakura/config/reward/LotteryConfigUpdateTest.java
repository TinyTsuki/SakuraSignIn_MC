package xin.vanilla.sakura.config.reward;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.RewardList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

public class LotteryConfigUpdateTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    private RewardConfig previous;
    private RewardConfig original;

    @Before public void setup() {
        previous = RewardConfigManager.getRewardConfig();
        original = new RewardConfig();
        original.getMonthRewards().put("1", new RewardList());
        RewardConfigManager.setRewardConfig(original);
    }

    @After public void cleanup() {
        RewardConfigManager.setRewardConfig(previous);
    }

    @Test public void successfulReplacementPersistsBeforePublishingAndIsIsolated() throws Exception {
        Path directory = temporary.newFolder("valid").toPath();
        RewardConfigRepository repository = new RewardConfigRepository(directory);
        repository.save(original);
        LotteryPool pool = pool();
        long before = RewardConfigManager.getLotteryGeneration();

        RewardConfigManager.replaceLotteryPools(Collections.singletonList(pool), repository);

        assertEquals(before + 1, RewardConfigManager.getLotteryGeneration());
        RewardConfig restored = repository.loadOrCreate(new RewardConfig());
        assertEquals(Collections.singletonList(pool), restored.getLotteryPools());
        assertTrue(restored.getMonthRewards().containsKey("1"));
        pool.setDescription("Changed later");
        assertEquals("Line one\nLine two", RewardConfigManager.getRewardConfig()
                .getLotteryPools().get(0).getDescription());
        assertTrue(original.getLotteryPools().isEmpty());
    }

    @Test public void backupFailureKeepsOriginalAndGeneration() throws Exception {
        Path directory = temporary.newFolder("blocked").toPath();
        Files.createDirectory(directory.resolve(RewardConfigRepository.REWARD_FILE_NAME));
        long before = RewardConfigManager.getLotteryGeneration();
        try {
            RewardConfigManager.replaceLotteryPools(Collections.singletonList(pool()),
                    new RewardConfigRepository(directory));
            fail("Failed write accepted");
        } catch (java.io.IOException expected) {
            assertSame(original, RewardConfigManager.getRewardConfig());
            assertTrue(original.getLotteryPools().isEmpty());
            assertEquals(before, RewardConfigManager.getLotteryGeneration());
        }
    }

    @Test public void actualSaveFailureKeepsOriginalAndGeneration() throws Exception {
        long before = RewardConfigManager.getLotteryGeneration();
        try {
            RewardConfigManager.replaceLotteryPools(Collections.singletonList(pool()), candidate -> {
                assertSame(original, RewardConfigManager.getRewardConfig());
                assertEquals(before, RewardConfigManager.getLotteryGeneration());
                assertEquals("xpPoints(2)", candidate.getLotteryPools().get(0).getVoucherExpression());
                throw new java.io.IOException("injected write failure");
            });
            fail("Failed save published");
        } catch (java.io.IOException expected) {
            assertSame(original, RewardConfigManager.getRewardConfig());
            assertTrue(original.getLotteryPools().isEmpty());
            assertEquals(before, RewardConfigManager.getLotteryGeneration());
        }
    }

    @Test public void replacementRebuildsOrdinaryRuntimeRelationsBeforeSaving() throws Exception {
        original.getContinuousRewards().put("1", new RewardList(Collections.singletonList(
                xin.vanilla.sakura.reward.Reward.getDefault())));
        original.getCycleRewards().put("1", new RewardList(Collections.singletonList(
                xin.vanilla.sakura.reward.Reward.getDefault())));
        original.refreshContinuousRewardsRelation();
        original.refreshCycleRewardsRelation();
        assertFalse(original.getContinuousRewardsRelation().isEmpty());
        assertFalse(original.getCycleRewardsRelation().isEmpty());

        RewardConfigManager.replaceLotteryPools(Collections.singletonList(pool()), candidate -> {
            assertEquals(original.getContinuousRewardsRelation(), candidate.getContinuousRewardsRelation());
            assertEquals(original.getCycleRewardsRelation(), candidate.getCycleRewardsRelation());
        });

        RewardConfig current = RewardConfigManager.getRewardConfig();
        assertEquals(original.getContinuousRewards().get("1"), current.getContinuousRewards().get(
                current.getContinuousRewardsRelation().get("1")));
        assertEquals(original.getCycleRewards().get("1"), current.getCycleRewards().get(
                current.getCycleRewardsRelation().get("1")));
    }

    @Test public void invalidUpdateKeepsFileAndGeneration() throws Exception {
        Path directory = temporary.newFolder("invalid").toPath();
        RewardConfigRepository repository = new RewardConfigRepository(directory);
        repository.save(original);
        Path file = directory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        byte[] bytes = Files.readAllBytes(file);
        long before = RewardConfigManager.getLotteryGeneration();
        LotteryPool pool = pool();
        pool.setVoucherExpression("unknown(1)");
        try {
            RewardConfigManager.replaceLotteryPools(Collections.singletonList(pool), repository);
            fail("Invalid update accepted");
        } catch (java.io.IOException expected) {
            assertSame(original, RewardConfigManager.getRewardConfig());
            assertArrayEquals(bytes, Files.readAllBytes(file));
            assertEquals(before, RewardConfigManager.getLotteryGeneration());
        }
    }

    private static LotteryPool pool() {
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY,
                1, 0, new RewardList());
        pool.setDescription("Line one\nLine two");
        pool.setVoucherExpression("xpPoints(2)");
        return pool;
    }
}
