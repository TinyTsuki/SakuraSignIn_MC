package xin.vanilla.sakura.data.personaldate;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class LunarCalendarTest {
    private final LunarCalendar calendar = new LunarCalendar();

    @Test
    public void convertsKnownNewYearAndLeapMonthDates() {
        assertEquals(new LunarDate(2025, 1, 1, false),
                calendar.toLunar(LocalDate.of(2025, 1, 29)));
        assertEquals(new LunarDate(2025, 6, 1, false),
                calendar.toLunar(LocalDate.of(2025, 6, 25)));
        assertEquals(new LunarDate(2025, 6, 1, true),
                calendar.toLunar(LocalDate.of(2025, 7, 25)));
    }

    @Test
    public void convertsNormalAndLeapOccurrencesBackToSolarDates() {
        assertEquals(LocalDate.of(2025, 6, 25),
                calendar.toSolar(new LunarDate(2025, 6, 1, false)));
        assertEquals(LocalDate.of(2025, 7, 25),
                calendar.toSolar(new LunarDate(2025, 6, 1, true)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSolarDatesBeforeSupportedBase() {
        calendar.toLunar(LocalDate.of(1900, 1, 30));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLunarYearsAfterSupportedRange() {
        calendar.toSolar(new LunarDate(2101, 1, 1, false));
    }
}
