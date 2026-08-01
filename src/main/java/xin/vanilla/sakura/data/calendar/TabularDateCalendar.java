package xin.vanilla.sakura.data.calendar;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts finite calendars by walking validated sequential month tables. */
final class TabularDateCalendar implements DateCalendar {
    private final CalendarRuleDefinition definition;
    private final LocalDate epoch;
    private final LocalDate lastDate;
    private final List<CalendarMonth> months;

    TabularDateCalendar(CalendarRuleDefinition definition) {
        this.definition = definition;
        this.epoch = LocalDate.parse(definition.getEpochIsoDate());
        this.months = Collections.unmodifiableList(validateAndFlatten(definition));
        long days = months.stream().mapToLong(month -> month.days).sum();
        this.lastDate = epoch.plusDays(days - 1L);
    }

    @Override
    public String id() {
        return definition.getId();
    }

    @Override
    public String displayNameKey() {
        return definition.getDisplayNameKey();
    }

    @Override
    public CalendarDate toCalendarDate(LocalDate isoDate) {
        if (!supports(isoDate)) {
            throw new IllegalArgumentException("Date is outside calendar range: " + isoDate);
        }
        long offset = ChronoUnit.DAYS.between(epoch, isoDate);
        for (CalendarMonth month : months) {
            if (offset < month.days) {
                return new CalendarDate(month.year, month.month, (int) offset + 1,
                        month.intercalary);
            }
            offset -= month.days;
        }
        throw new IllegalArgumentException("Date is outside calendar range: " + isoDate);
    }

    @Override
    public LocalDate toIsoDate(int year, int month, int day) {
        long offset = 0L;
        for (CalendarMonth candidate : months) {
            // Personal dates intentionally resolve the first visible month, not an intercalary repeat.
            if (candidate.year == year && candidate.month == month && !candidate.intercalary) {
                if (day < 1 || day > candidate.days) {
                    break;
                }
                return epoch.plusDays(offset + day - 1L);
            }
            offset += candidate.days;
        }
        throw new IllegalArgumentException("Invalid calendar date: " + year + '-' + month + '-' + day);
    }

    @Override
    public boolean supportsMonthDay(int month, int day) {
        return months.stream().anyMatch(candidate -> !candidate.intercalary
                && (month == 0 || candidate.month == month)
                && day >= 1 && day <= candidate.days);
    }

    @Override
    public boolean supports(LocalDate isoDate) {
        return isoDate != null && !isoDate.isBefore(epoch) && !isoDate.isAfter(lastDate);
    }

    private static List<CalendarMonth> validateAndFlatten(CalendarRuleDefinition definition) {
        if (definition == null || definition.getId() == null
                || !definition.getId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || definition.getDisplayNameKey() == null
                || definition.getDisplayNameKey().trim().isEmpty()
                || definition.getEpochIsoDate() == null
                || definition.getYears() == null || definition.getYears().isEmpty()) {
            throw new IllegalArgumentException("Incomplete calendar rule");
        }
        LocalDate.parse(definition.getEpochIsoDate());
        List<CalendarMonth> result = new ArrayList<>();
        int expectedYear = definition.getFirstYear();
        for (CalendarYearRule year : definition.getYears()) {
            if (year == null || year.getYear() != expectedYear++
                    || year.getMonths() == null || year.getMonths().isEmpty()) {
                throw new IllegalArgumentException("Calendar years must be contiguous");
            }
            java.util.Set<Integer> ordinaryMonths = new java.util.HashSet<>();
            java.util.Set<Integer> intercalaryMonths = new java.util.HashSet<>();
            for (CalendarMonthRule month : year.getMonths()) {
                if (month == null || month.getMonth() < 1 || month.getMonth() > 64
                        || month.getDays() < 1 || month.getDays() > 512) {
                    throw new IllegalArgumentException("Invalid calendar month");
                }
                java.util.Set<Integer> monthSet = month.isIntercalary()
                        ? intercalaryMonths : ordinaryMonths;
                if (!monthSet.add(month.getMonth())
                        || month.isIntercalary() && !ordinaryMonths.contains(month.getMonth())) {
                    throw new IllegalArgumentException("Duplicate or orphan calendar month");
                }
                result.add(new CalendarMonth(year.getYear(), month.getMonth(),
                        month.getDays(), month.isIntercalary()));
            }
        }
        return result;
    }

    private static final class CalendarMonth {
        private final int year;
        private final int month;
        private final int days;
        private final boolean intercalary;

        private CalendarMonth(int year, int month, int days, boolean intercalary) {
            this.year = year;
            this.month = month;
            this.days = days;
            this.intercalary = intercalary;
        }
    }
}
