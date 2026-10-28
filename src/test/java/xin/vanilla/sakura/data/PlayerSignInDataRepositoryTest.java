package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.lottery.LotteryDrawState;
import xin.vanilla.sakura.data.migration.LegacyPlayerData;
import xin.vanilla.sakura.data.migration.LegacyPlayerDataParser;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.player.HistoryRetentionPolicy;
import xin.vanilla.sakura.data.player.MonthSignInIndex;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.forge.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.internal.forge.storage.AtomicNbtFiles;
import xin.vanilla.sakura.reward.RewardList;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * 模拟服务端重启，验证拆分存储可以完整恢复运行时数据。
 */
public class PlayerSignInDataRepositoryTest {
    private static final UUID FIXTURE_UUID = UUID.fromString("00000000-0000-0000-0000-000000001665");
    private static final int RECORD_COUNT = 256;
    private static final int WARMUP_PAIRS = 8;
    private static final int SAMPLE_PAIRS = 7;
    private static final String ALLOCATION_MODE_ENV = "SAKURA_STORAGE_ALLOCATION_MODE";
    private static final String REFERENCE_COMMIT = "e621785396b8ba915de6681a4178a3b917538386";
    private static final String REFERENCE_REPOSITORY_BLOB = "1e9ff5c816431700bc9e04d2ac2f13efff2c4df5";
    private static final String REFERENCE_PARSER_BLOB = "7b0be1d1fe4ccd6cf34ee2a312a5dc3ade3705ad";
    private static final String REFERENCE_DATA_BLOB = "3cff80c04be9db236149278b45d2dde3f3015b7e";
    // Forge16 baseline r1: all seven summary pairs were 3902824 / 3902656 (ratio 1.0000430476).
    // A 1% paired-median improvement exceeds the observed 168-byte offset by over 232 times.
    private static final double MAX_SUMMARY_ALLOCATION_RATIO = 0.99;
    private static volatile Object allocationProbe;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

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

    @Test
    public void normalSaveMatchesLegacyChainAcross256RecordsAndPreviousPermanentState() throws Exception {
        SaveLane actual = lane("differential-actual", false, fixture());
        SaveLane legacy = lane("differential-legacy", true, fixture());
        // The built-in server cache is populated by this load path, including migration and recovery.
        assertEquals(PlayerSignInData.class, actual.repository.load(FIXTURE_UUID).getClass());
        seedPrevious(actual);
        seedPrevious(legacy);
        CompoundTag before = actual.data.serializeNBT();
        Path capabilityBackup = actual.world.resolve("backups/sakura_sign_in/legacy-capability/original.nbt");
        byte[] capabilityBytes = Files.readAllBytes(capabilityBackup);

        actual.save();
        legacy.save();
        assertEquals(PlayerSignInData.class, actual.repository.load(FIXTURE_UUID).getClass());
        assertEquivalent(actual, legacy);
        assertEquals(4096, actual.summaries.stored.getInt("totalSignInDays"));
        assertEquals(33, actual.summaries.stored.getInt("continuousSignInDays"));
        assertEquals(9, actual.histories.loadAll(FIXTURE_UUID).stream()
                .map(record -> YearMonth.from(record.getCompensateTime().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()))).distinct().count());
        assertEquals(RECORD_COUNT, actual.histories.loadAll(FIXTURE_UUID).size());
        assertTrue(actual.data.getMonthIndexes().get("2020-02").isRewarded(29));
        CompoundTag migration = actual.summaries.stored.getCompound("migration");
        assertTrue(migration.getBoolean("legacyCapabilityMigrated"));
        assertEquals("backups/sakura_sign_in/legacy-capability/original.nbt",
                migration.getString("legacyCapabilityBackup"));
        assertArrayEquals(capabilityBytes, Files.readAllBytes(capabilityBackup));
        ListTag slots = actual.summaries.stored.getList("personalDateSlots", 10);
        assertEquals(2, slots.size());
        assertEquals("YEARLY:2026", slots.getCompound(0).getCompound("claimedPeriodCursors")
                .getString("YEARLY"));
        assertEquals("MONTHLY:2026-10", slots.getCompound(0).getCompound("claimedPeriodCursors")
                .getString("MONTHLY"));
        assertEquals("removed", slots.getCompound(1).getString("presetId"));
        CompoundTag after = actual.data.serializeNBT();
        before.remove("monthIndexes");
        after.remove("monthIndexes");
        assertEquals("Only merged month indexes are written back to live data", before, after);
    }

    @Test
    public void normalSavePreservesCdkPendingRewardsAndClampsLotteryWithoutMutatingInput() throws Exception {
        SaveLane actual = lane("normalization-actual", false, fixture());
        SaveLane legacy = lane("normalization-legacy", true, fixture());
        for (SaveLane lane : Arrays.asList(actual, legacy)) {
            LotteryDrawState state = lane.data.getLotteryDrawStates().get(0);
            state.setPeriodDraws(-2);
            state.setTotalDraws(-3);
            state.setLastDrawEpochMillis(-4);
        }
        actual.save();
        legacy.save();
        assertEquivalent(actual, legacy);
        PlayerSignInData loaded = actual.repository.load(FIXTURE_UUID);
        assertEquals("WELCOME", loaded.getCdkRecords().get(0).key());
        assertEquals(Boolean.TRUE, loaded.getCdkRecords().get(0).value().value());
        assertEquals(Boolean.FALSE, loaded.getCdkRecords().get(1).value().value());
        assertEquals("2026-10-09", loaded.getOnlineTimeBaselineDate());
        assertEquals(1234, loaded.getOnlineTimeBaselineTicks());
        LotteryDrawState lottery = loaded.getLotteryDrawStates().get(0);
        assertEquals("daily", lottery.getPoolId());
        assertEquals("2026-10-09", lottery.getPeriodKey());
        assertEquals(0, lottery.getPeriodDraws());
        assertEquals(0, lottery.getTotalDraws());
        assertEquals(0L, lottery.getLastDrawEpochMillis());
        assertEquals(-2, actual.data.getLotteryDrawStates().get(0).getPeriodDraws());
        assertEquals(1, loaded.getSignInRecords().get(0).getPersonalDateRewards().size());
        assertFalse(loaded.getSignInRecords().get(0).isRewarded());
        assertEquals("YEARLY:2025", loaded.getSignInRecords().get(0)
                .getPersonalDateRewards().get(0).getOccurrenceKey());
        CompoundTag saved = actual.summaries.stored.copy();
        actual.data.getCdkRecords().clear();
        actual.data.getLotteryDrawStates().get(0).setTotalDraws(999);
        actual.data.getPersonalDateSlots().get(0).setLastClaimedOccurrenceKey("");
        actual.data.getMonthIndexes().clear();
        assertEquals(saved, actual.summaries.capturedSummary.serializeNBT());
    }

    @Test
    public void staleOrdinarySaveCannotResurrectDeletedOrStrippedMonthsOrDropPendingQueue() throws Exception {
        for (HistoryRetentionPolicy policy : HistoryRetentionPolicy.values()) {
            SaveLane actual = lane("retention-actual-" + policy, false, fixture());
            SaveLane legacy = lane("retention-legacy-" + policy, true, fixture());
            actual.save();
            legacy.save();
            for (SaveLane lane : Arrays.asList(actual, legacy)) {
                lane.histories.applyRetention(FIXTURE_UUID, YearMonth.of(2026, 10), 1, policy);
                Map<String, byte[]> bytes = historyBytes(lane);
                Map<String, FileTime> times = historyTimes(lane);
                lane.data.setLanguage("en_us");
                lane.save();
                assertHistoryUnchanged(lane, bytes, times);
                PlayerSignInData loaded = lane.repository.load(FIXTURE_UUID);
                assertEquals(4096, loaded.getTotalSignInDays());
                assertEquals(33, loaded.getContinuousSignInDays());
                assertTrue(loaded.isSignedOn(DateUtils.format("2025-09-13 12:00:00")));
                assertEquals(1, loaded.getSignInRecords().get(0).getPersonalDateRewards().size());
                if (policy == HistoryRetentionPolicy.DELETE_MONTH_FILE) {
                    assertEquals(Collections.singleton("2025-01.nbt"), bytes.keySet());
                    assertFalse(Files.exists(historyFile(lane, "2025-09")));
                } else {
                    assertEquals(9, bytes.size());
                    assertEquals("[]", AtomicNbtFiles.read(historyFile(lane, "2025-09"))
                            .getList("records", 10).getCompound(0).getString("rewardList"));
                }
            }
            assertEquivalent(actual, legacy);
        }
    }

    @Test
    public void deletionAndExplicitResetSurviveFollowingNormalSaveWithBackupsIntact() throws Exception {
        SaveLane lane = lane("delete-reset", false, fixture());
        lane.save();
        assertTrue(lane.repository.deleteDay(FIXTURE_UUID, lane.data,
                LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3)));
        CompoundTag deletion = backup(lane, "");
        assertEquals(31, deletion.getCompound("beforeHistory").getList("records", 10).size());
        assertEquals(30, deletion.getCompound("afterHistory").getList("records", 10).size());
        byte[] originalBackup = Files.readAllBytes(backupFile(lane, ""));
        assertEquals(1, lane.repository.resetPersonalDateClaims(FIXTURE_UUID, lane.data, "birthday", false));
        CompoundTag reset = backup(lane, "personalDateReset");
        assertEquals("YEARLY:2026", reset.getCompound("beforeSummary")
                .getList("personalDateSlots", 10).getCompound(0).getString("lastClaimedOccurrenceKey"));
        lane.save();
        PlayerSignInData loaded = lane.repository.load(FIXTURE_UUID);
        assertFalse(loaded.isSignedOn(DateUtils.format("2025-01-02 12:00:00")));
        assertFalse(loaded.isRewardedOn(DateUtils.format("2025-01-02 12:00:00")));
        assertEquals(4095, loaded.getTotalSignInDays());
        assertEquals("", loaded.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        assertTrue(loaded.getPersonalDateSlots().get(0).getClaimedPeriodCursors().isEmpty());
        assertEquals(1, loaded.getSignInRecords().get(0).getPersonalDateRewards().size());
        assertEquals(7, loaded.getLotteryDrawStates().get(0).getTotalDraws());
        assertEquals(2, loaded.getCdkRecords().size());
        assertArrayEquals(originalBackup, Files.readAllBytes(backupFile(lane, "")));
    }

    @Test
    public void pendingResetRecoveryPrecedesNormalSaveAndInvalidJournalBlocksAllWrites() throws Exception {
        SaveLane lane = lane("recover-reset", false, fixture());
        lane.save();
        lane.repository.resetPersonalDateClaims(FIXTURE_UUID, lane.data, "birthday", false);
        CompoundTag reset = backup(lane, "personalDateReset");
        Path pending = lane.world.resolve("sakura_sign_in/deletions")
                .resolve(FIXTURE_UUID.toString()).resolve("pending.nbt");
        lane.summaries.stored = reset.getCompound("beforeSummary").copy();
        AtomicNbtFiles.write(pending, reset);
        lane.save();
        assertFalse(Files.exists(pending));
        assertEquals("", lane.repository.load(FIXTURE_UUID).getPersonalDateSlots().get(0)
                .getLastClaimedOccurrenceKey());
        Map<String, byte[]> bytes = historyBytes(lane);
        Map<String, FileTime> times = historyTimes(lane);
        CompoundTag summary = lane.summaries.stored.copy();
        int calls = lane.summaries.saveCalls;
        AtomicNbtFiles.write(pending, new CompoundTag());
        assertThrows(IOException.class, lane::save);
        assertEquals(calls, lane.summaries.saveCalls);
        assertEquals(summary, lane.summaries.stored);
        assertHistoryUnchanged(lane, bytes, times);
        assertTrue(Files.exists(pending));
    }

    @Test
    public void summaryFailureIsNotRetriedAndHistoryWriteStillPrecedesSummary() throws Exception {
        SaveLane lane = lane("summary-failure", false, fixture());
        lane.save();
        CompoundTag before = lane.summaries.stored.copy();
        SignInRecord extra = record(LocalDate.of(2025, 10, 1), true);
        lane.data.getSignInRecords().add(extra);
        int calls = lane.summaries.saveCalls;
        lane.summaries.failNextSave = true;
        assertThrows(IOException.class, lane::save);
        assertEquals(calls + 1, lane.summaries.saveCalls);
        assertEquals(before, lane.summaries.stored);
        assertEquals(1, AtomicNbtFiles.read(historyFile(lane, "2025-10"))
                .getList("records", 10).size());
    }

    @Test
    public void subclassAndOtherInterfaceImplementationKeepSerializedOverrideContract() throws Exception {
        for (boolean proxy : new boolean[]{false, true}) {
            SaveLane lane = lane("extension-" + proxy, false, fixture());
            SerializedOverride delegate = new SerializedOverride();
            delegate.deserializeNBT(lane.data.serializeNBT());
            IPlayerSignInData data = delegate;
            if (proxy) {
                data = (IPlayerSignInData) Proxy.newProxyInstance(IPlayerSignInData.class.getClassLoader(),
                        new Class<?>[]{IPlayerSignInData.class}, (instance, method, args) -> {
                            try {
                                return method.invoke(delegate, args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
            }
            lane.repository.save(FIXTURE_UUID, data);
            assertEquals(1, delegate.serializations);
            PlayerSignInData loaded = lane.repository.load(FIXTURE_UUID);
            assertEquals(777, loaded.getTotalSignInDays());
            assertEquals(33, loaded.getContinuousSignInDays());
            assertEquals(44, loaded.getSignInCard());
            assertEquals("extension", loaded.getLanguage());
            assertEquals(RECORD_COUNT, loaded.getSignInRecords().size());
        }
    }

    @Test
    public void sharedCustomRecordTagsKeepCollectThenParseSemanticsAndUnknownFields() throws Exception {
        SaveLane actual = lane("shared-tags-actual", false, new PlayerSignInData());
        SaveLane legacy = lane("shared-tags-legacy", true, new PlayerSignInData());
        for (SaveLane lane : Arrays.asList(actual, legacy)) {
            CompoundTag shared = record(LocalDate.of(2025, 1, 1), true).writeToNBT();
            lane.data.setSignInRecords(Arrays.asList(
                    sharedRecord(shared, "2025-01-31 23:59:59", "first"),
                    sharedRecord(shared, "not a date", "second")));
            lane.save();
        }
        assertEquivalent(actual, legacy);
        assertEquals(Collections.singleton("unknown.nbt"), historyBytes(actual).keySet());
        ListTag records = AtomicNbtFiles.read(historyFile(actual, "unknown")).getList("records", 10);
        assertEquals(2, records.size());
        for (int i = 0; i < records.size(); i++) {
            assertEquals("not a date", records.getCompound(i).getString("compensateTime"));
            assertEquals("second", records.getCompound(i).getString("extensionPayload"));
        }
        assertTrue(actual.data.getMonthIndexes().isEmpty());
    }

    @Test
    public void encodedCompensationDatesControlMonthDayAndRecordOrder() throws Exception {
        SaveLane lane = lane("encoded-date", false, new PlayerSignInData());
        CompoundTag first = record(LocalDate.of(2025, 2, 1), false).writeToNBT();
        first.putString("compensateTime", "2024-02-29 23:59:59");
        first.putString("signInTime", "2024-03-01 00:01:00");
        first.putString("signInUUID", "first");
        CompoundTag second = first.copy();
        second.putBoolean("rewarded", true);
        second.putString("signInUUID", "second");
        lane.data.setSignInRecords(Arrays.asList(encodedRecord(first), encodedRecord(second)));
        lane.save();
        assertEquals(Collections.singleton("2024-02.nbt"), historyBytes(lane).keySet());
        assertTrue(lane.data.getMonthIndexes().get("2024-02").isSigned(29));
        assertTrue(lane.data.getMonthIndexes().get("2024-02").isRewarded(29));
        ListTag records = AtomicNbtFiles.read(historyFile(lane, "2024-02")).getList("records", 10);
        assertEquals(first, records.getCompound(0));
        assertEquals(second, records.getCompound(1));
    }

    @Test
    public void aliasedMetadataSubclassEncodingFallsBackAsAWhole() throws Exception {
        SaveLane actual = lane("metadata-extension-actual", false, aliasedMetadata());
        SaveLane legacy = lane("metadata-extension-legacy", true, aliasedMetadata());
        actual.save();
        legacy.save();
        assertEquivalent(actual, legacy);
        MonthSignInIndex index = actual.summaries.capturedSummary.getMonthIndexes().get("2024-02");
        assertTrue(index.isSigned(29));
        assertFalse(index.isSigned(1));
        assertEquals("YEARLY:2027", actual.summaries.capturedSummary.getPersonalDateSlots().get(0)
                .getLastClaimedOccurrenceKey());
        assertEquals(7, actual.summaries.capturedSummary.getLotteryDrawStates().get(0).getTotalDraws());
    }

    @Test
    public void builtInPendingRecordEncodingOwnsItsListAndSnapshotTags() {
        assertTrue("A subclassable snapshot codec needs its own legacy-copy eligibility guard",
                java.lang.reflect.Modifier.isFinal(
                        xin.vanilla.sakura.data.personaldate.PersonalDateRewardSnapshot.class.getModifiers()));
        SignInRecord record = fixture().getSignInRecords().get(0);
        CompoundTag first = record.writeToNBT();
        CompoundTag second = record.writeToNBT();
        CompoundTag expected = second.copy();
        ListTag firstSnapshots = first.getList("personalDateRewards", 10);
        ListTag secondSnapshots = second.getList("personalDateRewards", 10);
        assertEquals(1, firstSnapshots.size());
        assertEquals(1, secondSnapshots.size());
        assertNotSame(first, second);
        assertNotSame(firstSnapshots, secondSnapshots);
        assertNotSame(firstSnapshots.getCompound(0), secondSnapshots.getCompound(0));
        firstSnapshots.getCompound(0).putString("rewards", "[]");
        firstSnapshots.clear();
        assertEquals(expected, second);
        assertEquals(expected, record.writeToNBT());
        record.getPersonalDateRewards().get(0).getRewards().clear();
        assertEquals(expected, second);
        assertEquals("[]", record.writeToNBT().getList("personalDateRewards", 10)
                .getCompound(0).getString("rewards"));
        record.getPersonalDateRewards().clear();
        assertEquals(expected, second);
        assertFalse(record.writeToNBT().contains("personalDateRewards"));
    }

    @Test
    public void nullLanguageFailsBeforeAnyHistoryOrSummaryWrite() throws Exception {
        SaveLane lane = lane("null-language", false, fixture());
        lane.save();
        CompoundTag before = lane.summaries.stored.copy();
        Map<String, byte[]> bytes = historyBytes(lane);
        Map<String, FileTime> times = historyTimes(lane);
        lane.data.setLanguage(null);
        lane.data.getSignInRecords().add(record(LocalDate.of(2025, 10, 1), true));
        int calls = lane.summaries.saveCalls;
        assertThrows(NullPointerException.class, lane::save);
        assertEquals(calls, lane.summaries.saveCalls);
        assertEquals(before, lane.summaries.stored);
        assertHistoryUnchanged(lane, bytes, times);
    }

    @Test
    public void recordEncodingFailureLeavesSummaryAndHistoryUntouched() throws Exception {
        SaveLane lane = lane("encoding-failure", false, fixture());
        lane.save();
        CompoundTag before = lane.summaries.stored.copy();
        Map<String, byte[]> bytes = historyBytes(lane);
        Map<String, FileTime> times = historyTimes(lane);
        lane.data.getSignInRecords().add(new SignInRecord() {
            @Override
            public CompoundTag writeToNBT() {
                throw new IllegalArgumentException("record encoding failed");
            }
        });
        int calls = lane.summaries.saveCalls;
        assertEquals("record encoding failed", assertThrows(IllegalArgumentException.class, lane::save).getMessage());
        assertEquals(calls, lane.summaries.saveCalls);
        assertEquals(before, lane.summaries.stored);
        assertHistoryUnchanged(lane, bytes, times);
    }

    @Test
    public void emptyAndNullFilteredCollectionsKeepDefaultsAndSerializedIndexMonthKeys() throws Exception {
        SaveLane lane = lane("defaults", false, new PlayerSignInData());
        lane.save();
        PlayerSignInData empty = lane.repository.load(FIXTURE_UUID);
        assertEquals(0, empty.getTotalSignInDays());
        assertEquals(0, empty.getContinuousSignInDays());
        assertEquals("client", empty.getLanguage());
        assertTrue(historyBytes(lane).isEmpty());
        lane.data.getSignInRecords().add(null);
        lane.data.getCdkRecords().add(null);
        lane.data.getPersonalDateSlots().add(null);
        lane.data.getLotteryDrawStates().add(null);
        MonthSignInIndex index = new MonthSignInIndex("2024-02");
        index.markSigned(29, true);
        lane.data.getMonthIndexes().put("caller-alias", index);
        lane.save();
        assertEquals(Collections.singleton("2024-02"), lane.data.getMonthIndexes().keySet());
        assertTrue(lane.data.getMonthIndexes().get("2024-02").isRewarded(29));
        assertTrue(lane.data.getSignInRecords().isEmpty());
        assertTrue(lane.data.getCdkRecords().isEmpty());
        assertTrue(lane.data.getPersonalDateSlots().isEmpty());
        assertTrue(lane.data.getLotteryDrawStates().isEmpty());
        assertTrue(historyBytes(lane).isEmpty());
    }

    @Test
    public void bounded256RecordAllocationBaselineOrPerformanceGate() throws Exception {
        String mode = System.getenv(ALLOCATION_MODE_ENV);
        assumeTrue("Set " + ALLOCATION_MODE_ENV + "=baseline or red to run allocation sampling",
                mode != null && !mode.isEmpty());
        assertTrue("Unsupported " + ALLOCATION_MODE_ENV + ": " + mode,
                "baseline".equals(mode) || "red".equals(mode));
        java.lang.management.ThreadMXBean platform = ManagementFactory.getThreadMXBean();
        assertTrue("A JVM with com.sun.management.ThreadMXBean is required",
                platform instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean counter = (com.sun.management.ThreadMXBean) platform;
        assertTrue("Thread allocation accounting is unsupported", counter.isThreadAllocatedMemorySupported());
        boolean wasEnabled = counter.isThreadAllocatedMemoryEnabled();
        try {
            if (!wasEnabled) counter.setThreadAllocatedMemoryEnabled(true);
            assertTrue("Thread allocation accounting could not be enabled", counter.isThreadAllocatedMemoryEnabled());
            long threadId = Thread.currentThread().getId();
            long before = counter.getThreadAllocatedBytes(threadId);
            allocationProbe = new byte[4096];
            long after = counter.getThreadAllocatedBytes(threadId);
            assertTrue("Allocation counter returned an unavailable value", before >= 0 && after >= 0);
            assertTrue("Allocation counter did not observe an escaping allocation", after > before);
            System.out.println("SAKURA_STORAGE_ALLOCATION_ENV java=" + System.getProperty("java.runtime.version")
                    + " vm=" + System.getProperty("java.vm.name") + " thread=" + threadId
                    + " records=" + RECORD_COUNT + " warmupPairs=" + WARMUP_PAIRS
                    + " measuredPairs=" + SAMPLE_PAIRS + " mode=" + mode
                    + " summaryStore=memory historyStore=real-files referenceCommit=" + REFERENCE_COMMIT
                    + " referenceRepositoryBlob=" + REFERENCE_REPOSITORY_BLOB
                    + " referenceParserBlob=" + REFERENCE_PARSER_BLOB
                    + " referenceDataBlob=" + REFERENCE_DATA_BLOB);
            for (boolean firstSave : new boolean[]{true, false}) {
                SaveLane steadyActual = null;
                SaveLane steadyLegacy = null;
                Map<String, byte[]> actualBytes = null;
                Map<String, byte[]> legacyBytes = null;
                Map<String, FileTime> actualTimes = null;
                Map<String, FileTime> legacyTimes = null;
                String workload = firstSave ? "first-save" : "summary-only";
                if (!firstSave) {
                    steadyActual = lane("allocation-steady-actual", false, fixture());
                    steadyLegacy = lane("allocation-steady-legacy", true, fixture());
                    legacySave(steadyActual);
                    legacySave(steadyLegacy);
                    actualBytes = historyBytes(steadyActual);
                    legacyBytes = historyBytes(steadyLegacy);
                    actualTimes = historyTimes(steadyActual);
                    legacyTimes = historyTimes(steadyLegacy);
                }
                long[][] samples = new long[SAMPLE_PAIRS][2];
                for (int pair = 0; pair < WARMUP_PAIRS + SAMPLE_PAIRS; pair++) {
                    SaveLane actual = firstSave ? lane("allocation-first-actual-" + pair, false, fixture()) : steadyActual;
                    SaveLane legacy = firstSave ? lane("allocation-first-legacy-" + pair, true, fixture()) : steadyLegacy;
                    String language = (pair & 1) == 0 ? "en_us" : "zh_cn";
                    actual.data.setLanguage(language);
                    legacy.data.setLanguage(language);
                    assertEquals(PlayerSignInData.class, actual.data.getClass());
                    assertEquals(PlayerSignInData.class, legacy.data.getClass());
                    assertEquals(RECORD_COUNT, actual.data.getSignInRecords().size());
                    assertEquals(RECORD_COUNT, legacy.data.getSignInRecords().size());
                    boolean actualFirst = (pair & 1) == 0;
                    long first = allocatedSave(counter, threadId, actualFirst ? actual : legacy);
                    long second = allocatedSave(counter, threadId, actualFirst ? legacy : actual);
                    if (pair >= WARMUP_PAIRS) {
                        samples[pair - WARMUP_PAIRS][0] = actualFirst ? first : second;
                        samples[pair - WARMUP_PAIRS][1] = actualFirst ? second : first;
                    }
                    assertEquivalent(actual, legacy);
                }
                if (!firstSave) {
                    assertHistoryUnchanged(steadyActual, actualBytes, actualTimes);
                    assertHistoryUnchanged(steadyLegacy, legacyBytes, legacyTimes);
                }
                for (int sample = 0; sample < SAMPLE_PAIRS; sample++) {
                    System.out.println("SAKURA_STORAGE_ALLOCATION workload=" + workload + " sample=" + sample
                            + " order=" + (((sample + WARMUP_PAIRS) & 1) == 0 ? "repository,legacy" : "legacy,repository")
                            + " repositoryBytes=" + samples[sample][0] + " legacyBytes=" + samples[sample][1]);
                }
                if (!firstSave) assertSummaryAllocationImprovement(samples, "red".equals(mode));
            }
        } finally {
            allocationProbe = null;
            if (!wasEnabled) counter.setThreadAllocatedMemoryEnabled(false);
        }
    }

    private static void assertSummaryAllocationImprovement(long[][] samples, boolean enforceTarget) {
        assertEquals(SAMPLE_PAIRS, samples.length);
        double[] ratios = new double[SAMPLE_PAIRS];
        for (int sample = 0; sample < SAMPLE_PAIRS; sample++) {
            assertTrue("Invalid repository allocation sample", samples[sample][0] > 0);
            assertTrue("Invalid legacy allocation sample", samples[sample][1] > 0);
            ratios[sample] = (double) samples[sample][0] / samples[sample][1];
        }
        Arrays.sort(ratios);
        double median = ratios[SAMPLE_PAIRS / 2];
        System.out.println("SAKURA_STORAGE_ALLOCATION_TARGET workload=summary-only pairedMedianRatio=" + median
                + " maxRatio=" + MAX_SUMMARY_ALLOCATION_RATIO + " enforced=" + enforceTarget);
        if (enforceTarget) {
            assertTrue("Summary save must allocate at least 1% less than the same-window legacy reference; "
                            + "paired median ratio=" + median + ", maximum=" + MAX_SUMMARY_ALLOCATION_RATIO,
                    median <= MAX_SUMMARY_ALLOCATION_RATIO);
        }
    }

    private static long allocatedSave(com.sun.management.ThreadMXBean counter, long threadId,
                                      SaveLane lane) throws IOException {
        assertEquals("Allocation measurement moved to a different thread", threadId, Thread.currentThread().getId());
        long before = counter.getThreadAllocatedBytes(threadId);
        lane.save();
        long after = counter.getThreadAllocatedBytes(threadId);
        assertTrue("Allocation counter became unavailable or non-monotonic", before >= 0 && after > before);
        return after - before;
    }

    private SaveLane lane(String name, boolean legacy, PlayerSignInData data) throws IOException {
        return new SaveLane(temporaryFolder.newFolder(name).toPath(), legacy, data);
    }

    private static PlayerSignInData fixture() {
        PlayerSignInData data = new PlayerSignInData();
        data.setTotalSignInDays(4096);
        data.setContinuousSignInDays(33);
        data.setLastSignInTime(DateUtils.format("2025-09-13 12:00:00"));
        data.setSignInCard(17);
        data.setAutoRewarded(true);
        data.setLanguage("zh_cn");
        data.setCdkRecords(Arrays.asList(
                new KeyValue<>("WELCOME", new KeyValue<>(DateUtils.format("2025-01-01 12:00:00"), true)),
                new KeyValue<>("INVALID", new KeyValue<>(DateUtils.format("2025-01-02 12:00:00"), false))));
        PlayerPersonalDateSlot birthday = new PlayerPersonalDateSlot(
                "birthday", 0, CalendarIds.GREGORIAN, 1, 1, "YEARLY:2026");
        birthday.setLastClaimedOccurrenceKey("MONTHLY:2026-10");
        birthday.setLastClaimedOccurrenceKey("YEARLY:2026");
        data.setPersonalDateSlots(Collections.singletonList(birthday));
        data.setOnlineTimeBaselineDate("2026-10-09");
        data.setOnlineTimeBaselineTicks(1234);
        LotteryDrawState lottery = new LotteryDrawState("daily");
        lottery.setPeriodKey("2026-10-09");
        lottery.setPeriodDraws(2);
        lottery.setTotalDraws(7);
        lottery.setLastDrawEpochMillis(123456L);
        data.setLotteryDrawStates(Collections.singletonList(lottery));
        for (int i = 0; i < RECORD_COUNT; i++) {
            SignInRecord record = record(LocalDate.of(2025, 1, 1).plusDays(i), i != 0);
            if (i == 0) {
                CompoundTag pending = PersonalDatePendingSnapshotTest.record();
                CompoundTag snapshot = pending.getList("personalDateRewards", 10).getCompound(0);
                snapshot.putString("occurrenceKey", "YEARLY:2025");
                snapshot.putString("targetDate", "2025-01-01");
                record.setPersonalDateRewards(SignInRecord.readFromNBT(pending).getPersonalDateRewards());
            }
            data.getSignInRecords().add(record);
        }
        return data;
    }

    private static SignInRecord record(LocalDate day, boolean rewarded) {
        CompoundTag tag = new CompoundTag();
        tag.putString("compensateTime", day + " 12:00:00");
        tag.putString("signInTime", day + " 12:00:00");
        tag.putString("signInUUID", FIXTURE_UUID.toString());
        tag.putBoolean("rewarded", rewarded);
        tag.putString("rewardList", "[{\"type\":\"sakura_sign_in:sign_in_card\",\"probability\":1,\"content\":{\"signInCard\":1}}]");
        return SignInRecord.readFromNBT(tag);
    }

    private static void seedPrevious(SaveLane lane) throws IOException {
        PlayerSignInSummary previous = new PlayerSignInSummary();
        MonthSignInIndex old = new MonthSignInIndex("2020-02");
        old.markSigned(29, true);
        previous.getMonthIndexes().put("2020-02", old);
        previous.setLegacyCapabilityMigrated(true);
        previous.setLegacyCapabilityBackup("backups/sakura_sign_in/legacy-capability/original.nbt");
        previous.setPersonalDateSlots(new ArrayList<>(lane.data.getPersonalDateSlots()));
        previous.getPersonalDateSlots().add(new PlayerPersonalDateSlot(
                "removed", 0, CalendarIds.GREGORIAN, 0, 0, "YEARLY:2025"));
        lane.summaries.save(FIXTURE_UUID, previous);
        CompoundTag backup = new CompoundTag();
        backup.putString("source", "legacy-capability");
        backup.putInt("totalSignInDays", 4096);
        AtomicNbtFiles.write(lane.world.resolve("backups/sakura_sign_in/legacy-capability/original.nbt"), backup);
        lane.data.getPersonalDateSlots().get(0).setLastClaimedOccurrenceKey("");
    }

    // Frozen orchestration from REFERENCE_COMMIT/REFERENCE_REPOSITORY_BLOB; codecs retain production identity.
    private static void legacySave(SaveLane lane) throws IOException {
        lane.histories.deletionJournal().recover(FIXTURE_UUID, lane.summaries);
        CompoundTag serialized = lane.data.serializeNBT();
        serialized.putInt("continuousSignInDays", lane.data.getContinuousSignInDays());
        LegacyPlayerData parsed = lane.legacyParser.parse(serialized);
        Optional<PlayerSignInSummary> previous = lane.summaries.load(FIXTURE_UUID);
        if (previous.isPresent()) {
            previous.get().getMonthIndexes().forEach((month, index) -> parsed.getSummary().getMonthIndexes()
                    .computeIfAbsent(month, MonthSignInIndex::new).merge(index));
            parsed.getSummary().setLegacyCapabilityMigrated(previous.get().isLegacyCapabilityMigrated());
            parsed.getSummary().setLegacyCapabilityBackup(previous.get().getLegacyCapabilityBackup());
            Map<String, PlayerPersonalDateSlot> slots = new LinkedHashMap<>();
            previous.get().getPersonalDateSlots().forEach(slot -> slots.put(
                    slot.getPresetId() + "#" + slot.getSlotIndex(),
                    PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT())));
            parsed.getSummary().getPersonalDateSlots().forEach(slot -> {
                String key = slot.getPresetId() + "#" + slot.getSlotIndex();
                slot.mergeClaimState(slots.get(key));
                slots.put(key, PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT()));
            });
            parsed.getSummary().setPersonalDateSlots(new ArrayList<>(slots.values()));
        }
        lane.data.setMonthIndexes(parsed.getSummary().getMonthIndexes());
        Set<String> changed = new LinkedHashSet<>();
        parsed.getRecordsByMonth().keySet().forEach(month -> {
            if (!previous.isPresent() || !Objects.equals(previous.get().getMonthIndexes().get(month),
                    parsed.getSummary().getMonthIndexes().get(month))) changed.add(month);
        });
        lane.histories.saveMonths(FIXTURE_UUID, parsed, changed);
        lane.summaries.save(FIXTURE_UUID, parsed.getSummary());
    }

    private static void assertEquivalent(SaveLane actual, SaveLane legacy) throws IOException {
        assertEquals(legacy.summaries.stored, actual.summaries.stored);
        assertEquals(legacy.data.serializeNBT(), actual.data.serializeNBT());
        Map<String, byte[]> actualFiles = historyBytes(actual);
        Map<String, byte[]> legacyFiles = historyBytes(legacy);
        assertEquals(legacyFiles.keySet(), actualFiles.keySet());
        for (String name : actualFiles.keySet()) {
            assertEquals("History NBT differs: " + name,
                    AtomicNbtFiles.read(historyDirectory(legacy).resolve(name)),
                    AtomicNbtFiles.read(historyDirectory(actual).resolve(name)));
        }
    }

    private static Path historyDirectory(SaveLane lane) {
        return lane.world.resolve("sakura_sign_in/history").resolve(FIXTURE_UUID.toString());
    }

    private static Path historyFile(SaveLane lane, String month) {
        return historyDirectory(lane).resolve(month + ".nbt");
    }

    private static Map<String, byte[]> historyBytes(SaveLane lane) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        if (!Files.isDirectory(historyDirectory(lane))) return result;
        try (Stream<Path> paths = Files.list(historyDirectory(lane))) {
            for (Path path : paths.collect(Collectors.toList())) {
                result.put(path.getFileName().toString(), Files.readAllBytes(path));
            }
        }
        return result;
    }

    private static Map<String, FileTime> historyTimes(SaveLane lane) throws IOException {
        Map<String, FileTime> result = new TreeMap<>();
        for (String name : historyBytes(lane).keySet()) {
            result.put(name, Files.getLastModifiedTime(historyDirectory(lane).resolve(name)));
        }
        return result;
    }

    private static void assertHistoryUnchanged(SaveLane lane, Map<String, byte[]> bytes,
                                               Map<String, FileTime> times) throws IOException {
        Map<String, byte[]> after = historyBytes(lane);
        assertEquals(bytes.keySet(), after.keySet());
        for (String name : bytes.keySet()) assertArrayEquals(name, bytes.get(name), after.get(name));
        assertEquals(times, historyTimes(lane));
    }

    private static Path backupFile(SaveLane lane, String operation) throws IOException {
        Path directory = lane.world.resolve("sakura_sign_in/deletions").resolve(FIXTURE_UUID.toString());
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().startsWith("backup-"))
                    .collect(Collectors.toList())) {
                if (operation.equals(AtomicNbtFiles.read(path).getString("operation"))) return path;
            }
        }
        throw new AssertionError("Missing backup for operation: " + operation);
    }

    private static CompoundTag backup(SaveLane lane, String operation) throws IOException {
        return AtomicNbtFiles.read(backupFile(lane, operation));
    }

    private static SignInRecord encodedRecord(CompoundTag encoded) {
        return new SignInRecord() {
            @Override
            public CompoundTag writeToNBT() {
                return encoded;
            }
        };
    }

    private static PlayerSignInData aliasedMetadata() {
        PlayerSignInData data = new PlayerSignInData();
        CompoundTag index = new MonthSignInIndex("2024-02").serializeNBT();
        index.putInt("signedDays", 1);
        CompoundTag slot = new PlayerPersonalDateSlot("birthday", 0, CalendarIds.GREGORIAN,
                1, 1, "YEARLY:2026").serializeNBT();
        LotteryDrawState state = new LotteryDrawState("daily");
        state.setTotalDraws(7);
        CompoundTag lottery = state.serializeNBT();
        data.getMonthIndexes().put("alias", new MonthSignInIndex("caller-month") {
            @Override
            public CompoundTag serializeNBT() {
                return index;
            }
        });
        data.getPersonalDateSlots().add(new PlayerPersonalDateSlot() {
            @Override
            public CompoundTag serializeNBT() {
                index.putInt("signedDays", 1 << 28);
                return slot;
            }
        });
        data.getLotteryDrawStates().add(new LotteryDrawState() {
            @Override
            public CompoundTag serializeNBT() {
                slot.putString("lastClaimedOccurrenceKey", "YEARLY:2027");
                return lottery;
            }
        });
        return data;
    }

    private static SignInRecord sharedRecord(CompoundTag shared, String date, String payload) {
        return new SignInRecord() {
            @Override
            public CompoundTag writeToNBT() {
                shared.putString("compensateTime", date);
                shared.putString("extensionPayload", payload);
                return shared;
            }
        };
    }

    private static final class SerializedOverride extends PlayerSignInData {
        private int serializations;

        @Override
        public CompoundTag serializeNBT() {
            serializations++;
            CompoundTag tag = super.serializeNBT();
            tag.putInt("totalSignInDays", 777);
            tag.putInt("continuousSignInDays", -7);
            tag.putInt("signInCard", 44);
            tag.putString("language", "extension");
            return tag;
        }
    }

    private static final class SaveLane {
        private final Path world;
        private final boolean legacy;
        private final PlayerSignInData data;
        private final LegacyPlayerDataParser legacyParser = new LegacyPlayerDataParser();
        private final MemorySummaryStore summaries = new MemorySummaryStore();
        private final MonthlySignInHistoryRepository histories;
        private final PlayerSignInDataRepository repository;

        private SaveLane(Path world, boolean legacy, PlayerSignInData data) {
            this.world = world;
            this.legacy = legacy;
            this.data = data;
            histories = new MonthlySignInHistoryRepository(world);
            repository = new PlayerSignInDataRepository(summaries, histories);
        }

        private void save() throws IOException {
            if (legacy) legacySave(this);
            else repository.save(FIXTURE_UUID, data);
        }
    }

    private static final class MemorySummaryStore implements PlayerSummaryStore {
        private CompoundTag stored;
        private PlayerSignInSummary capturedSummary;
        private boolean failNextSave;
        private int saveCalls;
        private int verifiedSaveCalls;

        @Override
        public Optional<PlayerSignInSummary> load(UUID playerUuid) {
            return stored == null
                    ? Optional.empty()
                    : Optional.of(PlayerSignInSummary.deserializeNBT(stored));
        }

        @Override
        public void save(UUID playerUuid, PlayerSignInSummary summary) throws IOException {
            saveCalls++;
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("summary save failed");
            }
            capturedSummary = summary;
            stored = summary.serializeNBT();
        }

        @Override
        public void saveAndVerify(UUID playerUuid, PlayerSignInSummary summary) {
            verifiedSaveCalls++;
            capturedSummary = summary;
            stored = summary.serializeNBT();
        }
    }
}
