package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import xin.vanilla.sakura.data.migration.LegacyPlayerData;
import xin.vanilla.sakura.data.migration.LegacyPlayerDataParser;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.data.player.MonthSignInIndex;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.sakura.internal.forge.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;

/**
 * 运行时数据仍使用现有领域对象，持久化已拆分为摘要和月度详情。
 */
public final class PlayerSignInDataRepository {
    private final PlayerSummaryStore summaryRepository;
    private final MonthlySignInHistoryRepository historyRepository;
    private final LegacyPlayerDataParser legacyParser;

    public PlayerSignInDataRepository(
            PlayerSummaryStore summaryRepository,
            MonthlySignInHistoryRepository historyRepository
    ) {
        this.summaryRepository = summaryRepository;
        this.historyRepository = historyRepository;
        this.legacyParser = new LegacyPlayerDataParser();
    }

    public PlayerSignInData load(UUID playerUuid) throws IOException {
        historyRepository.deletionJournal().recover(playerUuid, summaryRepository);
        Optional<PlayerSignInSummary> optional = summaryRepository.load(playerUuid);
        if (!optional.isPresent()) {
            return new PlayerSignInData();
        }
        PlayerSignInSummary summary = optional.get();
        CompoundNBT legacyShape = new CompoundNBT();
        legacyShape.putInt("totalSignInDays", summary.getTotalSignInDays());
        legacyShape.putInt("continuousSignInDays", summary.getContinuousSignInDays());
        legacyShape.putString("lastSignInTime", summary.getLastSignInTime());
        legacyShape.putInt("signInCard", summary.getSignInCard());
        legacyShape.putBoolean("autoRewarded", summary.isAutoRewarded());
        legacyShape.putString("language", summary.getLanguage());
        legacyShape.put("cdkRecords", copyList(summary.getCdkRecords()));
        ListNBT indexes = new ListNBT();
        summary.getMonthIndexes().values().forEach(index -> indexes.add(index.serializeNBT()));
        legacyShape.put("monthIndexes", indexes);
        ListNBT slots = new ListNBT();
        summary.getPersonalDateSlots().forEach(slot -> slots.add(slot.serializeNBT()));
        legacyShape.put("personalDateSlots", slots);
        legacyShape.putString("onlineTimeBaselineDate", summary.getOnlineTimeBaselineDate());
        legacyShape.putInt("onlineTimeBaselineTicks", summary.getOnlineTimeBaselineTicks());
        ListNBT lotteryStates = new ListNBT();
        summary.getLotteryDrawStates().forEach(state -> lotteryStates.add(state.serializeNBT()));
        legacyShape.put("lotteryDrawStates", lotteryStates);

        ListNBT records = new ListNBT();
        historyRepository.loadAll(playerUuid).forEach(record -> records.add(record.writeToNBT()));
        legacyShape.put("signInRecords", records);

        PlayerSignInData result = new PlayerSignInData();
        result.deserializeNBT(legacyShape);
        return result;
    }

    public void save(UUID playerUuid, IPlayerSignInData data) throws IOException {
        historyRepository.deletionJournal().recover(playerUuid, summaryRepository);
        CompoundNBT serialized = data.serializeNBT();
        // 永久摘要采用已维护的统计值，不能因详情被清理而重新计算。
        serialized.putInt("continuousSignInDays", data.getContinuousSignInDays());
        LegacyPlayerData parsed = legacyParser.parse(serialized);
        Optional<PlayerSignInSummary> previous = summaryRepository.load(playerUuid);
        if (previous.isPresent()) {
            previous.get().getMonthIndexes().forEach((month, previousIndex) ->
                    parsed.getSummary().getMonthIndexes()
                            .computeIfAbsent(month, xin.vanilla.sakura.data.player.MonthSignInIndex::new)
                            .merge(previousIndex));
            parsed.getSummary().setLegacyCapabilityMigrated(
                    previous.get().isLegacyCapabilityMigrated()
            );
            parsed.getSummary().setLegacyCapabilityBackup(
                    previous.get().getLegacyCapabilityBackup()
            );
            mergePersonalDateSlots(parsed.getSummary(), previous.get());
        }
        data.setMonthIndexes(parsed.getSummary().getMonthIndexes());
        historyRepository.saveMonths(playerUuid, parsed, changedHistoryMonths(previous, parsed));
        summaryRepository.save(playerUuid, parsed.getSummary());
    }

    public boolean deleteDay(UUID uuid, IPlayerSignInData data, LocalDate day,
                             LocalDate currentDay) throws IOException {
        historyRepository.deletionJournal().recover(uuid, summaryRepository);
        PlayerSignInData candidate = new PlayerSignInData();
        candidate.copyFrom(data);
        String month = YearMonth.from(day).toString();
        MonthSignInIndex index = candidate.getMonthIndexes().get(month);
        boolean signed = index != null && index.isSigned(day.getDayOfMonth());
        boolean rewarded = index != null && index.isRewarded(day.getDayOfMonth());
        boolean latestRemoved = candidate.getSignInRecords().stream().anyMatch(record ->
                day.equals(localDay(record.getCompensateTime()))
                        && record.getSignInTime().equals(candidate.getLastSignInTime()));
        boolean removed = candidate.getSignInRecords().removeIf(record ->
                day.equals(localDay(record.getCompensateTime())));
        if (!signed && !rewarded && !removed) return false;

        // 保留日常保存的合并保护，只有显式删除允许清除永久位图。
        save(uuid, data);
        PlayerSignInSummary before = summaryRepository.load(uuid)
                .orElseThrow(() -> new IOException("Missing sign-in summary: " + uuid));
        candidate.setMonthIndexes(data.getMonthIndexes());
        index = candidate.getMonthIndexes().get(month);
        if (index != null) {
            int mask = ~(1 << (day.getDayOfMonth() - 1));
            index.setSignedDays(index.getSignedDays() & mask);
            index.setRewardedDays(index.getRewardedDays() & mask);
        }
        if (signed || removed) candidate.setTotalSignInDays(Math.max(0, data.getTotalSignInDays() - 1));
        LocalDate end = candidate.isSignedOn(atStart(currentDay)) ? currentDay : currentDay.minusDays(1);
        candidate.setContinuousSignInDays(candidate.calculateContinuousDays(atStart(end)));
        // 详情缺失时无法确认最后一次动作的签到日，保留原冷却时间。
        if (latestRemoved) {
            Date last = candidate.getSignInRecords().stream().map(SignInRecord::getSignInTime)
                    .max(Comparator.naturalOrder()).orElseGet(() -> lastIndexedDay(candidate));
            candidate.setLastSignInTime(last);
        }
        PlayerSignInSummary after = legacyParser.parse(candidate.serializeNBT()).getSummary();
        after.setLegacyCapabilityMigrated(before.isLegacyCapabilityMigrated());
        after.setLegacyCapabilityBackup(before.getLegacyCapabilityBackup());
        mergePersonalDateSlots(after, before);
        historyRepository.deletionJournal().delete(uuid, day, before, after, summaryRepository);
        candidate.setPersonalDateSlots(after.getPersonalDateSlots());
        data.copyFrom(candidate);
        return true;
    }

    private static Date lastIndexedDay(IPlayerSignInData data) {
        LocalDate latest = null;
        for (MonthSignInIndex index : data.getMonthIndexes().values()) {
            YearMonth month = YearMonth.parse(index.getMonth());
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                if (index.isSigned(day) && (latest == null || date.isAfter(latest))) latest = date;
            }
        }
        return latest == null ? DateUtils.getDate(0, 1, 1) : atStart(latest);
    }

    public int resetPersonalDateClaims(UUID uuid, IPlayerSignInData data, String preset) throws IOException {
        return resetPersonalDateClaims(uuid, data, preset, "all".equals(preset));
    }

    public int resetPersonalDateClaims(UUID uuid, IPlayerSignInData data, String preset, boolean all) throws IOException {
        if (preset == null || preset.isEmpty()) throw new IllegalArgumentException("Expected preset ID or all");
        historyRepository.deletionJournal().recover(uuid, summaryRepository);
        Optional<PlayerSignInSummary> stored = summaryRepository.load(uuid);
        boolean hasClaims = data.getPersonalDateSlots().stream().anyMatch(slot -> matchesClaim(slot, preset, all))
                || stored.map(summary -> summary.getPersonalDateSlots().stream()
                .anyMatch(slot -> matchesClaim(slot, preset, all))).orElse(false);
        if (!hasClaims) return 0;

        save(uuid, data);
        PlayerSignInSummary before = summaryRepository.load(uuid)
                .orElseThrow(() -> new IOException("Missing player summary: " + uuid));
        PlayerSignInSummary after = PlayerSignInSummary.deserializeNBT(before.serializeNBT());
        int cleared = 0;
        for (PlayerPersonalDateSlot slot : after.getPersonalDateSlots()) {
            if (matchesClaim(slot, preset, all)) {
                slot.setLastClaimedOccurrenceKey("");
                cleared++;
            }
        }
        if (cleared == 0) return 0;
        historyRepository.deletionJournal().resetPersonalDates(uuid, before, after, summaryRepository);
        data.setPersonalDateSlots(after.getPersonalDateSlots());
        return cleared;
    }

    private static boolean matchesClaim(PlayerPersonalDateSlot slot, String preset, boolean all) {
        return (all || preset.equals(slot.getPresetId()))
                && slot.getLastClaimedOccurrenceKey() != null && !slot.getLastClaimedOccurrenceKey().isEmpty();
    }

    private static Date atStart(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate localDay(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Set<String> changedHistoryMonths(
            Optional<PlayerSignInSummary> previous,
            LegacyPlayerData candidate
    ) {
        Set<String> changed = new LinkedHashSet<>();
        candidate.getRecordsByMonth().keySet().forEach(month -> {
            if (!previous.isPresent()
                    || !Objects.equals(
                    previous.get().getMonthIndexes().get(month),
                    candidate.getSummary().getMonthIndexes().get(month))) {
                changed.add(month);
            }
        });
        return changed;
    }

    private static ListNBT copyList(ListNBT source) {
        ListNBT copy = new ListNBT();
        for (INBT element : source) {
            copy.add(element.copy());
        }
        return copy;
    }

    private static void mergePersonalDateSlots(PlayerSignInSummary candidate,
                                               PlayerSignInSummary previous) {
        Map<String, PlayerPersonalDateSlot> merged = new LinkedHashMap<>();
        previous.getPersonalDateSlots().forEach(slot -> merged.put(slotKey(slot), copySlot(slot)));
        candidate.getPersonalDateSlots().forEach(slot -> {
            PlayerPersonalDateSlot old = merged.get(slotKey(slot));
            if (old != null && (slot.getLastClaimedOccurrenceKey() == null
                    || slot.getLastClaimedOccurrenceKey().isEmpty())) {
                slot.setLastClaimedOccurrenceKey(old.getLastClaimedOccurrenceKey());
            }
            merged.put(slotKey(slot), copySlot(slot));
        });
        candidate.setPersonalDateSlots(new java.util.ArrayList<>(merged.values()));
    }

    private static String slotKey(PlayerPersonalDateSlot slot) {
        return slot.getPresetId() + "#" + slot.getSlotIndex();
    }

    private static PlayerPersonalDateSlot copySlot(PlayerPersonalDateSlot slot) {
        return PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT());
    }
}
