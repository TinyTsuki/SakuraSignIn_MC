package xin.vanilla.sakura.data.personaldate;

import org.junit.Test;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.calendar.CalendarRegistry;

import java.time.LocalDate;
import java.util.List;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PersonalDateEvaluatorTest {
    private final PersonalDateEvaluator evaluator = new PersonalDateEvaluator(CalendarRegistry.builtIns());

    @Test
    public void yearlySolarWindowIsInclusiveAndClaimCursorSuppressesDuplicate() {
        PersonalDatePreset preset = preset(PersonalDateRecurrence.YEARLY,
                CalendarIds.GREGORIAN, 1, 1);
        PlayerPersonalDateSlot slot = slot(CalendarIds.GREGORIAN, 8, 15);

        List<PersonalDateOccurrence> before = evaluator.findActiveOccurrences(
                preset, slot, LocalDate.of(2026, 8, 14));
        List<PersonalDateOccurrence> after = evaluator.findActiveOccurrences(
                preset, slot, LocalDate.of(2026, 8, 16));

        assertEquals(LocalDate.of(2026, 8, 15), before.get(0).getTargetDate());
        assertEquals(before, after);

        slot.setLastClaimedOccurrenceKey(before.get(0).getOccurrenceKey());
        assertTrue(evaluator.findActiveOccurrences(
                preset, slot, LocalDate.of(2026, 8, 16)).isEmpty());
    }

    @Test
    public void monthlyWindowsReturnOverlappingOccurrencesChronologically() {
        PersonalDatePreset preset = preset(PersonalDateRecurrence.MONTHLY,
                CalendarIds.GREGORIAN, 20, 20);
        PlayerPersonalDateSlot slot = slot(CalendarIds.GREGORIAN, 0, 15);

        List<PersonalDateOccurrence> occurrences = evaluator.findActiveOccurrences(
                preset, slot, LocalDate.of(2026, 2, 1));

        assertEquals(2, occurrences.size());
        assertEquals(LocalDate.of(2026, 1, 15), occurrences.get(0).getTargetDate());
        assertEquals(LocalDate.of(2026, 2, 15), occurrences.get(1).getTargetDate());
        assertEquals("MONTHLY:2026-01", occurrences.get(0).getOccurrenceKey());
    }

    @Test
    public void yearlyLunarRuleUsesFirstMonthOccurrence() {
        PersonalDatePreset preset = preset(PersonalDateRecurrence.YEARLY,
                CalendarIds.CHINESE_LUNAR, 0, 40);
        PlayerPersonalDateSlot slot = slot(CalendarIds.CHINESE_LUNAR, 6, 1);

        List<PersonalDateOccurrence> occurrences = evaluator.findActiveOccurrences(
                preset, slot, LocalDate.of(2025, 7, 25));

        assertEquals(1, occurrences.size());
        assertEquals(LocalDate.of(2025, 6, 25), occurrences.get(0).getTargetDate());
        assertEquals("YEARLY:2025", occurrences.get(0).getOccurrenceKey());
    }

    private static PersonalDatePreset preset(PersonalDateRecurrence recurrence,
                                             String calendarId,
                                             int before, int after) {
        return new PersonalDatePreset(
                "server_day",
                "Server Day",
                recurrence,
                Collections.singletonList(calendarId),
                2,
                PersonalDateDeliveryMode.SIGN_IN,
                before,
                after,
                new RewardList()
        );
    }

    private static PlayerPersonalDateSlot slot(String calendarId, int month, int day) {
        return new PlayerPersonalDateSlot("server_day", 0, calendarId, month, day, "");
    }
}
