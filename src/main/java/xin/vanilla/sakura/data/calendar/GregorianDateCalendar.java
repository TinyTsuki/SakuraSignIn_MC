package xin.vanilla.sakura.data.calendar;

import java.time.DateTimeException;
import java.time.LocalDate;

final class GregorianDateCalendar implements DateCalendar {
    @Override
    public String id() {
        return CalendarIds.GREGORIAN;
    }

    @Override
    public String displayNameKey() {
        return "word.sakura_sign_in.calendar_gregorian";
    }

    @Override
    public CalendarDate toCalendarDate(LocalDate isoDate) {
        if (isoDate == null) {
            throw new IllegalArgumentException("ISO date is required");
        }
        return new CalendarDate(isoDate.getYear(), isoDate.getMonthValue(),
                isoDate.getDayOfMonth(), false);
    }

    @Override
    public LocalDate toIsoDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("Invalid Gregorian date", invalid);
        }
    }

    @Override
    public boolean supportsMonthDay(int month, int day) {
        if (month == 0) {
            return day >= 1 && day <= 31;
        }
        try {
            LocalDate.of(2000, month, day);
            return true;
        } catch (DateTimeException invalid) {
            return false;
        }
    }

    @Override
    public boolean supports(LocalDate isoDate) {
        return isoDate != null;
    }
}
