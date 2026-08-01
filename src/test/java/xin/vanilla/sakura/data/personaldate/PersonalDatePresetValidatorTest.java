package xin.vanilla.sakura.data.personaldate;

import org.junit.Test;
import xin.vanilla.sakura.reward.RewardList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonalDatePresetValidatorTest {
    @Test
    public void acceptsBoundedServerPreset() {
        PersonalDatePreset preset = preset("server_day", 2, 3, 7);

        assertTrue(PersonalDatePresetValidator.validate(preset).isEmpty());
    }

    @Test
    public void rejectsInvalidIdSlotsAndWindow() {
        PersonalDatePreset preset = preset("Invalid Id", 0, -1, 366);

        assertFalse(PersonalDatePresetValidator.validate(preset).isEmpty());
    }

    private static PersonalDatePreset preset(String id, int slots, int before, int after) {
        return new PersonalDatePreset(
                id,
                "Server Day",
                PersonalDateRecurrence.YEARLY,
                PersonalDateCalendarPolicy.PLAYER_CHOICE,
                slots,
                PersonalDateDeliveryMode.ONLINE,
                before,
                after,
                new RewardList()
        );
    }
}
