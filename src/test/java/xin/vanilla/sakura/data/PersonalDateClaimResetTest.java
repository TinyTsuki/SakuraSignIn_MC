package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.forge.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.internal.forge.storage.AtomicNbtFiles;
import xin.vanilla.banira.common.util.DateUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class PersonalDateClaimResetTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final UUID uuid = UUID.randomUUID();
    private final Store store = new Store();

    @Test
    public void resetsOnePresetWithoutChangingDatesOrOtherClaimsAfterReload() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        repo.save(uuid, data);
        assertEquals(1, reset(repo, data, "birthday"));
        repo.save(uuid, data);
        PlayerSignInData loaded = repo.load(uuid);
        assertEquals("", loaded.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        assertEquals(10, loaded.getPersonalDateSlots().get(0).getMonth());
        assertEquals(9, loaded.getPersonalDateSlots().get(0).getDay());
        assertEquals("MONTHLY:2026-10", loaded.getPersonalDateSlots().get(1).getLastClaimedOccurrenceKey());
        assertTrue(loaded.isSignedOn(DateUtils.format("2026-10-09")));
        assertTrue(loaded.isRewardedOn(DateUtils.format("2026-10-09")));
        assertEquals(17, loaded.getSignInCard());
        assertEquals(24, loaded.getTotalSignInDays());
        assertEquals(6, loaded.getContinuousSignInDays());
    }

    @Test
    public void resetsAllIncludingInertRemovedPresetClaims() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        data.getPersonalDateSlots().add(new PlayerPersonalDateSlot(
                "removed", 0, CalendarIds.GREGORIAN, 0, 0, "YEARLY:2025"));
        repo.save(uuid, data);
        assertEquals(3, reset(repo, data, "all"));
        repo.save(uuid, data);
        for (PlayerPersonalDateSlot slot : repo.load(uuid).getPersonalDateSlots()) {
            assertEquals("", slot.getLastClaimedOccurrenceKey());
        }
    }

    @Test
    public void missingOrAlreadyEmptyPresetIsNoOp() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        repo.save(uuid, data);
        CompoundTag before = data.serializeNBT();
        assertEquals(0, reset(repo, data, "unknown"));
        assertEquals(before, data.serializeNBT());
        assertEquals(1, reset(repo, data, "birthday"));
        assertEquals(0, reset(repo, data, "birthday"));
    }

    @Test
    public void resetDoesNotRewriteHistoryAndBacksUpOriginalClaims() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        SignInRecord record = new SignInRecord();
        record.setSignInTime(DateUtils.format("2026-10-09 12:00:00"));
        record.setCompensateTime(record.getSignInTime());
        record.setRewarded(true);
        data.setSignInRecords(Collections.singletonList(record));
        repo.save(uuid, data);
        Path month = temporary.getRoot().toPath().resolve("sakura_sign_in/history")
                .resolve(uuid.toString()).resolve("2026-10.nbt");
        byte[] original = Files.readAllBytes(month);
        java.nio.file.attribute.FileTime time = Files.getLastModifiedTime(month);
        assertEquals(1, reset(repo, data, "birthday"));
        assertArrayEquals(original, Files.readAllBytes(month));
        assertEquals(time, Files.getLastModifiedTime(month));
        CompoundTag backup = backup();
        assertEquals("personalDateReset", backup.getString("operation"));
        assertEquals("YEARLY:2026", backup.getCompound("beforeSummary")
                .getList("personalDateSlots", 10).getCompound(0).getString("lastClaimedOccurrenceKey"));
        assertFalse(backup.contains("beforeHistory"));
    }

    @Test
    public void failedVerifiedResetRollsBackWithoutMutatingLiveClaims() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        repo.save(uuid, data);
        CompoundTag before = data.serializeNBT();
        store.failNext = true;
        try {
            reset(repo, data, "birthday");
            fail("Expected verified save failure");
        } catch (IOException expected) {
            assertEquals("reset save failed", expected.getMessage());
        }
        assertEquals(before, data.serializeNBT());
        assertEquals("YEARLY:2026", repo.load(uuid).getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
    }

    @Test
    public void interruptedSummaryOnlyResetRecoversWithoutTouchingHistory() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        repo.save(uuid, data);
        assertEquals(1, reset(repo, data, "birthday"));
        CompoundTag backup = backup();
        store.save(uuid, PlayerSignInSummary.deserializeNBT(backup.getCompound("beforeSummary")));
        AtomicNbtFiles.write(temporary.getRoot().toPath().resolve("sakura_sign_in/deletions")
                .resolve(uuid.toString()).resolve("pending.nbt"), backup);
        assertEquals("", repo.load(uuid).getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        assertFalse(Files.exists(temporary.getRoot().toPath().resolve("sakura_sign_in/history")
                .resolve(uuid.toString())));
    }

    @Test
    public void ordinarySaveStillRejectsUntrustedCursorClearing() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        repo.save(uuid, data);
        data.getPersonalDateSlots().get(0).setLastClaimedOccurrenceKey("");
        repo.save(uuid, data);
        assertEquals("YEARLY:2026", repo.load(uuid).getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
    }

    @Test
    public void explicitPresetScopeCanResetPresetNamedAllWithoutOtherClaims() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        data.getPersonalDateSlots().add(new PlayerPersonalDateSlot(
                "all", 0, CalendarIds.GREGORIAN, 10, 9, "YEARLY:2026"));
        repo.save(uuid, data);
        int count = repo.resetPersonalDateClaims(uuid, data, "all", false);
        assertEquals(1, count);
        assertEquals("YEARLY:2026", repo.load(uuid).getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        assertEquals("", repo.load(uuid).getPersonalDateSlots().get(2).getLastClaimedOccurrenceKey());
    }

    private CompoundTag backup() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(temporary.getRoot().toPath()
                .resolve("sakura_sign_in/deletions").resolve(uuid.toString()))) {
            return AtomicNbtFiles.read(files.filter(path -> path.getFileName().toString().startsWith("backup-"))
                    .findFirst().get());
        }
    }

    @Test
    public void dualRecurrenceClaimsSurviveOrdinaryMergeAndResetTogether() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        data.getPersonalDateSlots().get(0).setLastClaimedOccurrenceKey("MONTHLY:2026-10");
        repo.save(uuid, data);
        data.setPersonalDateSlots(Arrays.asList(
                new PlayerPersonalDateSlot("birthday", 0, CalendarIds.GREGORIAN, 10, 9, "YEARLY:2026"),
                data.getPersonalDateSlots().get(1)));
        repo.save(uuid, data);
        PlayerPersonalDateSlot restored = repo.load(uuid).getPersonalDateSlots().get(0);
        assertEquals("YEARLY:2026", restored.claimedKey(xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence.YEARLY));
        assertEquals("MONTHLY:2026-10", restored.claimedKey(xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence.MONTHLY));
        assertEquals(1, reset(repo, data, "birthday"));
        repo.save(uuid, data);
        restored = repo.load(uuid).getPersonalDateSlots().get(0);
        assertEquals("", restored.claimedKey(xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence.YEARLY));
        assertEquals("", restored.claimedKey(xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence.MONTHLY));
    }

    @Test
    public void dualRecurrenceClaimsRemainIntactWhenResetPersistenceFails() throws Exception {
        PlayerSignInDataRepository repo = repository();
        PlayerSignInData data = player();
        data.getPersonalDateSlots().get(0).setLastClaimedOccurrenceKey("MONTHLY:2026-10");
        repo.save(uuid, data);
        store.failNext = true;
        assertThrows(IOException.class, () -> reset(repo, data, "birthday"));
        PlayerPersonalDateSlot restored = repo.load(uuid).getPersonalDateSlots().get(0);
        assertEquals("YEARLY:2026", restored.claimedKey(xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence.YEARLY));
        assertEquals("MONTHLY:2026-10", restored.claimedKey(xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence.MONTHLY));
    }

    private int reset(PlayerSignInDataRepository repo, PlayerSignInData data, String preset) throws Exception {
        return repo.resetPersonalDateClaims(uuid, data, preset);
    }

    private PlayerSignInDataRepository repository() {
        return new PlayerSignInDataRepository(store,
                new MonthlySignInHistoryRepository(temporary.getRoot().toPath()));
    }

    private PlayerSignInData player() {
        PlayerSignInData data = new PlayerSignInData();
        data.setPersonalDateSlots(Arrays.asList(
                new PlayerPersonalDateSlot("birthday", 0, CalendarIds.GREGORIAN, 10, 9, "YEARLY:2026"),
                new PlayerPersonalDateSlot("payday", 0, CalendarIds.GREGORIAN, 0, 9, "MONTHLY:2026-10")));
        data.markSigned(DateUtils.format("2026-10-09"), true);
        data.setLastSignInTime(DateUtils.format("2026-10-09 12:00:00"));
        data.setTotalSignInDays(24);
        data.setContinuousSignInDays(6);
        data.setSignInCard(17);
        return data;
    }

    private static final class Store implements PlayerSummaryStore {
        private CompoundTag stored;
        private boolean failNext;
        public Optional<PlayerSignInSummary> load(UUID uuid) {
            return stored == null ? Optional.empty() : Optional.of(PlayerSignInSummary.deserializeNBT(stored));
        }
        public void save(UUID uuid, PlayerSignInSummary summary) { stored = summary.serializeNBT(); }
        public void saveAndVerify(UUID uuid, PlayerSignInSummary summary) throws IOException {
            save(uuid, summary);
            if (failNext) { failNext = false; throw new IOException("reset save failed"); }
        }
    }
}
