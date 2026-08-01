package xin.vanilla.sakura.data.personaldate;

import java.time.LocalDate;

/**
 * 1900 至 2100 年农历换算。主体年份的月长与闰月已对照香港天文台年度表校验。
 */
public final class LunarCalendar {
    private static final int FIRST_YEAR = 1900;
    private static final int LAST_YEAR = 2100;
    private static final LocalDate BASE_DATE = LocalDate.of(1900, 1, 31);
    private static final LocalDate LAST_SOLAR_DATE = LocalDate.of(2100, 12, 31);

    private static final int[] YEAR_DATA = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x16a95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06aa0, 0x1a6c4, 0x0aae0,
            0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
            0x0d520
    };

    public LunarDate toLunar(LocalDate solarDate) {
        requireSupportedSolarDate(solarDate);
        long offset = java.time.temporal.ChronoUnit.DAYS.between(BASE_DATE, solarDate);
        int year = FIRST_YEAR;
        while (year <= LAST_YEAR && offset >= daysInYear(year)) {
            offset -= daysInYear(year++);
        }

        int leapMonth = leapMonth(year);
        int month = 1;
        boolean leap = false;
        while (month <= 12) {
            int monthDays = leap ? daysInLeapMonth(year) : daysInMonth(year, month);
            if (offset < monthDays) {
                return new LunarDate(year, month, (int) offset + 1, leap);
            }
            offset -= monthDays;
            if (month == leapMonth) {
                if (leap) {
                    leap = false;
                    month++;
                } else {
                    leap = true;
                }
            } else {
                month++;
            }
        }
        throw new IllegalArgumentException("Unsupported solar date: " + solarDate);
    }

    public LocalDate toSolar(LunarDate lunarDate) {
        requireSupportedLunarDate(lunarDate);
        long offset = 0;
        for (int year = FIRST_YEAR; year < lunarDate.getYear(); year++) {
            offset += daysInYear(year);
        }
        for (int month = 1; month < lunarDate.getMonth(); month++) {
            offset += daysInMonth(lunarDate.getYear(), month);
            if (leapMonth(lunarDate.getYear()) == month) {
                offset += daysInLeapMonth(lunarDate.getYear());
            }
        }
        if (lunarDate.isLeapMonth()) {
            offset += daysInMonth(lunarDate.getYear(), lunarDate.getMonth());
        }
        LocalDate result = BASE_DATE.plusDays(offset + lunarDate.getDay() - 1L);
        requireSupportedSolarDate(result);
        return result;
    }

    private static int daysInYear(int year) {
        int days = 348;
        int data = yearData(year);
        for (int bit = 0x8000; bit > 0x8; bit >>= 1) {
            if ((data & bit) != 0) {
                days++;
            }
        }
        return days + daysInLeapMonth(year);
    }

    private static int daysInMonth(int year, int month) {
        return (yearData(year) & (0x10000 >> month)) == 0 ? 29 : 30;
    }

    private static int leapMonth(int year) {
        return yearData(year) & 0xf;
    }

    private static int daysInLeapMonth(int year) {
        if (leapMonth(year) == 0) {
            return 0;
        }
        return (yearData(year) & 0x10000) == 0 ? 29 : 30;
    }

    private static int yearData(int year) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw new IllegalArgumentException("Unsupported lunar year: " + year);
        }
        return YEAR_DATA[year - FIRST_YEAR];
    }

    private static void requireSupportedSolarDate(LocalDate date) {
        if (date == null || date.isBefore(BASE_DATE) || date.isAfter(LAST_SOLAR_DATE)) {
            throw new IllegalArgumentException("Unsupported solar date: " + date);
        }
    }

    private static void requireSupportedLunarDate(LunarDate date) {
        if (date == null || date.getYear() < FIRST_YEAR || date.getYear() > LAST_YEAR
                || date.getMonth() < 1 || date.getMonth() > 12) {
            throw new IllegalArgumentException("Unsupported lunar date: " + date);
        }
        int leap = leapMonth(date.getYear());
        if (date.isLeapMonth() && leap != date.getMonth()) {
            throw new IllegalArgumentException("The lunar month is not a leap month: " + date);
        }
        int maxDay = date.isLeapMonth()
                ? daysInLeapMonth(date.getYear())
                : daysInMonth(date.getYear(), date.getMonth());
        if (date.getDay() < 1 || date.getDay() > maxDay) {
            throw new IllegalArgumentException("Invalid lunar day: " + date);
        }
    }
}
