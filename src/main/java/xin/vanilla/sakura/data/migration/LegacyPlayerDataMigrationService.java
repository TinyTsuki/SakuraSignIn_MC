package xin.vanilla.sakura.data.migration;

import net.minecraft.nbt.CompoundTag;
import xin.vanilla.sakura.data.migration.LegacyPlayerData;
import xin.vanilla.sakura.data.migration.LegacyPlayerDataParser;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 跨文件迁移以删除旧节点为最后一步，失败时旧数据仍可重试。
 */
public final class LegacyPlayerDataMigrationService {
    private final LegacyPlayerDataParser parser;
    private final LegacyCapabilityStore legacyStore;
    private final SignInHistoryStore historyStore;
    private final PlayerSummaryStore summaryStore;

    public LegacyPlayerDataMigrationService(
            LegacyPlayerDataParser parser,
            LegacyCapabilityStore legacyStore,
            SignInHistoryStore historyStore,
            PlayerSummaryStore summaryStore
    ) {
        this.parser = parser;
        this.legacyStore = legacyStore;
        this.historyStore = historyStore;
        this.summaryStore = summaryStore;
    }

    public LegacyMigrationResult migrate(UUID playerUuid) throws IOException {
        Optional<CompoundTag> legacyOptional = legacyStore.read(playerUuid);
        if (!legacyOptional.isPresent()) {
            return LegacyMigrationResult.NO_LEGACY_DATA;
        }

        CompoundTag legacy = legacyOptional.get();
        Optional<PlayerSignInSummary> current = summaryStore.load(playerUuid);
        if (current.isPresent()
                && current.get().isLegacyCapabilityMigrated()
                && legacyStore.backupMatches(current.get().getLegacyCapabilityBackup(), legacy)) {
            legacyStore.removeAndVerify(playerUuid, legacy);
            return LegacyMigrationResult.CLEANED_UP;
        }

        LegacyPlayerData playerData = parser.parse(legacy);
        historyStore.saveAndVerify(playerUuid, playerData);
        String backup = legacyStore.backupAndVerify(playerUuid, legacy);

        PlayerSignInSummary summary = playerData.getSummary();
        summary.setLegacyCapabilityBackup(backup);
        summary.setLegacyCapabilityMigrated(true);
        summaryStore.saveAndVerify(playerUuid, summary);

        legacyStore.removeAndVerify(playerUuid, legacy);
        return LegacyMigrationResult.MIGRATED;
    }
}
