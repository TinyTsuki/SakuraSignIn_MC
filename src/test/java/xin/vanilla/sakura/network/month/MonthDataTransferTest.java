package xin.vanilla.sakura.network.month;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class MonthDataTransferTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String MONTH = "2026-09";
    private final AtomicLong clock = new AtomicLong();
    private final MonthDataTransfer.Receiver receiver = new MonthDataTransfer.Receiver(clock::get);

    @Test
    public void usesCountAndStandardUtf8ByteLengthsWithEmptySnapshotSupport() {
        List<MonthDataTransfer.Part> parts = encode(1, MONTH, Arrays.asList("A", "\u732b"));
        assertEquals(1, parts.size());
        assertArrayEquals(new byte[]{0, 0, 0, 2, 0, 0, 0, 1, 65,
                0, 0, 0, 3, (byte) 0xe7, (byte) 0x8c, (byte) 0xab},
                Base64.getDecoder().decode(parts.get(0).getPayload()));
        assertEquals(Arrays.asList("A", "\u732b"), complete(parts));
        List<MonthDataTransfer.Part> empty = encode(2, MONTH, Collections.emptyList());
        assertEquals("AAAAAA==", empty.get(0).getPayload());
        assertEquals(Collections.emptyList(), complete(empty));
    }

    @Test
    public void roundTripsLargeMultibyteRecordWithoutInterpretingSnbt() {
        String record = "{text:\"" + repeat("\u732b\ud83c\udf38\u0000", 12000) + "\"}";
        List<String> expected = Arrays.asList(record, "{}", "");
        List<MonthDataTransfer.Part> parts = encode(1, MONTH, expected);
        assertTrue(record.length() > 32768);
        assertTrue(parts.size() > 2);
        for (int i = 0; i < parts.size(); i++) {
            MonthDataTransfer.Part part = parts.get(i);
            assertEquals(PLAYER, part.getPlayer());
            assertEquals(MONTH, part.getMonth());
            assertEquals(1, part.getRevision());
            assertEquals(i, part.getIndex());
            assertEquals(parts.size(), part.getCount());
            assertTrue(part.getPayload().length() <= 16384);
            if (i < parts.size() - 1) assertEquals(16384, part.getPayload().length());
        }
        assertEquals(expected, complete(parts));
    }

    @Test
    public void freezesInputPartsAndCompletedRecords() {
        List<String> input = new ArrayList<>(Collections.singletonList("{v:1}"));
        List<MonthDataTransfer.Part> parts = encode(1, MONTH, input);
        input.set(0, "{v:2}");
        input.clear();
        assertThrows(UnsupportedOperationException.class, () -> parts.clear());
        List<String> result = complete(parts);
        assertEquals(Collections.singletonList("{v:1}"), result);
        assertThrows(UnsupportedOperationException.class, () -> result.set(0, "changed"));
        assertThrows(UnsupportedOperationException.class, () -> result.add("changed"));
    }

    @Test
    public void acceptsExactByteAndRecordBudgetsAndRejectsOverflow() {
        String limit = repeat("a", 4 * 1024 * 1024 - 8);
        List<MonthDataTransfer.Part> parts = encode(1, MONTH, Collections.singletonList(limit));
        assertEquals(4 * 1024 * 1024, parts.get(0).getDecodedBytes());
        assertEquals(342, parts.size());
        assertEquals(Collections.singletonList(limit), complete(parts));
        assertEquals(Collections.nCopies(1024, ""), complete(encode(2, MONTH, Collections.nCopies(1024, ""))));
        invalid(() -> encode(3, MONTH, Collections.singletonList(limit + "a")));
        invalid(() -> encode(3, MONTH, Collections.singletonList(repeat("\u732b", 1400000))));
        invalid(() -> encode(3, MONTH, Collections.nCopies(1025, "")));
        invalid(() -> encode(3, MONTH, Arrays.asList("ok", null)));
        invalid(() -> encode(3, MONTH, null));
        invalid(() -> encode(3, MONTH, Collections.singletonList("\ud800")));
    }

    @Test
    public void splitsExactlyAtPartBoundary() {
        List<MonthDataTransfer.Part> one = encode(1, MONTH, Collections.singletonList(repeat("a", 12280)));
        assertEquals(1, one.size());
        assertEquals(16384, one.get(0).getPayload().length());
        List<MonthDataTransfer.Part> two = encode(2, MONTH, Collections.singletonList(repeat("a", 12281)));
        assertEquals(2, two.size());
        assertEquals(4, two.get(1).getPayload().length());
        assertEquals(Collections.singletonList(repeat("a", 12281)), complete(two));
    }

    @Test
    public void rejectsInvalidIdentityMonthAndRevisionBeforeAdmission() {
        for (String month : Arrays.asList(null, "", "2026-00", "2026-13", "2026-9", "26-09",
                "2026-09-01", "+2026-09", "2026-09\n", "\uff12\uff10\uff12\uff16-09")) {
            invalid(() -> new MonthDataTransfer.Part(PLAYER, month, 1, 0, 1, 4, "AAAAAA=="));
            invalid(() -> encode(1, month, Collections.emptyList()));
        }
        invalid(() -> new MonthDataTransfer.Part(null, MONTH, 1, 0, 1, 4, "AAAAAA=="));
        invalid(() -> MonthDataTransfer.encode(null, MONTH, 1, Collections.emptyList()));
        for (long revision : new long[]{0, -1, Long.MIN_VALUE}) {
            invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, revision, 0, 1, 4, "AAAAAA=="));
            invalid(() -> encode(revision, MONTH, Collections.emptyList()));
        }
        assertEquals(Collections.emptyList(), complete(encode(Long.MAX_VALUE, "9999-12", Collections.emptyList())));
    }

    @Test
    public void rejectsImpossiblePartDimensionsAndNonBase64Payloads() {
        for (int bytes : new int[]{-1, 0, 3, 4194305, Integer.MAX_VALUE}) {
            invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, 1, 0, 1, bytes, "AAAAAA=="));
        }
        for (int count : new int[]{-1, 0, 2, Integer.MAX_VALUE}) {
            invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, 1, 0, count, 4, "AAAAAA=="));
        }
        for (int index : new int[]{-1, 1, Integer.MAX_VALUE}) {
            invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, 1, index, 1, 4, "AAAAAA=="));
        }
        for (String payload : Arrays.asList(null, "", "AAAAAA=", "AAAAAA===", "AAAA A==",
                "AAAA\u00e9A==", "AAAA-A==", "AAAA_A==", "AAAA\nA==", "=AAAAA==", "AAAAAAAA")) {
            invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, 1, 0, 1, 4, payload));
        }
        invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, 1, 0, 2, 12289, repeat("A", 16383)));
        invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, 1, 1, 2, 12289, repeat("A", 16384)));
        invalid(() -> new MonthDataTransfer.Part(PLAYER, MONTH, 1, 0, 2, 12289, repeat("A", 16383) + "="));
    }

    @Test
    public void exposesNothingUntilCompleteAndIgnoresIdenticalOlderDuplicates() {
        List<MonthDataTransfer.Part> parts = large(1, MONTH);
        assertFalse(receiver.accept(parts.get(0)).isPresent());
        assertFalse(receiver.accept(parts.get(1)).isPresent());
        assertFalse(receiver.accept(parts.get(0)).isPresent());
        assertFalse(receiver.accept(parts.get(1)).isPresent());
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(parts, 2));
        for (MonthDataTransfer.Part part : parts) assertFalse(receiver.accept(part).isPresent());
    }

    @Test
    public void ignoresOrphansWithoutAdvancingRevisionWatermark() {
        List<MonthDataTransfer.Part> future = large(100, MONTH);
        assertFalse(receiver.accept(future.get(1)).isPresent());
        assertEquals(Collections.singletonList("old"), complete(encode(1, MONTH, Collections.singletonList("old"))));
    }

    @Test
    public void gapRejectsAndClearsOnlyItsSnapshot() {
        List<MonthDataTransfer.Part> first = large(1, MONTH);
        List<MonthDataTransfer.Part> other = large(2, "2026-10");
        receiver.accept(first.get(0));
        receiver.accept(other.get(0));
        invalid(() -> receiver.accept(first.get(2)));
        assertFalse(receiver.accept(first.get(1)).isPresent());
        assertFalse(receiver.accept(first.get(0)).isPresent());
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(other, 1));
    }

    @Test
    public void conflictingDuplicateClearsAndCannotReplay() {
        List<MonthDataTransfer.Part> parts = large(1, MONTH);
        receiver.accept(parts.get(0));
        String payload = parts.get(0).getPayload();
        MonthDataTransfer.Part changed = new MonthDataTransfer.Part(PLAYER, MONTH, 1, 0,
                parts.size(), parts.get(0).getDecodedBytes(), "B" + payload.substring(1));
        invalid(() -> receiver.accept(changed));
        for (MonthDataTransfer.Part part : parts) assertFalse(receiver.accept(part).isPresent());
        assertEquals(Collections.singletonList("new"), complete(encode(2, MONTH, Collections.singletonList("new"))));
    }

    @Test
    public void conflictingMetadataClearsExistingRevision() {
        for (int field = 0; field < 4; field++) {
            receiver.clear();
            List<MonthDataTransfer.Part> parts = large(1, MONTH);
            MonthDataTransfer.Part first = parts.get(0);
            receiver.accept(first);
            MonthDataTransfer.Part changed = new MonthDataTransfer.Part(
                    field == 0 ? new UUID(0, 0) : PLAYER, field == 1 ? "2026-10" : MONTH,
                    1, 0, field == 3 ? 5 : first.getCount(),
                    field == 2 ? first.getDecodedBytes() + 3 : field == 3 ? 50000 : first.getDecodedBytes(),
                    first.getPayload());
            invalid(() -> receiver.accept(changed));
            for (MonthDataTransfer.Part part : parts) assertFalse(receiver.accept(part).isPresent());
        }
    }

    @Test
    public void newerSameMonthReplacesOnlyThatMonthAndAllowsOtherInterleaving() {
        List<MonthDataTransfer.Part> old = large(1, MONTH);
        List<MonthDataTransfer.Part> other = large(2, "2026-10");
        List<MonthDataTransfer.Part> newest = large(3, MONTH);
        receiver.accept(old.get(0));
        receiver.accept(other.get(0));
        receiver.accept(newest.get(0));
        for (MonthDataTransfer.Part part : old) assertFalse(receiver.accept(part).isPresent());
        assertFalse(receiver.accept(other.get(0)).isPresent());
        assertFalse(receiver.accept(newest.get(1)).isPresent());
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(other, 1));
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(newest, 2));
    }

    @Test
    public void globalWatermarkRejectsStaleStartsEvenForOtherMonthsAfterCompletion() {
        List<MonthDataTransfer.Part> started = large(10, MONTH);
        receiver.accept(started.get(0));
        for (MonthDataTransfer.Part part : large(9, "2026-10")) assertFalse(receiver.accept(part).isPresent());
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(started, 1));
        assertFalse(receiver.accept(encode(10, "2026-11", Collections.emptyList()).get(0)).isPresent());
        assertFalse(receiver.accept(encode(1, MONTH, Collections.emptyList()).get(0)).isPresent());
        assertEquals(Collections.emptyList(), complete(encode(11, MONTH, Collections.emptyList())));
    }

    @Test
    public void thirdMonthRejectsWithoutEvictionOrAdvancingWatermark() {
        List<MonthDataTransfer.Part> first = large(1, MONTH);
        List<MonthDataTransfer.Part> second = large(2, "2026-10");
        List<MonthDataTransfer.Part> third = large(3, "2026-11");
        receiver.accept(first.get(0));
        receiver.accept(second.get(0));
        invalid(() -> receiver.accept(third.get(0)));
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(first, 1));
        assertFalse(receiver.accept(third.get(0)).isPresent());
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(second, 1));
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(third, 1));
    }

    @Test
    public void expiresAtThirtySecondsWithoutTrafficAndPreservesWatermark() {
        List<MonthDataTransfer.Part> parts = large(10, MONTH);
        receiver.accept(parts.get(0));
        clock.set(29999999999L);
        receiver.expire();
        assertFalse(receiver.accept(parts.get(1)).isPresent());
        clock.set(30000000000L);
        receiver.expire();
        for (MonthDataTransfer.Part part : parts) assertFalse(receiver.accept(part).isPresent());
        assertFalse(receiver.accept(encode(9, MONTH, Collections.emptyList()).get(0)).isPresent());
        assertEquals(Collections.emptyList(), complete(encode(11, MONTH, Collections.emptyList())));
    }

    @Test
    public void expiryDuringAcceptFreesCapacityAndDoesNotRefreshOnDuplicates() {
        List<MonthDataTransfer.Part> first = large(1, MONTH);
        List<MonthDataTransfer.Part> second = large(2, "2026-10");
        receiver.accept(first.get(0));
        clock.set(10000000000L);
        receiver.accept(second.get(0));
        clock.set(29999999999L);
        receiver.accept(first.get(0));
        clock.set(30000000000L);
        assertEquals(Collections.emptyList(), complete(encode(3, "2026-11", Collections.emptyList())));
        assertFalse(receiver.accept(first.get(1)).isPresent());
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(second, 1));
    }

    @Test
    public void ttlUsesElapsedNanoTimeAcrossClockWraparound() {
        clock.set(Long.MAX_VALUE - 1000000000L);
        List<MonthDataTransfer.Part> parts = large(1, MONTH);
        receiver.accept(parts.get(0));
        clock.addAndGet(30000000000L);
        receiver.expire();
        for (MonthDataTransfer.Part part : parts) assertFalse(receiver.accept(part).isPresent());
    }

    @Test
    public void disconnectClearsPartialsAndResetsGlobalRevisionWatermark() {
        List<MonthDataTransfer.Part> parts = large(100, MONTH);
        receiver.accept(parts.get(0));
        receiver.clear();
        assertFalse(receiver.accept(parts.get(1)).isPresent());
        assertEquals(Collections.singletonList("reset"), complete(encode(1, MONTH, Collections.singletonList("reset"))));
        receiver.clear();
        assertEquals(Collections.emptyList(), complete(encode(1, MONTH, Collections.emptyList())));
    }

    @Test
    public void rejectsMalformedCountsLengthsUtf8AndTrailingBytes() {
        List<byte[]> invalidFrames = Arrays.asList(
                ints(-1), ints(1025), ints(Integer.MAX_VALUE), ints(1), ints(1, -1),
                ints(1, Integer.MAX_VALUE), ints(1, 1), ints(2, 0), ints(0, 0), ints(1, 0, 0),
                new byte[]{0, 0, 0, 1, 0, 0, 0, 1, (byte) 0x80},
                new byte[]{0, 0, 0, 1, 0, 0, 0, 2, (byte) 0xc0, (byte) 0x80},
                new byte[]{0, 0, 0, 1, 0, 0, 0, 2, (byte) 0xe7, (byte) 0x8c},
                new byte[]{0, 0, 0, 1, 0, 0, 0, 3, (byte) 0xed, (byte) 0xa0, (byte) 0x80});
        long revision = 0;
        for (byte[] frame : invalidFrames) {
            List<MonthDataTransfer.Part> parts = raw(++revision, MONTH, frame);
            invalid(() -> complete(parts));
            for (MonthDataTransfer.Part part : parts) assertFalse(receiver.accept(part).isPresent());
        }
        assertEquals(Collections.singletonList("valid"), complete(encode(++revision, MONTH, Collections.singletonList("valid"))));
    }

    @Test
    public void malformedMultipartCompletionClearsOnlyItsStateAndAllowsRecovery() {
        byte[] frame = new byte[20000];
        ByteBuffer.wrap(frame).putInt(1).putInt(19992).put((byte) 0x80);
        List<MonthDataTransfer.Part> bad = raw(1, MONTH, frame);
        List<MonthDataTransfer.Part> good = large(2, "2026-10");
        receiver.accept(bad.get(0));
        receiver.accept(good.get(0));
        invalid(() -> receiver.accept(bad.get(1)));
        for (MonthDataTransfer.Part part : bad) assertFalse(receiver.accept(part).isPresent());
        assertEquals(Collections.emptyList(), complete(encode(3, "2026-11", Collections.emptyList())));
        assertEquals(Collections.singletonList(repeat("x", 40000)), finish(good, 1));
    }

    private static List<MonthDataTransfer.Part> encode(long revision, String month, List<String> records) {
        return MonthDataTransfer.encode(PLAYER, month, revision, records);
    }

    private static List<MonthDataTransfer.Part> large(long revision, String month) {
        return encode(revision, month, Collections.singletonList(repeat("x", 40000)));
    }

    private List<String> complete(List<MonthDataTransfer.Part> parts) {
        return finish(parts, 0);
    }

    private List<String> finish(List<MonthDataTransfer.Part> parts, int start) {
        Optional<List<String>> result = Optional.empty();
        for (int i = start; i < parts.size(); i++) {
            result = receiver.accept(parts.get(i));
            if (i != parts.size() - 1) assertFalse("partial snapshot exposed", result.isPresent());
        }
        assertTrue("complete snapshot missing", result.isPresent());
        return result.get();
    }

    private static List<MonthDataTransfer.Part> raw(long revision, String month, byte[] frame) {
        String base64 = Base64.getEncoder().encodeToString(frame);
        List<MonthDataTransfer.Part> parts = new ArrayList<>();
        int count = (base64.length() + 16383) / 16384;
        for (int i = 0; i < count; i++) {
            parts.add(new MonthDataTransfer.Part(PLAYER, month, revision, i, count, frame.length,
                    base64.substring(i * 16384, Math.min((i + 1) * 16384, base64.length()))));
        }
        return parts;
    }

    private static byte[] ints(int... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4);
        for (int value : values) buffer.putInt(value);
        return buffer.array();
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void invalid(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }
}
