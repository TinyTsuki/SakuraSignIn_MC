package xin.vanilla.sakura.internal.fabric.migration;

import net.minecraft.nbt.CompoundTag;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.api.BaniraPlayerData;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;
import xin.vanilla.sakura.internal.fabric.storage.AtomicNbtFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * 摘要写入 Banira 独立玩家数据，并在删除旧 Capability 前读取磁盘复核。
 */
public final class BaniraPlayerSummaryRepository implements PlayerSummaryStore {

    @Override
    public Optional<PlayerSignInSummary> load(UUID playerUuid) {
        CompoundTag tag = BaniraPlayerData.getOrCreate(
                playerUuid, SakuraSignIn.MODID, CompoundTag.class
        );
        return PlayerSignInSummary.isCurrentSchema(tag)
                ? Optional.of(PlayerSignInSummary.deserializeNBT(tag))
                : Optional.empty();
    }

    @Override
    public void save(UUID playerUuid, PlayerSignInSummary summary) {
        BaniraPlayerData.put(playerUuid, SakuraSignIn.MODID, summary.serializeNBT());
    }

    @Override
    public void saveAndVerify(UUID playerUuid, PlayerSignInSummary summary) throws IOException {
        CompoundTag serialized = summary.serializeNBT();
        BaniraPlayerData.put(playerUuid, SakuraSignIn.MODID, serialized);
        BaniraPlayerData.flush(playerUuid);

        Path playerFile = BaniraDataPaths.playerDataPath().resolve(playerUuid + ".nbt");
        if (!Files.isRegularFile(playerFile)) {
            throw new IOException("Banira player data was not written: " + playerFile);
        }
        CompoundTag root = AtomicNbtFiles.read(playerFile);
        if (!root.contains(SakuraSignIn.MODID, 10)
                || !serialized.equals(root.getCompound(SakuraSignIn.MODID))) {
            throw new IOException("Banira player summary verification failed: " + playerUuid);
        }
    }
}
