package xin.vanilla.sakura.internal.server.dev;

import java.util.Arrays;

/** Bounded per-operation wall-clock samples for development profiling. */
final class SakuraNetworkSmokeTimings {
    private final long[] values;
    private int count;
    private long total;
    private long maximum;

    SakuraNetworkSmokeTimings(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Positive sample capacity required");
        values = new long[capacity];
    }

    void record(long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("Negative duration");
        if (count == values.length) throw new IllegalStateException("Smoke sample capacity exceeded");
        long nextTotal = Math.addExact(total, nanos);
        values[count++] = nanos;
        total = nextTotal;
        maximum = Math.max(maximum, nanos);
    }

    int count() { return count; }
    long average() { return count == 0 ? 0 : total / count; }

    long percentile(int percentile) {
        if (percentile < 1 || percentile > 100) throw new IllegalArgumentException("Invalid percentile");
        if (count == 0) return 0;
        long[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(count * (percentile / 100.0)) - 1];
    }

    long maximum() { return maximum; }

    String summary() {
        return "count=" + count + " average-ns=" + average() + " p50-ns=" + percentile(50)
                + " p95-ns=" + percentile(95) + " max-ns=" + maximum;
    }
}
