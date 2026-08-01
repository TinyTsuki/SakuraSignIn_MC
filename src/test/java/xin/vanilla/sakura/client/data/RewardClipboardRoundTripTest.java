package xin.vanilla.sakura.client.data;

import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class RewardClipboardRoundTripTest {
    @Test
    public void preservesUnknownTypePayloadAndSourceKey() {
        JsonObject nested = new JsonObject();
        nested.addProperty("currency", "moon_shard");
        nested.addProperty("amount", 7);
        Reward original = new Reward(nested, RewardTypeId.parse("example:currency"),
                new BigDecimal("0.75"));
        original.setDisabled(true);

        RewardClipboard copied = RewardClipboardManager.toClipboard(original, "base");
        RewardClipboardList decoded = RewardClipboardManager.deSerializeRewardList(
                RewardClipboardManager.serialize(copied));
        Reward restored = decoded.get(0).toReward();

        assertEquals("base", decoded.get(0).getKey());
        assertEquals(original.getTypeId(), restored.getTypeId());
        assertEquals(original.getProbability(), restored.getProbability());
        assertEquals(original.getContent(), restored.getContent());
        assertEquals(original.isDisabled(), restored.isDisabled());
    }

    @Test
    public void convertsAClipboardListWithoutGsonRewardBinding() {
        JsonObject payload = new JsonObject();
        payload.addProperty("raw", "kept");
        RewardList rewards = new RewardList();
        rewards.add(new Reward(payload, RewardTypeId.parse("addon:custom"), BigDecimal.ONE));

        RewardClipboardList copied = RewardClipboardManager.toClipboardList(rewards, "7");
        RewardList restored = copied.toRewardList();

        assertEquals(1, restored.size());
        assertEquals("addon:custom", restored.get(0).getTypeId().toString());
        assertEquals(payload, restored.get(0).getContent());
    }
}
