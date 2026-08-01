package xin.vanilla.sakura.data.calendar;

import java.time.LocalDate;

/** Loader-neutral calendar conversion contract. */
public interface DateCalendar {
    String id();

    String displayNameKey();

    CalendarDate toCalendarDate(LocalDate isoDate);

    LocalDate toIsoDate(int year, int month, int day);

    boolean supportsMonthDay(int month, int day);

    boolean supports(LocalDate isoDate);
}
