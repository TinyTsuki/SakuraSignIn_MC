package xin.vanilla.sakura.reward;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RewardListJsonCodecTest {
    @Test
    public void decodesCurrentAndLegacyTypesTogether() {
        JsonArray source = new JsonArray();
        source.add(reward("ITEM", "minecraft:apple"));
        source.add(reward("sakura_sign_in:item", "minecraft:bread"));

        RewardList rewards = RewardListJsonCodec.decodeLenient(source, ignored -> {
        });

        assertEquals(2, rewards.size());
        assertEquals(SakuraRewardTypes.ITEM, rewards.get(0).getTypeId());
        assertEquals(SakuraRewardTypes.ITEM, rewards.get(1).getTypeId());
        assertEquals("minecraft:apple", rewards.get(0).getContent().get("item").getAsString());
    }

    @Test
    public void isolatesMalformedEntriesInsteadOfRejectingTheRecord() {
        JsonArray source = new JsonArray();
        source.add(reward("ITEM", "minecraft:apple"));
        source.add(new JsonObject());
        source.add(reward("UNKNOWN_LEGACY_TYPE", "minecraft:bread"));
        source.add(reward("sakura_sign_in:item", "minecraft:carrot"));
        List<String> warnings = new ArrayList<>();

        RewardList rewards = RewardListJsonCodec.decodeLenient(
                source, exception -> warnings.add(exception.getMessage()));

        assertEquals(2, rewards.size());
        assertEquals(2, warnings.size());
        assertEquals("minecraft:carrot",
                rewards.get(1).getContent().get("item").getAsString());
    }

    @Test
    public void encodedListRoundTripsThroughTheDedicatedBoundary() {
        JsonArray source = new JsonArray();
        source.add(reward("sakura_sign_in:item", "minecraft:apple"));
        RewardList decoded = RewardListJsonCodec.decodeLenient(source, ignored -> {
        });

        RewardList restored = RewardListJsonCodec.decodeLenient(
                RewardListJsonCodec.encode(decoded), ignored -> {
                });

        assertEquals(1, restored.size());
        assertEquals(decoded.get(0).getTypeId(), restored.get(0).getTypeId());
        assertEquals(decoded.get(0).getContent(), restored.get(0).getContent());
    }

    private static JsonObject reward(String type, String item) {
        JsonObject content = new JsonObject();
        content.addProperty("item", item);
        content.addProperty("count", 1);
        JsonObject reward = new JsonObject();
        reward.addProperty("type", type);
        reward.addProperty("probability", 1);
        reward.add("content", content);
        return reward;
    }
}
