package xin.vanilla.sakura.data.lottery;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class LotteryWindow {
    private final boolean open, opensNext;
    private final long remainingMillis;

    private LotteryWindow(boolean open, boolean opensNext, long remainingMillis) {
        this.open = open;
        this.opensNext = opensNext;
        this.remainingMillis = remainingMillis;
    }

    public boolean open() {
        return open;
    }

    public boolean opensNext() {
        return opensNext;
    }

    public long remainingMillis() {
        return remainingMillis;
    }

    static LocalDateTime boundary(String value, boolean end) {
        if (value.length() == 10) {
            LocalDate date = LocalDate.parse(value);
            return (end ? date.plusDays(1) : date).atStartOfDay();
        }
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    public static boolean isOpen(LotteryPool pool, long epochMillis, ZoneId zone) {
        return at(pool, epochMillis, zone).open();
    }

    public static LotteryWindow at(LotteryPool pool, long epochMillis, ZoneId zone) {
        Instant time = Instant.ofEpochMilli(epochMillis);
        if (!pool.getStartDate().isEmpty()) {
            Instant start = boundary(pool.getStartDate(), false).atZone(zone).toInstant();
            if (time.isBefore(start)) return new LotteryWindow(false, true, start.toEpochMilli() - epochMillis);
        }
        if (!pool.getEndDate().isEmpty()) {
            Instant end = boundary(pool.getEndDate(), true).atZone(zone).toInstant();
            if (!time.isBefore(end)) return new LotteryWindow(false, false, 0);
            return new LotteryWindow(true, false, end.toEpochMilli() - epochMillis);
        }
        return new LotteryWindow(true, false, 0);
    }
}
