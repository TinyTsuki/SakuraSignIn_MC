package xin.vanilla.sakura.data.personaldate;

import org.junit.Test;
import xin.vanilla.sakura.reward.RewardList;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PersonalDateEvaluatorTest {
    private final PersonalDateEvaluator evaluator = new PersonalDateEvaluator(new LunarCalendar());

    @Test
    public void yearlySolarWindowIsInclusiveAndClaimCursorSuppressesDuplicate() {
        PersonalDatePreset preset = preset(PersonalDateRecurrence.YEARLY,
                PersonalDateCalendarPolicy.SOLAR_ONLY, 1, 1);
        PlayerPersonalDateSlot slot = slot(PersonalDateCalendar.SOLAR, 8, 15);

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
                PersonalDateCalendarPolicy.SOLAR_ONLY, 20, 20);
        PlayerPersonalDateSlot slot = slot(PersonalDateCalendar.SOLAR, 0, 15);

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
                PersonalDateCalendarPolicy.LUNAR_ONLY, 0, 40);
        PlayerPersonalDateSlot slot = slot(PersonalDateCalendar.LUNAR, 6, 1);

        List<PersonalDateOccurrence> occurrences = evaluator.findActiveOccurrences(
                preset, slot, LocalDate.of(2025, 7, 25));

        assertEquals(1, occurrences.size());
        assertEquals(LocalDate.of(2025, 6, 25), occurrences.get(0).getTargetDate());
        assertEquals("YEARLY:2025", occurrences.get(0).getOccurrenceKey());
    }

    private static PersonalDatePreset preset(PersonalDateRecurrence recurrence,
                                             PersonalDateCalendarPolicy policy,
                                             int before, int after) {
        return new PersonalDatePreset(
                "server_day",
                "Server Day",
                recurrence,
                policy,
                2,
                PersonalDateDeliveryMode.SIGN_IN,
                before,
                after,
                new RewardList()
        );
    }

    private static PlayerPersonalDateSlot slot(PersonalDateCalendar calendar, int month, int day) {
        return new PlayerPersonalDateSlot("server_day", 0, calendar, month, day, "");
    }
}
