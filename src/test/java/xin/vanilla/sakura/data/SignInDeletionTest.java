package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundNBT;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.forge.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.internal.forge.storage.AtomicNbtFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class SignInDeletionTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();
    private final UUID uuid = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 10, 9);
    private final Store summaries = new Store();

    @Test
    public void deletionSurvivesOrdinarySaveAndOfflineReload() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(8, 9);
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        repository.save(uuid, data);
        PlayerSignInData loaded = repository.load(uuid);
        assertFalse(loaded.isSignedOn(date(9)));
        assertFalse(loaded.isRewardedOn(date(9)));
        assertTrue(loaded.isSignedOn(date(8)));
        assertEquals(1, loaded.getSignInRecords().size());
        assertEquals(1, loaded.getTotalSignInDays());
        assertEquals(1, loaded.getContinuousSignInDays());
        assertEquals(date(8), loaded.getLastSignInTime());
    }

    @Test
    public void deletingLastRecordRewritesEmptyMonth() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        assertTrue(repository.load(uuid).getSignInRecords().isEmpty());
        assertEquals(0, repository.load(uuid).getTotalSignInDays());
        assertEquals(0, repository.load(uuid).getContinuousSignInDays());
    }

    @Test
    public void deletionKeepsPersonalClaimsCardsAndOtherSummaryFields() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        data.setSignInCard(13);
        data.setLanguage("zh_cn");
        data.setOnlineTimeBaselineDate("2026-10-09");
        data.setOnlineTimeBaselineTicks(400);
        data.setPersonalDateSlots(Collections.singletonList(new PlayerPersonalDateSlot(
                "birthday", 0, CalendarIds.CHINESE_LUNAR, 8, 2, "YEARLY:2026")));
        repository.save(uuid, data);
        summaries.stored.put("other_mod", new CompoundNBT());
        CompoundNBT other = summaries.stored.getCompound("other_mod");
        other.putInt("value", 42);
        assertTrue(delete(repository, data, today));
        PlayerSignInData loaded = repository.load(uuid);
        assertEquals(13, loaded.getSignInCard());
        assertEquals("zh_cn", loaded.getLanguage());
        assertEquals(400, loaded.getOnlineTimeBaselineTicks());
        assertEquals("YEARLY:2026", loaded.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        assertEquals(42, summaries.stored.getCompound("other_mod").getInt("value"));
    }

    @Test
    public void retainedIndexCanBeDeletedWithoutHistory() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(8, 9);
        data.setSignInRecords(Collections.emptyList());
        data.setTotalSignInDays(30);
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today.minusDays(1)));
        assertEquals(29, repository.load(uuid).getTotalSignInDays());
        assertTrue(repository.load(uuid).isSignedOn(date(9)));
        assertFalse(repository.load(uuid).isSignedOn(date(8)));
    }

    @Test
    public void missingDayIsNoOp() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        CompoundNBT before = data.serializeNBT();
        assertFalse(delete(repository, data, today.minusDays(3)));
        assertEquals(before, data.serializeNBT());
    }

    @Test
    public void compensatedDayRatherThanActualSignInDayDeterminesDeletion() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(8, 9);
        data.getSignInRecords().get(0).setSignInTime(date(9));
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        assertEquals(1, repository.load(uuid).getSignInRecords().size());
        assertEquals(date(8), repository.load(uuid).getSignInRecords().get(0).getCompensateTime());
    }

    @Test
    public void duplicateDetailsCountAsOneSignedDay() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        data.getSignInRecords().add(data.getSignInRecords().get(0).clone());
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        assertEquals(0, data.getTotalSignInDays());
        assertTrue(data.getSignInRecords().isEmpty());
    }

    @Test
    public void verifiedSaveFailureRestoresDiskAndLeavesLiveDataUnchanged() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(8, 9);
        repository.save(uuid, data);
        CompoundNBT before = data.serializeNBT();
        summaries.failNextVerification = true;
        try {
            delete(repository, data, today);
            fail("Expected a save failure");
        } catch (IOException expected) {
            assertEquals("verification failure", expected.getMessage());
        }
        assertEquals(before, data.serializeNBT());
        assertTrue(repository.load(uuid).isSignedOn(date(9)));
        assertEquals(2, repository.load(uuid).getSignInRecords().size());
    }

    @Test
    public void backupContainsOriginalSummaryAndMonth() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        Path directory = temporary.getRoot().toPath().resolve("sakura_sign_in")
                .resolve("deletions").resolve(uuid.toString());
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            Path backup = files.filter(path -> path.getFileName().toString().startsWith("backup-"))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing backup"));
            CompoundNBT root = AtomicNbtFiles.read(backup);
            assertEquals(1, root.getCompound("beforeSummary").getInt("totalSignInDays"));
            assertEquals(1, root.getCompound("beforeHistory").getList("records", 10).size());
        }
    }

    @Test
    public void incompleteRecoveryJournalCannotOverwriteSummary() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        CompoundNBT journal = new CompoundNBT();
        journal.putString("playerUuid", uuid.toString());
        journal.putString("month", "2026-10");
        Path pending = temporary.getRoot().toPath().resolve("sakura_sign_in")
                .resolve("deletions").resolve(uuid.toString()).resolve("pending.nbt");
        AtomicNbtFiles.write(pending, journal);
        try {
            repository.load(uuid);
            fail("Incomplete journal was accepted");
        } catch (IOException expected) {
            assertTrue(summaries.load(uuid).get().getMonthIndexes().get("2026-10").isSigned(9));
        }
    }

    @Test
    public void failedRollbackIsRecoveredBeforeNextLoad() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        summaries.failVerifications = 2;
        try {
            delete(repository, data, today);
            fail("Expected failed verification");
        } catch (IOException expected) {
            assertEquals(1, expected.getSuppressed().length);
        }
        assertTrue(repository.load(uuid).isSignedOn(date(9)));
        assertEquals(1, repository.load(uuid).getSignInRecords().size());
    }

    @Test
    public void interruptedApplyIsCompletedBeforeNextLoad() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        Path directory = temporary.getRoot().toPath().resolve("sakura_sign_in")
                .resolve("deletions").resolve(uuid.toString());
        CompoundNBT journal;
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            journal = AtomicNbtFiles.read(files.filter(path -> path.getFileName().toString().startsWith("backup-"))
                    .findFirst().get());
        }
        summaries.save(uuid, PlayerSignInSummary.deserializeNBT(journal.getCompound("beforeSummary")));
        AtomicNbtFiles.write(directory.resolve("pending.nbt"), journal);
        assertFalse(repository.load(uuid).isSignedOn(date(9)));
        assertTrue(repository.load(uuid).getSignInRecords().isEmpty());
        assertFalse(Files.exists(directory.resolve("pending.nbt")));
    }

    @Test
    public void deletionKeepsOtherMonthsAndRawHistoryFields() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        java.util.Date otherDay = DateUtils.format("2026-09-30 12:00:00");
        data.markSigned(otherDay, true);
        data.setTotalSignInDays(2);
        repository.save(uuid, data);
        Path month = temporary.getRoot().toPath().resolve("sakura_sign_in")
                .resolve("history").resolve(uuid.toString()).resolve("2026-10.nbt");
        CompoundNBT original = AtomicNbtFiles.read(month);
        original.putString("extension", "unchanged");
        AtomicNbtFiles.write(month, original);
        assertTrue(delete(repository, data, today));
        assertTrue(repository.load(uuid).isSignedOn(otherDay));
        assertEquals("unchanged", AtomicNbtFiles.read(month).getString("extension"));
        assertFalse(delete(repository, data, today));
    }

    private PlayerSignInDataRepository repository() {
        return new PlayerSignInDataRepository(summaries,
                new MonthlySignInHistoryRepository(temporary.getRoot().toPath()));
    }

    private boolean delete(PlayerSignInDataRepository repository, PlayerSignInData data,
                           LocalDate day) throws Exception {
        return repository.deleteDay(uuid, data, day, today);
    }

    private PlayerSignInData signed(int... days) {
        PlayerSignInData data = new PlayerSignInData();
        Arrays.stream(days).forEach(day -> {
            SignInRecord record = new SignInRecord();
            record.setCompensateTime(date(day));
            record.setSignInTime(date(day));
            record.setSignInUUID(uuid.toString());
            record.setRewarded(true);
            data.getSignInRecords().add(record);
            data.markSigned(date(day), true);
        });
        data.setTotalSignInDays(days.length);
        data.setContinuousSignInDays(days.length);
        data.setLastSignInTime(date(days[days.length - 1]));
        return data;
    }

    private java.util.Date date(int day) {
        return DateUtils.format(String.format("2026-10-%02d 12:00:00", day));
    }

    private static final class Store implements PlayerSummaryStore {
        private final CompoundNBT stored = new CompoundNBT();
        private boolean failNextVerification;
        private int failVerifications;

        @Override
        public Optional<PlayerSignInSummary> load(UUID uuid) {
            return stored.contains("sakura_sign_in", 10)
                    ? Optional.of(PlayerSignInSummary.deserializeNBT(stored.getCompound("sakura_sign_in")))
                    : Optional.empty();
        }

        @Override
        public void save(UUID uuid, PlayerSignInSummary summary) {
            stored.put("sakura_sign_in", summary.serializeNBT());
        }

        @Override
        public void saveAndVerify(UUID uuid, PlayerSignInSummary summary) throws IOException {
            save(uuid, summary);
            if (failNextVerification || failVerifications > 0) {
                failNextVerification = false;
                failVerifications = Math.max(0, failVerifications - 1);
                throw new IOException("verification failure");
            }
        }
    }
}
