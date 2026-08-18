package xin.vanilla.sakura.internal.fabric.migration;

import net.minecraft.nbt.CompoundTag;
import xin.vanilla.sakura.data.migration.LegacyCapabilityStore;
import xin.vanilla.sakura.internal.fabric.storage.AtomicNbtFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * 读取并清理原版玩家文件中的旧 Forge Capability。
 */
public final class LegacyForgeCapabilityStore implements LegacyCapabilityStore {
    public static final String CAPABILITY_KEY = "sakura_sign_in:player_sign_in_data";

    private final Path vanillaPlayerDataPath;
    private final Path worldDataPath;

    public LegacyForgeCapabilityStore(Path vanillaPlayerDataPath, Path worldDataPath) {
        this.vanillaPlayerDataPath = vanillaPlayerDataPath;
        this.worldDataPath = worldDataPath;
    }

    @Override
    public Optional<CompoundTag> read(UUID playerUuid) throws IOException {
        Path playerFile = playerFile(playerUuid);
        recoverInterruptedRemoval(playerFile);
        if (!Files.isRegularFile(playerFile)) {
            return Optional.empty();
        }
        CompoundTag root = AtomicNbtFiles.read(playerFile);
        CompoundTag forgeCaps = root.getCompound("ForgeCaps");
        return forgeCaps.contains(CAPABILITY_KEY, 10)
                ? Optional.of(forgeCaps.getCompound(CAPABILITY_KEY).copy())
                : Optional.empty();
    }

    @Override
    public String backupAndVerify(UUID playerUuid, CompoundTag capability) throws IOException {
        Path directory = worldDataPath.resolve("backups")
                .resolve("sakura_sign_in")
                .resolve("legacy-capability");
        Path backup = nextBackupPath(directory, playerUuid, capability);
        if (!Files.exists(backup)) {
            AtomicNbtFiles.write(backup, capability);
        }
        if (!capability.equals(AtomicNbtFiles.read(backup))) {
            throw new IOException("Legacy Capability backup verification failed: " + backup);
        }
        return normalize(worldDataPath.relativize(backup));
    }

    @Override
    public boolean backupMatches(String relativePath, CompoundTag capability) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }
        Path backup = worldDataPath.resolve(relativePath).normalize();
        if (!backup.startsWith(worldDataPath.normalize()) || !Files.isRegularFile(backup)) {
            return false;
        }
        return capability.equals(AtomicNbtFiles.read(backup));
    }

    @Override
    public void removeAndVerify(UUID playerUuid, CompoundTag expectedCapability) throws IOException {
        Path playerFile = playerFile(playerUuid);
        CompoundTag root = AtomicNbtFiles.read(playerFile);
        CompoundTag forgeCaps = root.getCompound("ForgeCaps");
        if (!forgeCaps.contains(CAPABILITY_KEY, 10)) {
            return;
        }
        if (!expectedCapability.equals(forgeCaps.getCompound(CAPABILITY_KEY))) {
            throw new IOException("Legacy Capability changed during migration: " + playerUuid);
        }

        CompoundTag rewritten = root.copy();
        rewritten.getCompound("ForgeCaps").remove(CAPABILITY_KEY);
        replacePlayerFileWithRollback(playerFile, root, rewritten, playerUuid);
    }

    private Path nextBackupPath(Path directory, UUID playerUuid, CompoundTag capability) throws IOException {
        Files.createDirectories(directory);
        for (int index = 0; ; index++) {
            String suffix = index == 0 ? "" : "-" + index;
            Path candidate = directory.resolve(playerUuid + suffix + ".nbt");
            if (!Files.exists(candidate) || capability.equals(AtomicNbtFiles.read(candidate))) {
                return candidate;
            }
        }
    }

    private Path playerFile(UUID playerUuid) {
        return vanillaPlayerDataPath.resolve(playerUuid + ".dat");
    }

    private void replacePlayerFileWithRollback(
            Path playerFile,
            CompoundTag original,
            CompoundTag rewritten,
            UUID playerUuid
    ) throws IOException {
        Path replacement = playerFile.resolveSibling(playerFile.getFileName() + ".sakura-migration.tmp");
        Path rollback = rollbackFile(playerFile);
        AtomicNbtFiles.write(replacement, rewritten);
        if (!rewritten.equals(AtomicNbtFiles.read(replacement))) {
            throw new IOException("Rewritten player data verification failed: " + playerUuid);
        }
        AtomicNbtFiles.write(rollback, original);
        if (!original.equals(AtomicNbtFiles.read(rollback))) {
            throw new IOException("Player data rollback verification failed: " + playerUuid);
        }

        try {
            Files.move(replacement, playerFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            CompoundTag verified = AtomicNbtFiles.read(playerFile);
            if (verified.getCompound("ForgeCaps").contains(CAPABILITY_KEY, 10)) {
                throw new IOException("Legacy Capability removal verification failed: " + playerUuid);
            }
            Files.deleteIfExists(rollback);
        } catch (IOException failure) {
            restoreRollback(playerFile, rollback);
            throw failure;
        } finally {
            Files.deleteIfExists(replacement);
        }
    }

    private void recoverInterruptedRemoval(Path playerFile) throws IOException {
        Path rollback = rollbackFile(playerFile);
        if (!Files.isRegularFile(rollback)) {
            return;
        }
        if (!Files.isRegularFile(playerFile)) {
            restoreRollback(playerFile, rollback);
            return;
        }
        try {
            AtomicNbtFiles.read(playerFile);
            // 目标存在且可读取，说明替换前或替换后至少有一份完整玩家数据。
            Files.deleteIfExists(rollback);
        } catch (IOException unreadableTarget) {
            restoreRollback(playerFile, rollback);
        }
    }

    private static void restoreRollback(Path playerFile, Path rollback) throws IOException {
        if (Files.isRegularFile(rollback)) {
            Files.move(rollback, playerFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static Path rollbackFile(Path playerFile) {
        return playerFile.resolveSibling(playerFile.getFileName() + ".sakura-migration.rollback");
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
