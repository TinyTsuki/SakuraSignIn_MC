package xin.vanilla.sakura.internal.forge.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 管理操作先保存恢复日志，再更新摘要与必要的月文件。
 */
public final class SignInDeletionJournal {
    private final Path worldData;

    public SignInDeletionJournal(Path worldData) {
        this.worldData = worldData.resolve("sakura_sign_in");
    }

    public boolean hasPending(UUID uuid) throws IOException {
        return exists(pendingFile(uuid));
    }

    public void delete(UUID uuid, LocalDate day, PlayerSignInSummary before,
                       PlayerSignInSummary after, PlayerSummaryStore summaries) throws IOException {
        recover(uuid, summaries);
        String month = YearMonth.from(day).toString();
        Path history = historyFile(uuid, month);
        boolean historyExisted = exists(history);
        CompoundTag original = historyExisted ? AtomicNbtFiles.read(history) : emptyHistory(uuid, month);
        CompoundTag replacement = original.copy();
        ListTag kept = new ListTag();
        ListTag records = original.getList("records", 10);
        for (int i = 0; i < records.size(); i++) {
            CompoundTag record = records.getCompound(i);
            String date = record.getString("compensateTime");
            if (!date.equals(day.toString()) && !date.startsWith(day + " ")) {
                kept.add(record.copy());
            }
        }
        replacement.put("records", kept);
        CompoundTag journal = new CompoundTag();
        journal.putString("playerUuid", uuid.toString());
        journal.putString("month", month);
        journal.putString("day", day.toString());
        journal.putBoolean("historyExisted", historyExisted);
        journal.put("beforeSummary", before.serializeNBT());
        journal.put("afterSummary", after.serializeNBT());
        journal.put("beforeHistory", original);
        journal.put("afterHistory", replacement);
        commit(uuid, journal, summaries);
    }

    public void resetPersonalDates(UUID uuid, PlayerSignInSummary before,
                                   PlayerSignInSummary after, PlayerSummaryStore summaries) throws IOException {
        recover(uuid, summaries);
        CompoundTag journal = new CompoundTag();
        journal.putString("playerUuid", uuid.toString());
        journal.putString("operation", "personalDateReset");
        journal.put("beforeSummary", before.serializeNBT());
        journal.put("afterSummary", after.serializeNBT());
        commit(uuid, journal, summaries);
    }

    private void commit(UUID uuid, CompoundTag journal, PlayerSummaryStore summaries) throws IOException {
        Path pending = pendingFile(uuid);
        writeVerified(directory(uuid).resolve("backup-" + UUID.randomUUID() + ".nbt"), journal);
        writeVerified(pending, journal);
        try {
            recover(uuid, summaries);
        } catch (IOException failure) {
            try {
                journal.putBoolean("rollback", true);
                writeVerified(pending, journal);
                recover(uuid, summaries);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public void recover(UUID uuid, PlayerSummaryStore summaries) throws IOException {
        Path pending = pendingFile(uuid);
        if (!exists(pending)) return;
        CompoundTag journal = AtomicNbtFiles.read(pending);
        String month = journal.getString("month");
        String operation = journal.getString("operation");
        boolean summaryOnly = "personalDateReset".equals(operation);
        try {
            if (!operation.isEmpty() && !summaryOnly) {
                throw new IllegalArgumentException("Unknown operation");
            }
            if (!summaryOnly) YearMonth.parse(month);
            if (!uuid.toString().equals(journal.getString("playerUuid"))) {
                throw new IllegalArgumentException("Player mismatch");
            }
            for (String side : new String[]{"before", "after"}) {
                if (!PlayerSignInSummary.isCurrentSchema(journal.getCompound(side + "Summary"))) {
                    throw new IllegalArgumentException("Missing summary snapshot");
                }
                if (!summaryOnly) {
                    CompoundTag snapshot = journal.getCompound(side + "History");
                    if (!uuid.toString().equals(snapshot.getString("playerUuid"))
                            || !month.equals(snapshot.getString("month")) || !snapshot.contains("records", 9)) {
                        throw new IllegalArgumentException("Invalid history snapshot");
                    }
                }
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid sign-in deletion journal: " + pending, invalid);
        }
        boolean rollback = journal.getBoolean("rollback");
        String side = rollback ? "before" : "after";
        if (!summaryOnly) {
            Path history = historyFile(uuid, month);
            if (rollback && !journal.getBoolean("historyExisted")) {
                Files.deleteIfExists(history);
            } else {
                writeVerified(history, journal.getCompound(side + "History"));
            }
        }
        try {
            summaries.saveAndVerify(uuid,
                    PlayerSignInSummary.deserializeNBT(journal.getCompound(side + "Summary")));
        } catch (RuntimeException failure) {
            throw new IOException("Unable to persist sign-in deletion summary: " + uuid, failure);
        }
        Files.delete(pending);
    }

    private Path directory(UUID uuid) {
        return worldData.resolve("deletions").resolve(uuid.toString());
    }

    private static boolean exists(Path file) throws IOException {
        try {
            Files.readAttributes(file, BasicFileAttributes.class);
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        }
    }

    private Path pendingFile(UUID uuid) {
        return directory(uuid).resolve("pending.nbt");
    }

    private Path historyFile(UUID uuid, String month) {
        return worldData.resolve("history").resolve(uuid.toString()).resolve(month + ".nbt");
    }

    private static void writeVerified(Path file, CompoundTag data) throws IOException {
        AtomicNbtFiles.write(file, data);
        if (!data.equals(AtomicNbtFiles.read(file))) {
            throw new IOException("Sign-in deletion verification failed: " + file);
        }
    }

    private static CompoundTag emptyHistory(UUID uuid, String month) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", 1);
        root.putString("playerUuid", uuid.toString());
        root.putString("month", month);
        root.put("records", new ListTag());
        return root;
    }
}
