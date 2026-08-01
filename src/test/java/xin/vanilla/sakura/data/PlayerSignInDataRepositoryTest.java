package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.data.personaldate.PersonalDateCalendar;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.internal.forge.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.banira.common.util.DateUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 模拟服务端重启，验证拆分存储可以完整恢复运行时数据。
 */
public class PlayerSignInDataRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reloadsSummaryCdkAndMonthlyRecordsAfterSave() throws Exception {
        UUID uuid = UUID.randomUUID();
        MemorySummaryStore summaries = new MemorySummaryStore();
        MonthlySignInHistoryRepository histories = new MonthlySignInHistoryRepository(
                temporaryFolder.newFolder("world-data").toPath()
        );
        PlayerSignInDataRepository repository =
                new PlayerSignInDataRepository(summaries, histories);

        PlayerSignInData source = new PlayerSignInData();
        source.setTotalSignInDays(23);
        source.setContinuousSignInDays(6);
        source.setLastSignInTime(DateUtils.format("2024-06-08 12:13:14"));
        source.setSignInCard(9);
        source.setAutoRewarded(true);
        source.setLanguage("zh_cn");
        source.setCdkRecords(Collections.singletonList(
                new KeyValue<>("WELCOME", new KeyValue<>(new Date(1717848794000L), true))
        ));
        source.setPersonalDateSlots(Collections.singletonList(
                new PlayerPersonalDateSlot("server_day", 0, PersonalDateCalendar.SOLAR,
                        6, 8, "YEARLY:2024")
        ));
        SignInRecord record = new SignInRecord();
        record.setCompensateTime(DateUtils.format("2024-06-08 12:13:14"));
        record.setSignInTime(DateUtils.format("2024-06-08 12:13:14"));
        record.setSignInUUID(uuid.toString());
        record.setRewarded(true);
        record.setRewardList(new RewardList());
        source.setSignInRecords(Collections.singletonList(record));

        repository.save(uuid, source);
        PlayerSignInData restored = new PlayerSignInDataRepository(summaries, histories).load(uuid);

        assertEquals(23, restored.getTotalSignInDays());
        assertEquals(6, restored.getContinuousSignInDays());
        assertEquals(9, restored.getSignInCard());
        assertTrue(restored.isAutoRewarded());
        assertEquals("zh_cn", restored.getLanguage());
        assertEquals(1, restored.getCdkRecords().size());
        assertEquals("WELCOME", restored.getCdkRecords().get(0).key());
        assertEquals(source.getPersonalDateSlots(), restored.getPersonalDateSlots());
        assertEquals(1, restored.getSignInRecords().size());
        assertTrue(restored.getSignInRecords().get(0).isRewarded());
    }

    private static final class MemorySummaryStore implements PlayerSummaryStore {
        private CompoundNBT stored;

        @Override
        public Optional<PlayerSignInSummary> load(UUID playerUuid) {
            return stored == null
                    ? Optional.empty()
                    : Optional.of(PlayerSignInSummary.deserializeNBT(stored));
        }

        @Override
        public void saveAndVerify(UUID playerUuid, PlayerSignInSummary summary) throws IOException {
            stored = summary.serializeNBT();
        }
    }
}
