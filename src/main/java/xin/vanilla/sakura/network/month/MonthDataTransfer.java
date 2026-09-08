package xin.vanilla.sakura.network.month;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class MonthDataTransfer {
    public static final int MAX_PART_CHARS = 16384;
    public static final int MAX_SNAPSHOT_BYTES = 4 * 1024 * 1024;
    public static final int MAX_RECORDS = 1024;

    private MonthDataTransfer() { }

    public static List<Part> encode(UUID player, String month, long revision, List<String> records) {
        validateIdentity(player, month, revision);
        require(records != null && records.size() <= MAX_RECORDS, "Invalid record count");
        List<String> snapshot = new ArrayList<>(records);
        require(snapshot.size() <= MAX_RECORDS, "Too many records");
        List<byte[]> encoded = new ArrayList<>(snapshot.size());
        int size = 4;
        for (String record : snapshot) {
            require(record != null && record.length() <= MAX_SNAPSHOT_BYTES - size - 4,
                    "Record exceeds snapshot budget");
            try {
                ByteBuffer utf8 = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(record));
                require(utf8.remaining() <= MAX_SNAPSHOT_BYTES - size - 4, "Snapshot exceeds byte budget");
                byte[] bytes = new byte[utf8.remaining()];
                utf8.get(bytes);
                encoded.add(bytes);
                size += 4 + bytes.length;
            } catch (CharacterCodingException e) {
                throw new IllegalArgumentException("Malformed record Unicode", e);
            }
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(size);
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(encoded.size());
            for (byte[] bytes : encoded) {
                output.writeInt(bytes.length);
                output.write(bytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException("In-memory snapshot encoding failed", e);
        }
        String payload = Base64.getEncoder().encodeToString(buffer.toByteArray());
        int count = partCount(size);
        List<Part> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int start = index * MAX_PART_CHARS;
            result.add(new Part(player, month, revision, index, count, size,
                    payload.substring(start, Math.min(start + MAX_PART_CHARS, payload.length()))));
        }
        return Collections.unmodifiableList(result);
    }

    private static void validateIdentity(UUID player, String month, long revision) {
        require(player != null, "Missing player");
        require(month != null && month.matches("[0-9]{4}-(0[1-9]|1[0-2])"), "Invalid month");
        require(revision > 0, "Invalid revision");
    }

    private static int encodedLength(int bytes) { return ((bytes + 2) / 3) * 4; }
    private static int partCount(int bytes) { return (encodedLength(bytes) + MAX_PART_CHARS - 1) / MAX_PART_CHARS; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static final class Part {
        private final UUID player;
        private final String month;
        private final long revision;
        private final int index, count, decodedBytes;
        private final String payload;

        public Part(UUID player, String month, long revision, int index, int count, int decodedBytes, String payload) {
            validateIdentity(player, month, revision);
            require(decodedBytes >= 4 && decodedBytes <= MAX_SNAPSHOT_BYTES, "Invalid decoded size");
            require(count == partCount(decodedBytes) && index >= 0 && index < count, "Invalid part position");
            int total = encodedLength(decodedBytes);
            int offset = index * MAX_PART_CHARS;
            int expected = Math.min(MAX_PART_CHARS, total - offset);
            require(payload != null && payload.length() == expected, "Invalid payload length");
            int padding = (3 - decodedBytes % 3) % 3;
            for (int i = 0; i < payload.length(); i++) {
                char c = payload.charAt(i);
                boolean data = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                        || c >= '0' && c <= '9' || c == '+' || c == '/';
                require(offset + i >= total - padding ? c == '=' : data, "Invalid Base64 character or padding");
            }
            this.player = player;
            this.month = month;
            this.revision = revision;
            this.index = index;
            this.count = count;
            this.decodedBytes = decodedBytes;
            this.payload = payload;
        }

        public UUID getPlayer() { return player; }
        public String getMonth() { return month; }
        public long getRevision() { return revision; }
        public int getIndex() { return index; }
        public int getCount() { return count; }
        public int getDecodedBytes() { return decodedBytes; }
        public String getPayload() { return payload; }
    }

    /** Confined to the client thread; expire on ticks and clear on disconnect. */
    public static final class Receiver {
        private static final long TTL_NANOS = 30_000_000_000L;
        private final LongSupplier nanoClock;
        private final Map<Long, Pending> pending = new HashMap<>();
        private long highestStartedRevision;

        public Receiver(LongSupplier nanoClock) {
            this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        }

        public Optional<List<String>> accept(Part part) {
            require(part != null, "Missing part");
            expire();
            Pending current = pending.get(part.revision);
            if (current == null) {
                if (part.index != 0 || part.revision <= highestStartedRevision) return Optional.empty();
                Pending replaced = null;
                for (Pending candidate : pending.values()) {
                    if (candidate.first.month.equals(part.month)) replaced = candidate;
                }
                require(replaced != null || pending.size() < 2, "Too many in-flight snapshots");
                if (replaced != null) pending.remove(replaced.first.revision);
                current = new Pending(part, nanoClock.getAsLong());
                pending.put(part.revision, current);
                highestStartedRevision = part.revision;
            }
            try {
                Part first = current.first;
                require(first.player.equals(part.player) && first.month.equals(part.month)
                        && first.count == part.count && first.decodedBytes == part.decodedBytes,
                        "Conflicting snapshot metadata");
                if (part.index < current.next) {
                    require(part.payload.equals(current.payloads[part.index]), "Conflicting duplicate");
                    return Optional.empty();
                }
                require(part.index == current.next, "Out-of-order snapshot part");
                current.payloads[current.next++] = part.payload;
                if (current.next != first.count) return Optional.empty();
                pending.remove(part.revision);
                return Optional.of(decode(current));
            } catch (IllegalArgumentException e) {
                pending.remove(part.revision);
                throw e;
            }
        }

        public void expire() {
            long now = nanoClock.getAsLong();
            pending.values().removeIf(value -> now - value.started >= TTL_NANOS);
        }

        public void clear() {
            pending.clear();
            highestStartedRevision = 0;
        }
    }

    private static List<String> decode(Pending pending) {
        StringBuilder base64 = new StringBuilder(encodedLength(pending.first.decodedBytes));
        for (String payload : pending.payloads) base64.append(payload);
        byte[] frame = Base64.getDecoder().decode(base64.toString());
        require(frame.length == pending.first.decodedBytes, "Decoded size mismatch");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(frame))) {
            int count = input.readInt();
            require(count >= 0 && count <= MAX_RECORDS && count <= input.available() / 4, "Invalid record count");
            List<String> records = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int length = input.readInt();
                require(length >= 0 && length <= input.available(), "Invalid record length");
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                records.add(StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString());
            }
            require(input.available() == 0, "Trailing snapshot bytes");
            return Collections.unmodifiableList(records);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed snapshot frame", e);
        }
    }

    private static final class Pending {
        private final Part first;
        private final long started;
        private final String[] payloads;
        private int next;

        private Pending(Part first, long started) {
            this.first = first;
            this.started = started;
            this.payloads = new String[first.count];
        }
    }
}
