package xin.vanilla.sakura.config.reward;

import org.junit.Test;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Collections;

import static org.junit.Assert.*;

public class RewardRuleHistoryTest {
    @Test public void incompleteLegacyRewardsAreRejectedForEveryRule() {
        for (ERewardRule rule : ERewardRule.values()) {
            assertThrows("Incomplete reward accepted for " + rule, java.io.IOException.class,
                    () -> RewardRuleHistory.decode("{\"1\":[{}]}", rule));
        }
    }

    private RewardConfig config() {
        RewardConfig config = new RewardConfig();
        config.getPersonalDatePresets().add(new PersonalDatePreset("birthday", "Birthday",
                PersonalDateRecurrence.YEARLY, Collections.singletonList(CalendarIds.GREGORIAN),
                2, PersonalDateDeliveryMode.SIGN_IN, 3, 4, new RewardList()));
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.WEEKLY,
                2, 30, LotteryPreviewMode.NONE, new RewardList());
        pool.setDescription("Original description");
        pool.setVoucherExpression("xpPoints(2)");
        config.getLotteryPools().add(pool);
        return config;
    }

    @Test public void personalDateUndoRestoresMetadata() throws Exception {
        RewardConfig config = config();
        String before = new RewardConfigCodec().encode(config);
        String history = RewardRuleHistory.encode(config, ERewardRule.PERSONAL_DATE_REWARD);
        config.getPersonalDatePresets().get(0).setDisplayName("Changed");
        RewardRuleHistory.decode(history, ERewardRule.PERSONAL_DATE_REWARD).restore(config);
        assertEquals(before, new RewardConfigCodec().encode(config));
    }

    @Test public void deletedPersonalDateCanBeRestored() throws Exception {
        RewardConfig config = config();
        String before = new RewardConfigCodec().encode(config);
        String history = RewardRuleHistory.encode(config, ERewardRule.PERSONAL_DATE_REWARD);
        config.getPersonalDatePresets().clear();
        RewardRuleHistory.decode(history, ERewardRule.PERSONAL_DATE_REWARD).restore(config);
        assertEquals(before, new RewardConfigCodec().encode(config));
    }

    @Test public void lotteryUndoRestoresMetadata() throws Exception {
        RewardConfig config = config();
        String before = new RewardConfigCodec().encode(config);
        String history = RewardRuleHistory.encode(config, ERewardRule.LOTTERY_REWARD);
        config.getLotteryPools().get(0).setDescription("Changed");
        config.getLotteryPools().get(0).setVoucherExpression("xpPoints(20)");
        RewardRuleHistory.decode(history, ERewardRule.LOTTERY_REWARD).restore(config);
        assertEquals(before, new RewardConfigCodec().encode(config));
    }

    @Test public void deletedLotteryCanBeRestored() throws Exception {
        RewardConfig config = config();
        String before = new RewardConfigCodec().encode(config);
        String history = RewardRuleHistory.encode(config, ERewardRule.LOTTERY_REWARD);
        config.getLotteryPools().clear();
        RewardRuleHistory.decode(history, ERewardRule.LOTTERY_REWARD).restore(config);
        assertEquals(before, new RewardConfigCodec().encode(config));
    }

    @Test public void emptyRuleCanBeRestoredWithoutChangingOtherRules() throws Exception {
        RewardConfig config = config();
        String history = RewardRuleHistory.encode(config, ERewardRule.MONTH_REWARD);
        config.getMonthRewards().put("1", new RewardList());
        RewardRuleHistory.decode(history, ERewardRule.MONTH_REWARD).restore(config);
        assertTrue(config.getMonthRewards().isEmpty());
        assertEquals("Birthday", config.getPersonalDatePresets().get(0).getDisplayName());
        assertEquals("Original description", config.getLotteryPools().get(0).getDescription());
    }

    @Test public void ordinaryRuleRetainsUnrelatedSubsequentChanges() throws Exception {
        RewardConfig config = config();
        config.getMonthRewards().put("1", new RewardList());
        String history = RewardRuleHistory.encode(config, ERewardRule.MONTH_REWARD);
        config.getMonthRewards().clear();
        config.getPersonalDatePresets().get(0).setDisplayName("Later change");
        RewardRuleHistory.decode(history, ERewardRule.MONTH_REWARD).restore(config);
        assertTrue(config.getMonthRewards().containsKey("1"));
        assertEquals("Later change", config.getPersonalDatePresets().get(0).getDisplayName());
    }

    @Test public void mismatchedRuleAndMalformedSnapshotDoNotChangeModel() throws Exception {
        RewardConfig config = config();
        String before = new RewardConfigCodec().encode(config);
        String history = RewardRuleHistory.encode(config, ERewardRule.LOTTERY_REWARD);
        assertThrows(java.io.IOException.class, () -> RewardRuleHistory.decode(history, ERewardRule.PERSONAL_DATE_REWARD));
        assertThrows(java.io.IOException.class, () -> RewardRuleHistory.decode("{broken", ERewardRule.LOTTERY_REWARD));
        assertEquals(before, new RewardConfigCodec().encode(config));
    }

    @Test public void legacyMapHistoryStillRestoresOrdinaryRule() throws Exception {
        RewardConfig config = config();
        config.getMonthRewards().put("2", new RewardList());
        RewardRuleHistory.decode("{\"1\":[]}", ERewardRule.MONTH_REWARD).restore(config);
        assertEquals(Collections.singleton("1"), config.getMonthRewards().keySet());
        assertEquals("Birthday", config.getPersonalDatePresets().get(0).getDisplayName());
    }
}
