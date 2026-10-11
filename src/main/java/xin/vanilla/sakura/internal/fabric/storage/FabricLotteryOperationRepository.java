package xin.vanilla.sakura.internal.fabric.storage;

import xin.vanilla.sakura.data.lottery.LotteryOperation;
import xin.vanilla.sakura.data.lottery.LotteryOperationCodec;
import xin.vanilla.sakura.data.lottery.LotteryProtocolLimits;
import xin.vanilla.sakura.platform.LotteryOperationRepository;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** One world-owned repository; every returned write is forced and read back. */
public final class FabricLotteryOperationRepository implements LotteryOperationRepository {
    @FunctionalInterface
    interface Replacement { void replace(Path source, Path target) throws IOException; }

    private final Path root;
    private final Replacement replacement;
    private final LotteryOperationCodec codec = new LotteryOperationCodec();

    public FabricLotteryOperationRepository(Path worldData) {
        this(worldData, FabricLotteryOperationRepository::replace);
    }
    FabricLotteryOperationRepository(Path worldData, Replacement replacement) {
        this.root = worldData.resolve("sakura_sign_in").resolve("lottery").toAbsolutePath().normalize();
        this.replacement = replacement;
    }

    @Override public synchronized LotteryOperation load(UUID playerId, String operationId) throws IOException {
        Path file = file(playerId, operationId);
        String json = read(file, LotteryOperationCodec.MAX_BYTES);
        if (json == null) return null;
        LotteryOperation operation = codec.decode(json);
        if (!playerId.equals(operation.getPlayerId()) || !operationId.equals(operation.getOperationId()))
            throw new IOException("Lottery operation identity mismatch: " + file);
        return operation;
    }

    @Override public synchronized List<LotteryOperation> list(UUID playerId) throws IOException {
        List<LotteryOperation> result = new ArrayList<>();
        Path directory = directory(playerId);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                if (result.size() >= 101) throw new IOException("Too many lottery operation records");
                String name = file.getFileName().toString();
                LotteryOperation op = load(playerId, name.substring(0, name.length() - 5));
                if (op == null) throw new IOException("Lottery record disappeared during read");
                result.add(op);
            }
        } catch (NoSuchFileException missing) { return result; }
        return result;
    }

    @Override public synchronized List<LotteryOperation> incomplete(UUID playerId) throws IOException {
        List<LotteryOperation> result = list(playerId);
        result.removeIf(LotteryOperation::isTerminal);
        return result;
    }

    @Override public synchronized void write(LotteryOperation operation) throws IOException {
        UUID player = operation.getPlayerId();
        List<LotteryOperation> records = list(player);
        LotteryOperation previous = records.stream().filter(value -> value.getOperationId()
                .equals(operation.getOperationId())).findFirst().orElse(null);
        UUID session = operation.getRequest().getRequest().getSession();
        long sequence = operation.getRequest().getRequest().getRequestId();
        long watermark = highWater(player, session);
        String encoded = codec.encode(operation);
        if (previous == null) {
            if (operation.getStage() != LotteryOperation.Stage.PREPARED || sequence <= watermark
                    || records.stream().anyMatch(value -> !value.isTerminal()))
                throw new IOException("Lottery request is replayed or another operation is incomplete");
            // Reserve the sequence before publishing the operation. Failed creation cannot be retried as a new debit.
            writeHighWater(player, session, sequence, watermark);
        } else {
            if (watermark < sequence) throw new IOException("Lottery replay watermark is missing");
            if (encoded.equals(codec.encode(previous))) return;
            if (!canFollow(previous, encoded, operation)) throw new IOException("Invalid lottery operation transition");
        }
        atomicWrite(file(player, operation.getOperationId()), encoded, previous == null ? null : codec.encode(previous));
        if (operation.isTerminal()) prune(player);
    }

    private boolean canFollow(LotteryOperation before, String encoded, LotteryOperation next) throws IOException {
        if (before.getStage() == LotteryOperation.Stage.COMPLETED && !before.isAcknowledged())
            return encoded.equals(codec.encode(before.acknowledge()));
        if (before.isTerminal() || before.getStage() == LotteryOperation.Stage.UNCERTAIN) return false;
        List<LotteryOperation> allowed = new ArrayList<>();
        allowed.add(before.uncertain());
        switch (before.getStage()) {
            case PREPARED: allowed.add(before.paying()); allowed.add(before.rolledBack()); break;
            case PAYING:
                if (next.getReceipt() != null) allowed.add(before.paid(next.getReceipt()));
                allowed.add(before.rolledBack()); break;
            case PAID:
                if (before.isRollbackPending()) { allowed.add(before.rolledBack()); break; }
                allowed.add(before.selectRewards());
                if (!before.getRewards().isEmpty() && before.getGrantedCount() < before.getCount()) allowed.add(before.granting());
                if (before.getGrantedCount() == before.getCount()) allowed.add(before.complete());
                if (before.getGrantedCount() == 0) allowed.add(before.beginRollback());
                break;
            case GRANTING: allowed.add(before.grantSucceeded()); break;
            default: return false;
        }
        for (LotteryOperation candidate : allowed) if (encoded.equals(codec.encode(candidate))) return true;
        return false;
    }

    private void prune(UUID player) throws IOException {
        List<LotteryOperation> terminal = list(player);
        terminal.removeIf(value -> !value.isTerminal());
        terminal.sort(Comparator.comparingLong(LotteryOperation::getCreatedAt)
                .thenComparingLong(value -> value.getRequest().getRequest().getRequestId())
                .thenComparing(LotteryOperation::getOperationId));
        for (int i = 0; i < terminal.size() - 100; i++) {
            LotteryOperation op = terminal.get(i);
            if (highWater(player, op.getRequest().getRequest().getSession())
                    < op.getRequest().getRequest().getRequestId())
                throw new IOException("Cannot prune lottery operation without replay watermark");
            Files.delete(file(player, op.getOperationId()));
        }
    }

    private long highWater(UUID player, UUID session) throws IOException {
        Path file = directory(player).resolve("_sessions").resolve(session + ".json");
        String json = read(file, 4096);
        if (json == null) return 0;
        try {
            LotteryProtocolLimits.json(json, 4096);
            com.google.gson.JsonObject object = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            if (object.get("schemaVersion").getAsBigDecimal().intValueExact() != 1
                    || !session.toString().equals(object.get("session").getAsString()))
                throw new IllegalArgumentException("Invalid replay watermark");
            long value = object.get("sequence").getAsBigDecimal().longValueExact();
            if (value <= 0) throw new IllegalArgumentException("Invalid replay watermark");
            return value;
        } catch (RuntimeException invalid) { throw new IOException("Invalid lottery replay watermark: " + file, invalid); }
    }
    private static String watermarkJson(UUID session, long sequence) {
        com.google.gson.JsonObject object = new com.google.gson.JsonObject();
        object.addProperty("schemaVersion", 1); object.addProperty("session", session.toString());
        object.addProperty("sequence", sequence);
        return object.toString();
    }
    private void writeHighWater(UUID player, UUID session, long sequence, long previous) throws IOException {
        atomicWrite(directory(player).resolve("_sessions").resolve(session + ".json"), watermarkJson(session, sequence),
                previous == 0 ? null : watermarkJson(session, previous));
    }

    @Override public void flush() { }

    private Path directory(UUID playerId) throws IOException {
        if (playerId == null) throw new IOException("Lottery player required");
        return root.resolve(playerId.toString());
    }
    private Path file(UUID playerId, String operationId) throws IOException {
        try { LotteryProtocolLimits.operationId(operationId); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid lottery operation path", invalid); }
        return directory(playerId).resolve(operationId + ".json");
    }

    private static String read(Path file, int maxBytes) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw new IOException("Lottery record is not a regular file: " + file);
            }
            if (attributes.size() > maxBytes) throw new IOException("Lottery record exceeds limits: " + file);
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > maxBytes) throw new IOException("Lottery record exceeds limits: " + file);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) { return null; }
    }

    private void atomicWrite(Path file, String json, String expected) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "lottery-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer bytes = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            long started = System.nanoTime();
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Lottery write interrupted");
                if (!java.util.Objects.equals(expected, read(file, LotteryOperationCodec.MAX_BYTES)))
                    throw new IOException("Lottery record changed before replacement: " + file);
                try { replacement.replace(temporary, file); break; }
                catch (AccessDeniedException denied) {
                    if (!System.getProperty("os.name", "").startsWith("Windows")
                            || System.nanoTime() - started >= 1_000_000_000L) throw denied;
                    Files.readAttributes(temporary, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    try { Thread.sleep(25); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        InterruptedIOException failure = new InterruptedIOException("Lottery write interrupted");
                        failure.initCause(interrupted); failure.addSuppressed(denied); throw failure;
                    }
                }
            }
            if (!json.equals(read(file, LotteryOperationCodec.MAX_BYTES)))
                throw new IOException("Lottery record readback failed: " + file);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
