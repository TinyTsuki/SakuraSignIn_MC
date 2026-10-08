package xin.vanilla.sakura.internal.forge.storage;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import xin.vanilla.sakura.data.migration.PlayerSummaryStore;
import xin.vanilla.sakura.data.player.PlayerSignInSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 删除跨越摘要和月文件，恢复日志先于两个文件落盘。
 */
public final class SignInDeletionJournal {
    private final Path worldData;

    public SignInDeletionJournal(Path worldData) {
        this.worldData = worldData.resolve("sakura_sign_in");
    }

    public void delete(UUID uuid, LocalDate day, PlayerSignInSummary before,
                       PlayerSignInSummary after, PlayerSummaryStore summaries) throws IOException {
        recover(uuid, summaries);
        String month = YearMonth.from(day).toString();
        Path history = historyFile(uuid, month);
        CompoundNBT original = Files.exists(history) ? AtomicNbtFiles.read(history) : emptyHistory(uuid, month);
        CompoundNBT replacement = original.copy();
        ListNBT kept = new ListNBT();
        ListNBT records = original.getList("records", 10);
        for (int i = 0; i < records.size(); i++) {
            CompoundNBT record = records.getCompound(i);
            String date = record.getString("compensateTime");
            if (!date.equals(day.toString()) && !date.startsWith(day + " ")) {
                kept.add(record.copy());
            }
        }
        replacement.put("records", kept);
        CompoundNBT journal = new CompoundNBT();
        journal.putString("playerUuid", uuid.toString());
        journal.putString("month", month);
        journal.putString("day", day.toString());
        journal.putBoolean("historyExisted", Files.exists(history));
        journal.put("beforeSummary", before.serializeNBT());
        journal.put("afterSummary", after.serializeNBT());
        journal.put("beforeHistory", original);
        journal.put("afterHistory", replacement);
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
        if (!Files.exists(pending)) return;
        CompoundNBT journal = AtomicNbtFiles.read(pending);
        String month = journal.getString("month");
        try {
            YearMonth.parse(month);
            if (!uuid.toString().equals(journal.getString("playerUuid"))) {
                throw new IllegalArgumentException("Player mismatch");
            }
            for (String side : new String[]{"before", "after"}) {
                if (!PlayerSignInSummary.isCurrentSchema(journal.getCompound(side + "Summary"))) {
                    throw new IllegalArgumentException("Missing summary snapshot");
                }
                CompoundNBT snapshot = journal.getCompound(side + "History");
                if (!uuid.toString().equals(snapshot.getString("playerUuid"))
                        || !month.equals(snapshot.getString("month")) || !snapshot.contains("records", 9)) {
                    throw new IllegalArgumentException("Invalid history snapshot");
                }
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid sign-in deletion journal: " + pending, invalid);
        }
        boolean rollback = journal.getBoolean("rollback");
        String side = rollback ? "before" : "after";
        Path history = historyFile(uuid, month);
        if (rollback && !journal.getBoolean("historyExisted")) {
            Files.deleteIfExists(history);
        } else {
            writeVerified(history, journal.getCompound(side + "History"));
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

    private Path pendingFile(UUID uuid) {
        return directory(uuid).resolve("pending.nbt");
    }

    private Path historyFile(UUID uuid, String month) {
        return worldData.resolve("history").resolve(uuid.toString()).resolve(month + ".nbt");
    }

    private static void writeVerified(Path file, CompoundNBT data) throws IOException {
        AtomicNbtFiles.write(file, data);
        if (!data.equals(AtomicNbtFiles.read(file))) {
            throw new IOException("Sign-in deletion verification failed: " + file);
        }
    }

    private static CompoundNBT emptyHistory(UUID uuid, String month) {
        CompoundNBT root = new CompoundNBT();
        root.putInt("schemaVersion", 1);
        root.putString("playerUuid", uuid.toString());
        root.putString("month", month);
        root.put("records", new ListNBT());
        return root;
    }
}
