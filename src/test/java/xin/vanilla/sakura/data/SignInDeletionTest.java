package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.neoforge.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.internal.neoforge.storage.AtomicNbtFiles;
import xin.vanilla.sakura.internal.neoforge.player.NeoForgePlayerSignInDataService;
import xin.vanilla.sakura.test.BaniraTestPlatform;
import xin.vanilla.banira.api.Banira;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.banira.platform.BaniraPathService;
import xin.vanilla.banira.platform.BaniraPlayerDataService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
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
        summaries.stored.put("other_mod", new CompoundTag());
        CompoundTag other = summaries.stored.getCompound("other_mod");
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
        CompoundTag before = data.serializeNBT();
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
        CompoundTag before = data.serializeNBT();
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
            CompoundTag root = AtomicNbtFiles.read(backup);
            assertEquals(1, root.getCompound("beforeSummary").getInt("totalSignInDays"));
            assertEquals(1, root.getCompound("beforeHistory").getList("records", 10).size());
        }
    }

    @Test
    public void incompleteRecoveryJournalCannotOverwriteSummary() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        CompoundTag journal = new CompoundTag();
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
        CompoundTag journal;
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
        CompoundTag original = AtomicNbtFiles.read(month);
        original.putString("extension", "unchanged");
        AtomicNbtFiles.write(month, original);
        assertTrue(delete(repository, data, today));
        assertTrue(repository.load(uuid).isSignedOn(otherDay));
        assertEquals("unchanged", AtomicNbtFiles.read(month).getString("extension"));
        assertFalse(delete(repository, data, today));
    }

    @Test
    public void preliminaryFailurePreservesDirtyServerCache() throws Exception {
        BaniraTestPlatform.install();
        BaniraPlatform previous = Banira.platform();
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pathService")) throw new IllegalStateException("history unavailable");
                    return method.invoke(previous, args);
                }));
        Field field = NeoForgePlayerSignInDataService.class.getDeclaredField("serverCache");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, IPlayerSignInData> cache = (Map<UUID, IPlayerSignInData>) field.get(NeoForgePlayerSignInDataService.INSTANCE);
        PlayerSignInData dirty = signed(9);
        dirty.setSignInCard(11);
        dirty.setPersonalDateSlots(Collections.singletonList(new PlayerPersonalDateSlot(
                "birthday", 0, CalendarIds.GREGORIAN, 10, 9, "YEARLY:2026")));
        CompoundTag before = dirty.serializeNBT();
        cache.put(uuid, dirty);
        try {
            try {
                NeoForgePlayerSignInDataService.INSTANCE.deleteDay(uuid, today, today);
                fail("Expected unavailable history");
            } catch (IllegalStateException expected) {
                assertEquals("history unavailable", expected.getMessage());
            }
            assertSame(dirty, cache.get(uuid));
            assertEquals(before, cache.get(uuid).serializeNBT());
        } finally {
            cache.remove(uuid);
            NeoForgePlayerSignInDataService.INSTANCE.removeServer(uuid);
            BaniraPlatforms.install(previous);
        }
    }

    @Test
    public void prunedLatestMakeUpActionKeepsItsCooldownTimestamp() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData data = signed(8, 9);
        data.markSigned(DateUtils.format("2026-09-30 12:00:00"), true);
        data.setTotalSignInDays(3);
        java.util.Date makeUpTime = DateUtils.format("2026-10-09 15:00:00");
        data.setLastSignInTime(makeUpTime);
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        assertEquals(makeUpTime, repository.load(uuid).getLastSignInTime());
        assertEquals(2, repository.load(uuid).getTotalSignInDays());
    }

    @Test
    public void recoveredDeletionMustReloadCacheEvenAfterJournalRemoval() throws Exception {
        PlayerSignInDataRepository repository = repository();
        PlayerSignInData stale = signed(9);
        PlayerSignInData data = signed(9);
        repository.save(uuid, data);
        assertTrue(delete(repository, data, today));
        Path world = temporary.getRoot().toPath();
        Path directory = world.resolve("sakura_sign_in/deletions").resolve(uuid.toString());
        CompoundTag journal;
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            journal = AtomicNbtFiles.read(files.filter(path -> path.getFileName().toString().startsWith("backup-"))
                    .findFirst().get());
        }
        Path pending = directory.resolve("pending.nbt");
        AtomicNbtFiles.write(pending, journal);
        CompoundTag root = new CompoundTag();
        root.put("sakura_sign_in", journal.getCompound("beforeSummary"));
        BaniraTestPlatform.install();
        BaniraPlatform previous = Banira.platform();
        BaniraPathService paths = (BaniraPathService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPathService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("worldDataPath")) return world;
                    if (method.getName().equals("playerDataPath")) return world.resolve("playerdata");
                    throw new UnsupportedOperationException(method.getName());
                });
        BaniraPlayerDataService players = (BaniraPlayerDataService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPlayerDataService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getOrCreate": return root.getCompound((String) args[1]);
                        case "put": root.put((String) args[1], (CompoundTag) args[2]); return null;
                        case "flush": AtomicNbtFiles.write(world.resolve("playerdata").resolve(uuid + ".nbt"), root); return null;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pathService")) return paths;
                    if (method.getName().equals("playerDataService")) return players;
                    return method.invoke(previous, args);
                }));
        Field field = NeoForgePlayerSignInDataService.class.getDeclaredField("serverCache");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, IPlayerSignInData> cache = (Map<UUID, IPlayerSignInData>) field.get(NeoForgePlayerSignInDataService.INSTANCE);
        Field recovery = NeoForgePlayerSignInDataService.class.getDeclaredField("recoveryNeeded");
        recovery.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<UUID> needed = (Set<UUID>) recovery.get(NeoForgePlayerSignInDataService.INSTANCE);
        cache.put(uuid, stale); needed.add(uuid);
        Method retry = NeoForgePlayerSignInDataService.class.getDeclaredMethod("recoverPending", UUID.class);
        retry.setAccessible(true);
        Path broken = world.resolve("sakura_sign_in/history").resolve(uuid.toString()).resolve("2026-09.nbt");
        Files.write(broken, new byte[]{0, 1});
        try {
            try {
                retry.invoke(NeoForgePlayerSignInDataService.INSTANCE, uuid);
                fail("Expected unreadable unrelated history");
            } catch (InvocationTargetException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
            assertFalse(Files.exists(pending));
            assertSame(stale, cache.get(uuid));
            Files.delete(broken);
            retry.invoke(NeoForgePlayerSignInDataService.INSTANCE, uuid);
            assertFalse(cache.get(uuid).isSignedOn(date(9)));
            assertEquals(0, cache.get(uuid).getTotalSignInDays());
            Method persist = NeoForgePlayerSignInDataService.class.getDeclaredMethod("persist", UUID.class);
            persist.setAccessible(true);
            persist.invoke(NeoForgePlayerSignInDataService.INSTANCE, uuid);
            assertEquals(0, root.getCompound("sakura_sign_in").getInt("totalSignInDays"));
        } finally {
            Files.deleteIfExists(broken);
            cache.remove(uuid);
            NeoForgePlayerSignInDataService.INSTANCE.removeServer(uuid);
            BaniraPlatforms.install(previous);
        }
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
        private final CompoundTag stored = new CompoundTag();
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
