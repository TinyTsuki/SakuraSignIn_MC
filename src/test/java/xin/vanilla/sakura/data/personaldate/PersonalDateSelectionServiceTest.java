package xin.vanilla.sakura.data.personaldate;

import org.junit.Test;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.data.calendar.CalendarIds;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonalDateSelectionServiceTest {
    private final PersonalDateSelectionService service = new PersonalDateSelectionService();

    @Test
    public void replacesSelectionsWhileKeepingAuthoritativeCursor() {
        PlayerPersonalDateSlot current = slot("server_day", 0, 6, 8, "YEARLY:2026");
        PlayerPersonalDateSlot candidate = slot("server_day", 0, 7, 9, "YEARLY:2099");

        PersonalDateSelectionResult result = service.replace(
                Collections.singletonList(preset("server_day", 2)),
                Collections.singletonList(current),
                Collections.singletonList(candidate)
        );

        assertTrue(result.isSuccess());
        assertEquals(7, result.getSlots().get(0).getMonth());
        assertEquals("YEARLY:2026", result.getSlots().get(0).getLastClaimedOccurrenceKey());
    }

    @Test
    public void rejectsUnknownDuplicateAndOutOfRangeCandidatesWithoutPartialResult() {
        List<PlayerPersonalDateSlot> candidates = Arrays.asList(
                slot("unknown", 0, 6, 8, ""),
                slot("server_day", 3, 6, 8, ""),
                slot("server_day", 3, 7, 9, "")
        );

        PersonalDateSelectionResult result = service.replace(
                Collections.singletonList(preset("server_day", 2)),
                Collections.emptyList(), candidates);

        assertFalse(result.isSuccess());
        assertTrue(result.getSlots().isEmpty());
    }

    @Test
    public void omittedAndDeletedPresetsKeepInertCursorRecords() {
        PlayerPersonalDateSlot active = slot("server_day", 0, 6, 8, "YEARLY:2026");
        PlayerPersonalDateSlot deleted = slot("deleted_day", 0, 1, 2, "YEARLY:2025");

        PersonalDateSelectionResult result = service.replace(
                Collections.singletonList(preset("server_day", 1)),
                Arrays.asList(active, deleted), Collections.emptyList());

        assertTrue(result.isSuccess());
        assertEquals(2, result.getSlots().size());
        assertEquals(0, result.getSlots().get(0).getMonth());
        assertEquals("YEARLY:2026", result.getSlots().get(0).getLastClaimedOccurrenceKey());
        assertEquals("deleted_day", result.getSlots().get(1).getPresetId());
    }

    private static PersonalDatePreset preset(String id, int slots) {
        return new PersonalDatePreset(
                id, "Server Day", PersonalDateRecurrence.YEARLY,
                Collections.singletonList(CalendarIds.GREGORIAN), slots,
                PersonalDateDeliveryMode.ONLINE, 0, 0, new RewardList()
        );
    }

    private static PlayerPersonalDateSlot slot(String id, int index, int month, int day,
                                               String cursor) {
        return new PlayerPersonalDateSlot(
                id, index, CalendarIds.GREGORIAN, month, day, cursor);
    }
}
