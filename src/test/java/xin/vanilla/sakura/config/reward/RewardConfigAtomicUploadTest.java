package xin.vanilla.sakura.config.reward;

import org.junit.*;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.data.personaldate.*;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.network.packet.LotteryPoolSyncPacket;
import xin.vanilla.sakura.reward.RewardList;
import java.util.*;
import static org.junit.Assert.*;

public class RewardConfigAtomicUploadTest {
    @Rule public xin.vanilla.sakura.test.UnboundConfigPlatform platform = new xin.vanilla.sakura.test.UnboundConfigPlatform();
    private RewardConfig previous;
    @Before public void setup() { previous = RewardConfigManager.getRewardConfig(); RewardConfigManager.setRewardConfig(new RewardConfig()); }
    @After public void cleanup() { RewardConfigManager.setRewardConfig(previous); }

    @Test public void oneSnapshotCarriesOrdinaryDateAndLotteryEditsTogether() {
        RewardConfig source = configuration();
        LotteryPoolSyncPacket upload = new LotteryPoolSyncPacket(source, RewardConfigManager.getLotteryGeneration());
        LotteryPoolSyncPacket restored = upload.mergePackets(upload.splitPacket());
        assertTrue(restored.isConfigurationUpload());
        assertEquals(source.getMonthRewards(), restored.getConfiguration().getMonthRewards());
        assertEquals(source.getPersonalDatePresets(), restored.getConfiguration().getPersonalDatePresets());
        assertEquals(source.getLotteryPools(), restored.getConfiguration().getLotteryPools());
    }

    @Test public void combinedReplacementPersistsOnceThenAdvancesRevisionOnce() throws Exception {
        long generation = RewardConfigManager.getLotteryGeneration(); int[] writes = {0};
        RewardConfigManager.replaceRewardConfig(configuration(), generation, candidate -> {
            writes[0]++; assertEquals(generation, RewardConfigManager.getLotteryGeneration());
            assertTrue(candidate.getMonthRewards().containsKey("3"));
            assertEquals(1, candidate.getPersonalDatePresets().size()); assertEquals(1, candidate.getLotteryPools().size());
        });
        assertEquals(1, writes[0]); assertEquals(generation + 1, RewardConfigManager.getLotteryGeneration());
        assertThrows(java.io.IOException.class, () -> RewardConfigManager.replaceRewardConfig(
                new RewardConfig(), generation, candidate -> fail("Stale snapshot persisted")));
    }

    @Test public void hiddenRulesRemainAuthoritativeInIntegratedOrDedicatedApply() throws Exception {
        RewardConfig server = configuration(); RewardConfig incoming = new RewardConfig();
        RewardConfig merged = RewardConfigManager.mergeUpload(incoming, server, rule -> false);
        assertEquals(server.getMonthRewards(), merged.getMonthRewards());
        assertEquals(server.getLotteryPools(), merged.getLotteryPools());
        assertEquals(server.getPersonalDatePresets(), merged.getPersonalDatePresets());
    }

    private static RewardConfig configuration() {
        RewardConfig config = new RewardConfig(); config.getMonthRewards().put("3", new RewardList());
        config.getPersonalDatePresets().add(new PersonalDatePreset("annual", "Annual", PersonalDateRecurrence.YEARLY,
                Collections.singletonList("minecraft:gregorian"), 1, PersonalDateDeliveryMode.SIGN_IN, 0, 0, new RewardList()));
        config.getLotteryPools().add(new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY, 1, 0, new RewardList()));
        return config;
    }
}
