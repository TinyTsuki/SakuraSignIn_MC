package xin.vanilla.sakura.internal.forge.migration;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import xin.vanilla.sakura.data.migration.SignInHistoryStore;
import xin.vanilla.sakura.domain.player.LegacyPlayerData;
import xin.vanilla.sakura.internal.forge.storage.AtomicNbtFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家每月一个文件，详情清理不会影响永久摘要。
 */
public final class MonthlySignInHistoryRepository implements SignInHistoryStore {
    private static final int SCHEMA_VERSION = 1;

    private final Path worldDataPath;

    public MonthlySignInHistoryRepository(Path worldDataPath) {
        this.worldDataPath = worldDataPath;
    }

    @Override
    public void saveAndVerify(UUID playerUuid, LegacyPlayerData playerData) throws IOException {
        for (Map.Entry<String, List<CompoundNBT>> entry : playerData.getRecordsByMonth().entrySet()) {
            CompoundNBT root = new CompoundNBT();
            root.putInt("schemaVersion", SCHEMA_VERSION);
            root.putString("playerUuid", playerUuid.toString());
            root.putString("month", entry.getKey());
            ListNBT records = new ListNBT();
            entry.getValue().forEach(record -> records.add(record.copy()));
            root.put("records", records);

            Path target = historyFile(playerUuid, entry.getKey());
            AtomicNbtFiles.write(target, root);
            if (!root.equals(AtomicNbtFiles.read(target))) {
                throw new IOException("Monthly sign-in history verification failed: " + target);
            }
        }
    }

    private Path historyFile(UUID playerUuid, String month) {
        return worldDataPath.resolve("sakura_sign_in")
                .resolve("history")
                .resolve(playerUuid.toString())
                .resolve(month + ".nbt");
    }
}
