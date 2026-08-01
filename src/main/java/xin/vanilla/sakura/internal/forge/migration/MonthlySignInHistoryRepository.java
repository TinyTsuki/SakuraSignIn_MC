package xin.vanilla.sakura.internal.forge.migration;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.data.migration.SignInHistoryStore;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.data.migration.LegacyPlayerData;
import xin.vanilla.sakura.data.player.HistoryRetentionPolicy;
import xin.vanilla.sakura.internal.forge.storage.AtomicNbtFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 每个玩家每月一个文件，详情清理不会影响永久摘要。
 */
public final class MonthlySignInHistoryRepository implements SignInHistoryStore {
    private static final int SCHEMA_VERSION = 1;
    private static final Logger LOGGER = LogManager.getLogger();

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

    public List<SignInRecord> loadAll(UUID playerUuid) throws IOException {
        Path directory = historyDirectory(playerUuid);
        if (!Files.isDirectory(directory)) {
            return new ArrayList<>();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }

        List<SignInRecord> result = new ArrayList<>();
        for (Path file : files) {
            ListNBT records = AtomicNbtFiles.read(file).getList("records", 10);
            for (int i = 0; i < records.size(); i++) {
                try {
                    result.add(SignInRecord.readFromNBT(records.getCompound(i)));
                } catch (RuntimeException malformedRecord) {
                    LOGGER.warn("Skipping unreadable sign-in history record in {}: {}",
                            file, malformedRecord.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * 清理单个玩家的过期详情，无法识别月份的迁移保底文件始终保留。
     */
    public RetentionResult applyRetention(
            UUID playerUuid,
            YearMonth currentMonth,
            int retentionMonths,
            HistoryRetentionPolicy policy
    ) throws IOException {
        RetentionResult result = new RetentionResult();
        if (retentionMonths <= 0) {
            return result;
        }
        Path directory = historyDirectory(playerUuid);
        if (!Files.isDirectory(directory)) {
            return result;
        }
        YearMonth oldestRetained = currentMonth.minusMonths(retentionMonths - 1L);
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .collect(Collectors.toList());
        }
        for (Path file : files) {
            YearMonth month = parseMonth(file);
            if (month == null || !month.isBefore(oldestRetained)) {
                continue;
            }
            if (policy == HistoryRetentionPolicy.DELETE_MONTH_FILE) {
                Files.deleteIfExists(file);
                result.deletedMonthFiles++;
            } else if (stripRewardDetails(file)) {
                result.strippedMonthFiles++;
            }
        }
        return result;
    }

    private static YearMonth parseMonth(Path file) {
        String name = file.getFileName().toString();
        try {
            return YearMonth.parse(name.substring(0, name.length() - 4));
        } catch (DateTimeParseException | IndexOutOfBoundsException ignored) {
            return null;
        }
    }

    private static boolean stripRewardDetails(Path file) throws IOException {
        CompoundNBT root = AtomicNbtFiles.read(file);
        ListNBT records = root.getList("records", 10);
        boolean changed = false;
        for (int i = 0; i < records.size(); i++) {
            CompoundNBT record = records.getCompound(i);
            // 未领取奖励仍依赖签到时保存的快照，不能参与压缩。
            if (record.getBoolean("rewarded") && !"[]".equals(record.getString("rewardList"))) {
                record.putString("rewardList", "[]");
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }
        root.put("records", records);
        AtomicNbtFiles.write(file, root);
        if (!root.equals(AtomicNbtFiles.read(file))) {
            throw new IOException("Monthly sign-in history verification failed: " + file);
        }
        return true;
    }

    private Path historyFile(UUID playerUuid, String month) {
        return historyDirectory(playerUuid).resolve(month + ".nbt");
    }

    private Path historyDirectory(UUID playerUuid) {
        return worldDataPath.resolve("sakura_sign_in")
                .resolve("history")
                .resolve(playerUuid.toString());
    }

    @Getter
    public static final class RetentionResult {
        private int strippedMonthFiles;
        private int deletedMonthFiles;
    }
}
