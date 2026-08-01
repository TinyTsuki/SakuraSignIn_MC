package xin.vanilla.sakura.config.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import xin.vanilla.sakura.config.reward.RewardConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 负责奖励配置的原子读写、旧格式备份与一次性迁移。
 */
public final class RewardConfigRepository {
    public static final String REWARD_FILE_NAME = "reward_option_data.json";

    private final Path configDirectory;
    private final Path rewardFile;
    private final Path backupDirectory;
    private final RewardConfigCodec codec;
    private final LegacyRewardConfigReader legacyReader;

    public RewardConfigRepository(Path configDirectory) {
        this(configDirectory, new RewardConfigCodec(), new LegacyRewardConfigReader());
    }

    RewardConfigRepository(Path configDirectory, RewardConfigCodec codec,
                           LegacyRewardConfigReader legacyReader) {
        this.configDirectory = configDirectory;
        this.rewardFile = configDirectory.resolve(REWARD_FILE_NAME);
        this.backupDirectory = configDirectory.resolve("backups").resolve("reward-config");
        this.codec = codec;
        this.legacyReader = legacyReader;
    }

    public RewardConfig loadOrCreate(RewardConfig defaults) throws IOException {
        Files.createDirectories(configDirectory);
        if (!Files.isRegularFile(rewardFile)) {
            save(defaults);
            return codec.decode(readUtf8(rewardFile));
        }

        byte[] source = Files.readAllBytes(rewardFile);
        String json = new String(source, StandardCharsets.UTF_8);
        if (isVersioned(json)) {
            return codec.decode(json);
        }

        RewardConfigDocument legacyDocument = legacyReader.read(json);
        RewardConfig migrated = codec.toRuntimeConfig(legacyDocument);
        codec.assertEquivalent(legacyDocument, migrated);
        Path backup = writeBackup(source, "migration-v1");
        try {
            save(migrated);
            RewardConfig verified = codec.decode(readUtf8(rewardFile));
            if (!codec.encode(migrated).equals(codec.encode(verified))) {
                throw new IOException("Migrated reward configuration failed verification");
            }
            return verified;
        } catch (IOException e) {
            atomicWrite(rewardFile, Files.readAllBytes(backup));
            throw e;
        }
    }

    public void save(RewardConfig config) throws IOException {
        atomicWrite(rewardFile, codec.encode(config).getBytes(StandardCharsets.UTF_8));
    }

    public Path backupCurrent(String reason) throws IOException {
        if (!Files.isRegularFile(rewardFile)) {
            throw new IOException("Reward configuration does not exist: " + rewardFile);
        }
        return writeBackup(Files.readAllBytes(rewardFile), reason);
    }

    private boolean isVersioned(String json) throws IOException {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            return root.has("schemaVersion");
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Invalid reward configuration JSON", e);
        }
    }

    private Path writeBackup(byte[] source, String reason) throws IOException {
        Files.createDirectories(backupDirectory);
        String safeReason = reason == null ? "manual" : reason.replaceAll("[^a-zA-Z0-9_-]", "_");
        long timestamp = System.currentTimeMillis();
        Path target;
        int suffix = 0;
        do {
            String name = safeReason + "-" + timestamp
                    + (suffix == 0 ? "" : "-" + suffix) + ".json";
            target = backupDirectory.resolve(name);
            suffix++;
        } while (Files.exists(target));
        writeNewFile(target, source);
        return target;
    }

    private static String readUtf8(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void writeNewFile(Path file, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void atomicWrite(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
