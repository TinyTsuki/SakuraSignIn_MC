package xin.vanilla.sakura.network;

import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.RewardRegistryTestSupport;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardAddPermissionChecker;
import xin.vanilla.sakura.reward.builtin.BuiltInRewardTypes;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RewardAddPermissionEnforcementTest {

    @Before
    public void registerBuiltIns() {
        RewardRegistryTestSupport.reset();
        BuiltInRewardTypes.register();
    }

    @After
    public void resetRegistry() {
        RewardRegistryTestSupport.reset();
    }

    @Test
    public void requiresPermissionOnlyForPositivePerTypeCountDeltas() {
        RewardConfig oneItem = config(reward(SakuraRewardTypes.ITEM, "first"));
        RewardConfig twoItems = config(reward(SakuraRewardTypes.ITEM, "first"),
                reward(SakuraRewardTypes.ITEM, "second"));
        RewardConfig differentItem = config(reward(SakuraRewardTypes.ITEM, "changed"));
        RewardConfig oneEffect = config(reward(SakuraRewardTypes.EFFECT, "effect"));
        RewardConfig empty = config();

        assertEquals(Collections.singletonList(SakuraRewardTypes.ITEM),
                RewardAddPermissionChecker.requiredAddedTypes(oneItem, twoItems));
        assertTrue(RewardAddPermissionChecker.requiredAddedTypes(oneItem, differentItem).isEmpty());
        assertEquals(Collections.singletonList(SakuraRewardTypes.EFFECT),
                RewardAddPermissionChecker.requiredAddedTypes(oneItem, oneEffect));
        assertTrue(RewardAddPermissionChecker.requiredAddedTypes(twoItems, oneItem).isEmpty());
        assertTrue(RewardAddPermissionChecker.requiredAddedTypes(oneItem, empty).isEmpty());
    }

    @Test
    public void movingRewardsBetweenGroupsDoesNotRequireAddPermission() {
        RewardConfig authoritative = config(reward(SakuraRewardTypes.ITEM, "same"));
        Reward moved = authoritative.getBaseRewards().remove(0);
        authoritative.getContinuousRewards().put("1",
                new xin.vanilla.sakura.reward.RewardList(
                        Collections.singletonList(moved)));
        RewardConfig candidate = config(reward(SakuraRewardTypes.ITEM, "same"));

        assertTrue(RewardAddPermissionChecker.requiredAddedTypes(authoritative, candidate).isEmpty());
    }

    @Test
    public void addingUnavailableTypeIsReportedForServerRejection() {
        RewardTypeId unknown = RewardTypeId.parse("example_currency:coin");

        assertEquals(Collections.singletonList(unknown),
                RewardAddPermissionChecker.requiredAddedTypes(config(), config(reward(unknown, "coin"))));
    }

    private static RewardConfig config(Reward... rewards) {
        RewardConfig result = new RewardConfig();
        Collections.addAll(result.getBaseRewards(), rewards);
        return result;
    }

    private static Reward reward(RewardTypeId type, String marker) {
        JsonObject content = new JsonObject();
        content.addProperty("marker", marker);
        return new Reward(content, type);
    }
}
