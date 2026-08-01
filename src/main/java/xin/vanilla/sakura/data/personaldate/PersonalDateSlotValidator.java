package xin.vanilla.sakura.data.personaldate;

import xin.vanilla.sakura.data.calendar.CalendarRegistry;
import xin.vanilla.sakura.data.calendar.DateCalendar;
import xin.vanilla.sakura.data.calendar.SakuraCalendars;

public final class PersonalDateSlotValidator {
    private PersonalDateSlotValidator() {
    }

    public static boolean isValid(PersonalDatePreset preset, PlayerPersonalDateSlot slot) {
        return isValid(preset, slot, SakuraCalendars.get());
    }

    public static boolean isValid(PersonalDatePreset preset, PlayerPersonalDateSlot slot,
                                  CalendarRegistry calendars) {
        if (preset == null || slot == null || preset.getId() == null
                || preset.getCalendarIds() == null || preset.getRecurrence() == null
                || !preset.getId().equals(slot.getPresetId())
                || slot.getSlotIndex() < 0 || slot.getSlotIndex() >= preset.getMaxDateSlots()
                || slot.getCalendarId() == null
                || !preset.getCalendarIds().contains(slot.getCalendarId())
                || calendars == null || !calendars.find(slot.getCalendarId()).isPresent()) {
            return false;
        }
        if (!validMonth(preset, slot.getMonth()) || slot.getDay() < 1 || slot.getDay() > 512) {
            return false;
        }
        DateCalendar calendar = calendars.require(slot.getCalendarId());
        return calendar.supportsMonthDay(slot.getMonth(), slot.getDay());
    }

    private static boolean validMonth(PersonalDatePreset preset, int month) {
        return preset.getRecurrence() == PersonalDateRecurrence.MONTHLY
                ? month == 0
                : month >= 1 && month <= 64;
    }
}
