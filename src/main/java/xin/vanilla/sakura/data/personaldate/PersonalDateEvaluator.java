package xin.vanilla.sakura.data.personaldate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 只计算当前有效的目标日期；奖励发放与游标持久化由服务端服务完成。
 */
public final class PersonalDateEvaluator {
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private final LunarCalendar lunarCalendar;

    public PersonalDateEvaluator(LunarCalendar lunarCalendar) {
        this.lunarCalendar = lunarCalendar;
    }

    public List<PersonalDateOccurrence> findActiveOccurrences(PersonalDatePreset preset,
                                                              PlayerPersonalDateSlot slot,
                                                              LocalDate currentDate) {
        if (!PersonalDatePresetValidator.validate(preset).isEmpty()
                || !PersonalDateSlotValidator.isValid(preset, slot)
                || currentDate == null) {
            return Collections.emptyList();
        }
        LocalDate firstTarget = currentDate.minusDays(preset.getValidAfterDays());
        LocalDate lastTarget = currentDate.plusDays(preset.getValidBeforeDays());
        List<PersonalDateOccurrence> occurrences = new ArrayList<>();
        for (LocalDate target = firstTarget; !target.isAfter(lastTarget); target = target.plusDays(1)) {
            if (matches(preset, slot, target)) {
                String key = occurrenceKey(preset.getRecurrence(), target);
                if (!alreadyClaimed(key, slot.getLastClaimedOccurrenceKey())) {
                    occurrences.add(new PersonalDateOccurrence(
                            preset.getId(), slot.getSlotIndex(), target, key));
                }
            }
        }
        occurrences.sort(Comparator.comparing(PersonalDateOccurrence::getTargetDate));
        return occurrences;
    }

    private boolean matches(PersonalDatePreset preset, PlayerPersonalDateSlot slot, LocalDate target) {
        if (slot.getCalendar() == PersonalDateCalendar.SOLAR) {
            return target.getDayOfMonth() == slot.getDay()
                    && (preset.getRecurrence() == PersonalDateRecurrence.MONTHLY
                    || target.getMonthValue() == slot.getMonth());
        }
        LunarDate lunar;
        try {
            lunar = lunarCalendar.toLunar(target);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        // 闰月不形成第二个同周期目标日期。
        return !lunar.isLeapMonth() && lunar.getDay() == slot.getDay()
                && (preset.getRecurrence() == PersonalDateRecurrence.MONTHLY
                || lunar.getMonth() == slot.getMonth());
    }

    private static String occurrenceKey(PersonalDateRecurrence recurrence, LocalDate target) {
        return recurrence == PersonalDateRecurrence.YEARLY
                ? "YEARLY:" + target.getYear()
                : "MONTHLY:" + MONTH_KEY.format(target);
    }

    private static boolean alreadyClaimed(String candidate, String lastClaimed) {
        if (lastClaimed == null || lastClaimed.isEmpty()) {
            return false;
        }
        int separator = candidate.indexOf(':');
        String prefix = separator < 0 ? candidate : candidate.substring(0, separator + 1);
        return lastClaimed.startsWith(prefix) && candidate.compareTo(lastClaimed) <= 0;
    }
}
