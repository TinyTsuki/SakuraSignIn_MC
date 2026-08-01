package xin.vanilla.sakura.data.personaldate;

import xin.vanilla.sakura.reward.RewardList;

import java.util.ArrayList;
import java.util.List;

public final class PersonalDatePresets {
    private PersonalDatePresets() {
    }

    public static List<PersonalDatePreset> copy(List<PersonalDatePreset> source) {
        List<PersonalDatePreset> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        source.stream().filter(preset -> preset != null).forEach(preset -> {
            RewardList rewards = new RewardList();
            preset.getRewards().forEach(reward -> rewards.add(reward.clone()));
            result.add(new PersonalDatePreset(
                    preset.getId(), preset.getDisplayName(), preset.getRecurrence(),
                    preset.getCalendarPolicy(), preset.getMaxDateSlots(),
                    preset.getDeliveryMode(), preset.getValidBeforeDays(),
                    preset.getValidAfterDays(), rewards));
        });
        return result;
    }
}
