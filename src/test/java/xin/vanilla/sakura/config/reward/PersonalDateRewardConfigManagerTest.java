package xin.vanilla.sakura.config.reward;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PersonalDateRewardConfigManagerTest {
    private RewardConfig previous;

    @Before
    public void setUp() {
        previous = RewardConfigManager.getRewardConfig();
        RewardConfig config = new RewardConfig();
        config.getPersonalDatePresets().add(new PersonalDatePreset(
                "annual", "Annual", PersonalDateRecurrence.YEARLY,
                Collections.singletonList(CalendarIds.GREGORIAN), 1,
                PersonalDateDeliveryMode.SIGN_IN, 0, 0, new RewardList()));
        RewardConfigManager.setRewardConfig(config);
    }

    @After
    public void tearDown() {
        RewardConfigManager.setRewardConfig(previous);
    }

    @Test
    public void appendClearAndDeleteKeepTheirSeparateMeanings() {
        RewardList additions = new RewardList();
        additions.add(new Reward());

        RewardConfigManager.addKeyName(ERewardRule.PERSONAL_DATE_REWARD,
                "annual", additions);
        assertEquals(1, RewardConfigManager.getKeyName(
                ERewardRule.PERSONAL_DATE_REWARD, "annual").size());

        RewardConfigManager.clearKey(ERewardRule.PERSONAL_DATE_REWARD, "annual");
        assertTrue(RewardConfigManager.getRewardConfig()
                .getPersonalDatePresets().stream().anyMatch(preset ->
                        "annual".equals(preset.getId())));
        assertTrue(RewardConfigManager.getKeyName(
                ERewardRule.PERSONAL_DATE_REWARD, "annual").isEmpty());

        RewardConfigManager.deleteKey(ERewardRule.PERSONAL_DATE_REWARD, "annual");
        assertTrue(RewardConfigManager.getRewardConfig()
                .getPersonalDatePresets().isEmpty());
    }
}
