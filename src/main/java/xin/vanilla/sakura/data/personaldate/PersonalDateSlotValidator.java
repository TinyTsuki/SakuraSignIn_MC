package xin.vanilla.sakura.data.personaldate;

import java.time.DateTimeException;
import java.time.LocalDate;

public final class PersonalDateSlotValidator {
    private PersonalDateSlotValidator() {
    }

    public static boolean isValid(PersonalDatePreset preset, PlayerPersonalDateSlot slot) {
        if (preset == null || slot == null || preset.getId() == null
                || preset.getCalendarPolicy() == null || preset.getRecurrence() == null
                || !preset.getId().equals(slot.getPresetId())
                || slot.getSlotIndex() < 0 || slot.getSlotIndex() >= preset.getMaxDateSlots()
                || slot.getCalendar() == null || !preset.getCalendarPolicy().accepts(slot.getCalendar())) {
            return false;
        }
        if (slot.getCalendar() == PersonalDateCalendar.LUNAR) {
            return validMonth(preset, slot.getMonth()) && slot.getDay() >= 1 && slot.getDay() <= 30;
        }
        if (!validMonth(preset, slot.getMonth()) || slot.getDay() < 1 || slot.getDay() > 31) {
            return false;
        }
        if (preset.getRecurrence() == PersonalDateRecurrence.MONTHLY) {
            return true;
        }
        try {
            LocalDate.of(2024, slot.getMonth(), slot.getDay());
            return true;
        } catch (DateTimeException ignored) {
            return false;
        }
    }

    private static boolean validMonth(PersonalDatePreset preset, int month) {
        return preset.getRecurrence() == PersonalDateRecurrence.MONTHLY
                ? month >= 0 && month <= 12
                : month >= 1 && month <= 12;
    }
}
