package xin.vanilla.sakura.data.calendar;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class BuiltInLunarCalendarRuleTest {
    @Test
    public void preservesKnownLunarConversionsThroughRuleRegistry() {
        DateCalendar calendar = CalendarRegistry.builtIns()
                .require(CalendarIds.CHINESE_LUNAR);

        assertEquals(new CalendarDate(2025, 1, 1, false),
                calendar.toCalendarDate(LocalDate.of(2025, 1, 29)));
        assertEquals(LocalDate.of(2025, 1, 29), calendar.toIsoDate(2025, 1, 1));
    }
}
