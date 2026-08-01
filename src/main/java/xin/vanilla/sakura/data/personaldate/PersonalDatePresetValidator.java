package xin.vanilla.sakura.data.personaldate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PersonalDatePresetValidator {
    public static final int MAX_DATE_SLOTS = 16;
    public static final int MAX_WINDOW_DAYS = 365;

    private PersonalDatePresetValidator() {
    }

    public static List<String> validate(PersonalDatePreset preset) {
        if (preset == null) {
            return Collections.singletonList("preset");
        }
        List<String> errors = new ArrayList<>();
        if (preset.getId() == null || !preset.getId().matches("[a-z0-9_.-]{1,64}")) {
            errors.add("id");
        }
        if (preset.getDisplayName() == null || preset.getDisplayName().trim().isEmpty()
                || preset.getDisplayName().length() > 64) {
            errors.add("displayName");
        }
        if (preset.getRecurrence() == null) {
            errors.add("recurrence");
        }
        if (preset.getCalendarPolicy() == null) {
            errors.add("calendarPolicy");
        }
        if (preset.getDeliveryMode() == null) {
            errors.add("deliveryMode");
        }
        if (preset.getMaxDateSlots() < 1 || preset.getMaxDateSlots() > MAX_DATE_SLOTS) {
            errors.add("maxDateSlots");
        }
        if (preset.getValidBeforeDays() < 0 || preset.getValidBeforeDays() > MAX_WINDOW_DAYS) {
            errors.add("validBeforeDays");
        }
        if (preset.getValidAfterDays() < 0 || preset.getValidAfterDays() > MAX_WINDOW_DAYS) {
            errors.add("validAfterDays");
        }
        if (preset.getRewards() == null) {
            errors.add("rewards");
        }
        return errors;
    }
}
