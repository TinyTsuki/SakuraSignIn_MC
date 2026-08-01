package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import xin.vanilla.sakura.data.migration.LegacyPlayerData;
import xin.vanilla.sakura.data.migration.LegacyPlayerDataParser;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.forge.migration.MonthlySignInHistoryRepository;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

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

        ListNBT records = new ListNBT();
        historyRepository.loadAll(playerUuid).forEach(record -> records.add(record.writeToNBT()));
        legacyShape.put("signInRecords", records);

        PlayerSignInData result = new PlayerSignInData();
        result.deserializeNBT(legacyShape);
        return result;
    }

    public void save(UUID playerUuid, IPlayerSignInData data) throws IOException {
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
        }
        data.setMonthIndexes(parsed.getSummary().getMonthIndexes());
        historyRepository.saveAndVerify(playerUuid, parsed);
        summaryRepository.saveAndVerify(playerUuid, parsed.getSummary());
    }

    private static ListNBT copyList(ListNBT source) {
        ListNBT copy = new ListNBT();
        for (INBT element : source) {
            copy.add(element.copy());
        }
        return copy;
    }
}
