package xin.vanilla.sakura.config.reward;

import org.junit.After;
import org.junit.Test;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.network.data.RewardOptionSyncKind;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.reward.RewardList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardRuleVisibilityTest {
    @After
    public void resetVisibility() {
        RewardConfigManager.updateRedactedRules(null);
    }

    @Test
    public void hiddenRuleIsRepresentedOnlyByRedactedMarker() {
        RewardConfig config = new RewardConfig();
        config.getWeekRewards().put("1", new RewardList());

        RewardOptionSyncPacket packet = RewardConfigManager.toSyncPacket(
                config, rule -> rule != ERewardRule.WEEK_REWARD);

        assertTrue(packet.getRewardOptionData().stream().anyMatch(data ->
                data.getRule() == ERewardRule.WEEK_REWARD
                        && data.getKind() == RewardOptionSyncKind.REDACTED_RULE));
        assertFalse(packet.getRewardOptionData().stream().anyMatch(data ->
                data.getRule() == ERewardRule.WEEK_REWARD
                        && data.getKind() != RewardOptionSyncKind.REDACTED_RULE));
        RewardConfigManager.updateRedactedRules(packet);
        assertTrue(RewardConfigManager.isRuleRedacted(ERewardRule.WEEK_REWARD));
    }

    @Test
    public void personalDateDetailsStayOutOfOrdinaryRewardPacket() {
        RewardOptionSyncPacket packet = RewardConfigManager.toSyncPacket(
                new RewardConfig(), rule -> true);

        assertTrue(packet.getRewardOptionData().stream().anyMatch(data ->
                data.getRule() == ERewardRule.PERSONAL_DATE_REWARD
                        && data.getKind() == RewardOptionSyncKind.EMPTY_GROUP));
        assertFalse(packet.getRewardOptionData().stream().anyMatch(data ->
                data.getRule() == ERewardRule.PERSONAL_DATE_REWARD
                        && data.getKind() == RewardOptionSyncKind.REWARD));
    }
}
