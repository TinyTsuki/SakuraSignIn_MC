package xin.vanilla.sakura.data.calendar;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CalendarRuleRepositoryTest {
    @Test
    public void writesDefaultsAndLoadsAdditionalTableCalendar() throws Exception {
        Path directory = Files.createTempDirectory("sakura-calendar-rules");
        CalendarRuleRepository repository = new CalendarRuleRepository(directory);

        CalendarRegistry defaults = repository.loadOrCreate();
        assertTrue(Files.isRegularFile(directory.resolve("calendar-rules.json")));
        assertTrue(defaults.find(CalendarIds.GREGORIAN).isPresent());
        assertTrue(defaults.find(CalendarIds.CHINESE_LUNAR).isPresent());

        CalendarRuleDocument document = repository.readDocument();
        document.getCalendars().add(new CalendarRuleDefinition(
                "example:short_year", "word.example.short_year",
                "2026-01-01", 1,
                Arrays.asList(new CalendarYearRule(1, Arrays.asList(
                        new CalendarMonthRule(1, 20, false),
                        new CalendarMonthRule(2, 20, false))))));
        repository.saveDocument(document);

        DateCalendar calendar = repository.loadOrCreate().require("example:short_year");
        assertEquals(new CalendarDate(1, 2, 1, false),
                calendar.toCalendarDate(LocalDate.of(2026, 1, 21)));
        assertEquals(LocalDate.of(2026, 2, 9), calendar.toIsoDate(1, 2, 20));
    }

    @Test
    public void customCalendarsMayUseNonGregorianMonthNumbers() {
        CalendarRuleDefinition definition = new CalendarRuleDefinition(
                "example:thirteen_months", "Thirteen Months", "2026-01-01", 1,
                Arrays.asList(new CalendarYearRule(1, Arrays.asList(
                        new CalendarMonthRule(1, 20, false),
                        new CalendarMonthRule(13, 20, false)))));

        DateCalendar calendar = CalendarRegistry.fromDefinitions(
                Arrays.asList(definition)).require("example:thirteen_months");

        assertTrue(calendar.supportsMonthDay(13, 20));
        assertEquals(LocalDate.of(2026, 1, 21), calendar.toIsoDate(1, 13, 1));
    }

    @Test
    public void rejectsIntercalaryMonthWithoutItsOrdinaryMonth() {
        CalendarRuleDefinition definition = new CalendarRuleDefinition(
                "example:broken", "Broken", "2026-01-01", 1,
                Arrays.asList(new CalendarYearRule(1, Arrays.asList(
                        new CalendarMonthRule(2, 20, true)))));

        try {
            CalendarRegistry.fromDefinitions(Arrays.asList(definition));
            fail("Expected invalid calendar rule to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("calendar month"));
        }
    }
}
