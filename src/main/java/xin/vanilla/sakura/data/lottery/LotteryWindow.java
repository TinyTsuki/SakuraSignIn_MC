package xin.vanilla.sakura.data.lottery;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class LotteryWindow {
    private LotteryWindow() {
    }

    static LocalDateTime boundary(String value, boolean end) {
        if (value.length() == 10) {
            LocalDate date = LocalDate.parse(value);
            return (end ? date.plusDays(1) : date).atStartOfDay();
        }
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    public static boolean isOpen(LotteryPool pool, long epochMillis, ZoneId zone) {
        Instant time = Instant.ofEpochMilli(epochMillis);
        return (pool.getStartDate().isEmpty() || !time.isBefore(boundary(pool.getStartDate(), false).atZone(zone).toInstant()))
                && (pool.getEndDate().isEmpty() || time.isBefore(boundary(pool.getEndDate(), true).atZone(zone).toInstant()));
    }
}
