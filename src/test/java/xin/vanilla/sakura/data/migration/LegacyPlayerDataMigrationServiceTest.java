package xin.vanilla.sakura.data.migration;

import net.minecraft.nbt.CompoundNBT;
import org.junit.Test;
import xin.vanilla.sakura.data.migration.LegacyPlayerData;
import xin.vanilla.sakura.data.migration.LegacyPlayerDataParser;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 迁移必须先持久化详情、备份和摘要，最后才允许删除旧节点。
 */
public class LegacyPlayerDataMigrationServiceTest {

    @Test
    public void removesLegacyNodeOnlyAfterAllDurableWritesSucceed() throws Exception {
        UUID uuid = UUID.randomUUID();
        CompoundNBT legacy = legacyData();
        RecordingStores stores = new RecordingStores(legacy);
        LegacyPlayerDataMigrationService service = stores.service();

        LegacyMigrationResult result = service.migrate(uuid);

        assertEquals(LegacyMigrationResult.MIGRATED, result);
        assertEquals(Arrays.asList("history", "backup", "summary", "remove"), stores.calls);
        assertTrue(stores.removed);
        assertTrue(stores.summary.isLegacyCapabilityMigrated());
        assertEquals("backups/sakura_sign_in/legacy-capability/player.nbt",
                stores.summary.getLegacyCapabilityBackup());
    }

    @Test
    public void failedSummaryWriteLeavesLegacyCapabilityUntouched() {
        UUID uuid = UUID.randomUUID();
        RecordingStores stores = new RecordingStores(legacyData());
        stores.failSummary = true;

        try {
            stores.service().migrate(uuid);
        } catch (IOException expected) {
            assertEquals(Arrays.asList("history", "backup", "summary"), stores.calls);
            assertFalse(stores.removed);
            return;
        }
        throw new AssertionError("Migration should fail");
    }

    @Test
    public void retryOnlyCleansUpMatchingAlreadyMigratedCapability() throws Exception {
        UUID uuid = UUID.randomUUID();
        CompoundNBT legacy = legacyData();
        RecordingStores stores = new RecordingStores(legacy);
        PlayerSignInSummary summary = new LegacyPlayerDataParser().parse(legacy).getSummary();
        summary.setLegacyCapabilityMigrated(true);
        summary.setLegacyCapabilityBackup("backups/sakura_sign_in/legacy-capability/player.nbt");
        stores.summary = summary;
        stores.backupMatches = true;

        LegacyMigrationResult result = stores.service().migrate(uuid);

        assertEquals(LegacyMigrationResult.CLEANED_UP, result);
        assertEquals(Arrays.asList("remove"), stores.calls);
        assertTrue(stores.removed);
    }

    private static CompoundNBT legacyData() {
        CompoundNBT legacy = new CompoundNBT();
        legacy.putInt("totalSignInDays", 1);
        legacy.putString("lastSignInTime", "2024-02-02 09:10:11");
        return legacy;
    }

    private static final class RecordingStores implements LegacyCapabilityStore, SignInHistoryStore, PlayerSummaryStore {
        private final CompoundNBT legacy;
        private final List<String> calls = new ArrayList<>();
        private PlayerSignInSummary summary;
        private boolean backupMatches;
        private boolean failSummary;
        private boolean removed;

        private RecordingStores(CompoundNBT legacy) {
            this.legacy = legacy;
        }

        private LegacyPlayerDataMigrationService service() {
            return new LegacyPlayerDataMigrationService(
                    new LegacyPlayerDataParser(), this, this, this
            );
        }

        @Override
        public Optional<CompoundNBT> read(UUID playerUuid) {
            return Optional.of(legacy);
        }

        @Override
        public String backupAndVerify(UUID playerUuid, CompoundNBT capability) {
            calls.add("backup");
            backupMatches = true;
            return "backups/sakura_sign_in/legacy-capability/player.nbt";
        }

        @Override
        public boolean backupMatches(String relativePath, CompoundNBT capability) {
            return backupMatches;
        }

        @Override
        public void removeAndVerify(UUID playerUuid, CompoundNBT expectedCapability) {
            calls.add("remove");
            removed = true;
        }

        @Override
        public void saveAndVerify(UUID playerUuid, LegacyPlayerData playerData) {
            calls.add("history");
        }

        @Override
        public Optional<PlayerSignInSummary> load(UUID playerUuid) {
            return Optional.ofNullable(summary);
        }

        @Override
        public void saveAndVerify(UUID playerUuid, PlayerSignInSummary playerSummary) throws IOException {
            calls.add("summary");
            if (failSummary) {
                throw new IOException("simulated");
            }
            summary = playerSummary;
        }
    }
}
