package xin.vanilla.sakura.internal.forge.migration;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInDataRepository;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.sakura.data.player.HistoryRetentionPolicy;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.forge.storage.AtomicNbtFiles;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.banira.common.util.DateUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 月度详情可以清理，但永久签到摘要必须保持稳定。
 */
public class MonthlySignInHistoryRetentionTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stripsOnlyRewardPayloadFromExpiredMonths() throws Exception {
        Path worldData = temporaryFolder.newFolder("strip-world").toPath();
        UUID uuid = UUID.randomUUID();
        MonthlySignInHistoryRepository histories = new MonthlySignInHistoryRepository(worldData);
        AtomicNbtFiles.write(
                historyFile(worldData, uuid, "2024-01"),
                monthRoot(uuid, "2024-01", record("2024-01-08 12:00:00", true))
        );
        AtomicNbtFiles.write(
                historyFile(worldData, uuid, "2024-06"),
                monthRoot(uuid, "2024-06", record("2024-06-08 12:00:00", true))
        );

        MonthlySignInHistoryRepository.RetentionResult result = histories.applyRetention(
                uuid, YearMonth.of(2024, 6), 2, HistoryRetentionPolicy.STRIP_REWARD_DETAILS
        );

        assertEquals(1, result.getStrippedMonthFiles());
        assertEquals(0, result.getDeletedMonthFiles());
        assertEquals("[]", firstRecord(worldData, uuid, "2024-01").getString("rewardList"));
        assertEquals("[{\"type\":\"COMMAND\"}]",
                firstRecord(worldData, uuid, "2024-06").getString("rewardList"));
    }

    @Test
    public void keepsUnclaimedRewardPayloadWhenStrippingExpiredMonths() throws Exception {
        Path worldData = temporaryFolder.newFolder("unclaimed-world").toPath();
        UUID uuid = UUID.randomUUID();
        MonthlySignInHistoryRepository histories = new MonthlySignInHistoryRepository(worldData);
        AtomicNbtFiles.write(
                historyFile(worldData, uuid, "2024-01"),
                monthRoot(uuid, "2024-01", record("2024-01-08 12:00:00", false))
        );

        MonthlySignInHistoryRepository.RetentionResult result = histories.applyRetention(
                uuid, YearMonth.of(2024, 6), 2, HistoryRetentionPolicy.STRIP_REWARD_DETAILS
        );

        assertEquals(0, result.getStrippedMonthFiles());
        assertEquals("[{\"type\":\"COMMAND\"}]",
                firstRecord(worldData, uuid, "2024-01").getString("rewardList"));
    }

    @Test
    public void deletesExpiredMonthWithoutChangingPermanentSummary() throws Exception {
        Path worldData = temporaryFolder.newFolder("delete-world").toPath();
        UUID uuid = UUID.randomUUID();
        MemorySummaryStore summaries = new MemorySummaryStore();
        MonthlySignInHistoryRepository histories = new MonthlySignInHistoryRepository(worldData);
        PlayerSignInDataRepository repository = new PlayerSignInDataRepository(summaries, histories);
        PlayerSignInData source = TestPlayerData.twoMonths(uuid);
        source.setTotalSignInDays(42);
        source.setContinuousSignInDays(7);
        source.setSignInCard(5);
        source.setLanguage("zh_cn");
        source.setAutoRewarded(true);
        source.setCdkRecords(Collections.singletonList(
                new KeyValue<>("WELCOME", new KeyValue<>(new Date(1717848794000L), true))
        ));
        repository.save(uuid, source);
        CompoundNBT summaryBefore = summaries.stored.copy();

        histories.applyRetention(
                uuid, YearMonth.of(2024, 6), 2, HistoryRetentionPolicy.DELETE_MONTH_FILE
        );
        PlayerSignInData restored = repository.load(uuid);

        assertFalse(Files.exists(historyFile(worldData, uuid, "2024-01")));
        assertTrue(Files.exists(historyFile(worldData, uuid, "2024-06")));
        assertEquals(summaryBefore, summaries.stored);
        assertEquals(42, restored.getTotalSignInDays());
        assertEquals(7, restored.getContinuousSignInDays());
        assertEquals(5, restored.getSignInCard());
        assertEquals("zh_cn", restored.getLanguage());
        assertTrue(restored.isAutoRewarded());
        assertEquals("WELCOME", restored.getCdkRecords().get(0).key());
        assertTrue(restored.isSignedOn(DateUtils.format("2024-01-08 12:00:00")));
        assertTrue(restored.isRewardedOn(DateUtils.format("2024-01-08 12:00:00")));

        restored.plusSignInCard();
        repository.save(uuid, restored);
        PlayerSignInData savedAgain = repository.load(uuid);
        assertEquals(6, savedAgain.getSignInCard());
        assertTrue(savedAgain.isSignedOn(DateUtils.format("2024-01-08 12:00:00")));
        assertFalse(Files.exists(historyFile(worldData, uuid, "2024-01")));
    }

    @Test
    public void keepsMalformedUnknownHistoryAndDisablesCleanupAtZeroMonths() throws Exception {
        Path worldData = temporaryFolder.newFolder("unknown-world").toPath();
        UUID uuid = UUID.randomUUID();
        Path unknown = historyFile(worldData, uuid, "unknown");
        AtomicNbtFiles.write(unknown, monthRoot(uuid, "unknown", record("invalid", true)));
        MonthlySignInHistoryRepository histories = new MonthlySignInHistoryRepository(worldData);

        MonthlySignInHistoryRepository.RetentionResult result = histories.applyRetention(
                uuid, YearMonth.of(2024, 6), 0, HistoryRetentionPolicy.DELETE_MONTH_FILE
        );

        assertEquals(0, result.getDeletedMonthFiles());
        assertTrue(Files.exists(unknown));
    }

    private static CompoundNBT firstRecord(Path worldData, UUID uuid, String month) throws IOException {
        return AtomicNbtFiles.read(historyFile(worldData, uuid, month))
                .getList("records", 10)
                .getCompound(0);
    }

    private static Path historyFile(Path worldData, UUID uuid, String month) {
        return worldData.resolve("sakura_sign_in")
                .resolve("history")
                .resolve(uuid.toString())
                .resolve(month + ".nbt");
    }

    private static CompoundNBT monthRoot(UUID uuid, String month, CompoundNBT... records) {
        CompoundNBT root = new CompoundNBT();
        root.putInt("schemaVersion", 1);
        root.putString("playerUuid", uuid.toString());
        root.putString("month", month);
        ListNBT list = new ListNBT();
        Arrays.stream(records).forEach(list::add);
        root.put("records", list);
        return root;
    }

    private static CompoundNBT record(String date, boolean rewarded) {
        CompoundNBT record = new CompoundNBT();
        record.putString("compensateTime", date);
        record.putString("signInTime", date);
        record.putString("signInUUID", UUID.randomUUID().toString());
        record.putBoolean("rewarded", rewarded);
        record.putString("rewardList", "[{\"type\":\"COMMAND\"}]");
        return record;
    }

    private static final class TestPlayerData {
        private static PlayerSignInData twoMonths(UUID uuid) {
            SignInRecord january = record(uuid, "2024-01-08 12:00:00");
            SignInRecord june = record(uuid, "2024-06-08 12:00:00");
            PlayerSignInData data = new PlayerSignInData();
            data.setSignInRecords(Arrays.asList(january, june));
            return data;
        }

        private static SignInRecord record(UUID uuid, String date) {
            SignInRecord record = new SignInRecord();
            record.setCompensateTime(DateUtils.format(date));
            record.setSignInTime(DateUtils.format(date));
            record.setSignInUUID(uuid.toString());
            record.setRewarded(true);
            record.setRewardList(new RewardList());
            return record;
        }
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
        public void saveAndVerify(UUID playerUuid, PlayerSignInSummary summary) {
            stored = summary.serializeNBT();
        }
    }
}
