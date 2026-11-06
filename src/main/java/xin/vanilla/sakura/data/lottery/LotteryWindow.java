package xin.vanilla.sakura.data.lottery;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record LotteryWindow(boolean open, boolean opensNext, long remainingMillis) {
    static LocalDateTime boundary(String value, boolean end) {
        if (value.length() == 10) {
            LocalDate date = LocalDate.parse(value);
            return (end ? date.plusDays(1) : date).atStartOfDay();
        }
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    public static LotteryWindow at(LotteryPool pool, long now, ZoneId zone) {
        Instant instant = Instant.ofEpochMilli(now);
        if (!pool.getStartDate().isEmpty()) {
            Instant start = boundary(pool.getStartDate(), false).atZone(zone).toInstant();
            if (instant.isBefore(start)) return new LotteryWindow(false, true, start.toEpochMilli() - now);
        }
        if (!pool.getEndDate().isEmpty()) {
            Instant end = boundary(pool.getEndDate(), true).atZone(zone).toInstant();
            if (!instant.isBefore(end)) return new LotteryWindow(false, false, 0);
            return new LotteryWindow(true, false, end.toEpochMilli() - now);
        }
        return new LotteryWindow(true, false, 0);
    }
}
