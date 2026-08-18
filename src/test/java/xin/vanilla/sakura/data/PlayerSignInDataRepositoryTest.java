package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.internal.fabric.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.banira.common.util.DateUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
                new PlayerPersonalDateSlot("server_day", 0, CalendarIds.GREGORIAN,
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
        assertEquals(1, summaries.saveCalls);
        assertEquals(0, summaries.verifiedSaveCalls);
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

    @Test
    public void summaryOnlySaveDoesNotRewriteUnchangedMonthlyHistory() throws Exception {
        UUID uuid = UUID.randomUUID();
        MemorySummaryStore summaries = new MemorySummaryStore();
        Path worldData = temporaryFolder.newFolder("unchanged-history").toPath();
        PlayerSignInDataRepository repository = new PlayerSignInDataRepository(
                summaries, new MonthlySignInHistoryRepository(worldData)
        );
        PlayerSignInData source = new PlayerSignInData();
        SignInRecord record = new SignInRecord();
        record.setCompensateTime(DateUtils.format("2024-06-08 12:13:14"));
        record.setSignInTime(DateUtils.format("2024-06-08 12:13:14"));
        record.setSignInUUID(uuid.toString());
        record.setRewarded(true);
        record.setRewardList(new RewardList());
        source.setSignInRecords(Collections.singletonList(record));

        repository.save(uuid, source);
        Path monthFile = worldData.resolve("sakura_sign_in")
                .resolve("history").resolve(uuid.toString()).resolve("2024-06.nbt");
        FileTime unchangedMarker = FileTime.fromMillis(1_000L);
        Files.setLastModifiedTime(monthFile, unchangedMarker);

        source.setLanguage("zh_cn");
        repository.save(uuid, source);

        assertEquals(unchangedMarker, Files.getLastModifiedTime(monthFile));

        SignInRecord nextRecord = new SignInRecord();
        nextRecord.setCompensateTime(DateUtils.format("2024-06-09 12:13:14"));
        nextRecord.setSignInTime(DateUtils.format("2024-06-09 12:13:14"));
        nextRecord.setSignInUUID(uuid.toString());
        nextRecord.setRewarded(true);
        nextRecord.setRewardList(new RewardList());
        source.getSignInRecords().add(nextRecord);
        repository.save(uuid, source);

        assertNotEquals(unchangedMarker, Files.getLastModifiedTime(monthFile));
        assertEquals(2, repository.load(uuid).getSignInRecords().size());
    }

    private static final class MemorySummaryStore implements PlayerSummaryStore {
        private CompoundTag stored;
        private int saveCalls;
        private int verifiedSaveCalls;

        @Override
        public Optional<PlayerSignInSummary> load(UUID playerUuid) {
            return stored == null
                    ? Optional.empty()
                    : Optional.of(PlayerSignInSummary.deserializeNBT(stored));
        }

        @Override
        public void save(UUID playerUuid, PlayerSignInSummary summary) {
            saveCalls++;
            stored = summary.serializeNBT();
        }

        @Override
        public void saveAndVerify(UUID playerUuid, PlayerSignInSummary summary) {
            verifiedSaveCalls++;
            stored = summary.serializeNBT();
        }
    }
}
