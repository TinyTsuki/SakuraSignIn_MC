package xin.vanilla.sakura.reward;

import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import xin.vanilla.sakura.api.reward.RewardTypeId;

import java.math.BigDecimal;

/**
 * 奖励持久化与网络共用的显式 JSON 边界，不解析具体类型的 payload。
 */
public final class RewardJsonCodec {
    private static final Gson GSON = new Gson();

    private RewardJsonCodec() {
    }

    public static Reward decode(JsonElement element) {
        try {
            JsonObject json = element.getAsJsonObject();
            RewardTypeId typeId = RewardTypeId.parse(required(json, "type").getAsString());
            BigDecimal probability = required(json, "probability").getAsBigDecimal();
            JsonObject content = copy(required(json, "content").getAsJsonObject());
            Reward reward = new Reward(content, typeId, probability);
            reward.setRewarded(json.has("rewarded") && json.get("rewarded").getAsBoolean());
            reward.setDisabled(json.has("disabled") && json.get("disabled").getAsBoolean());
            return reward;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonParseException("Invalid reward JSON", exception);
        }
    }

    public static JsonObject encode(Reward reward) {
        if (reward == null || reward.getTypeId() == null || reward.getContent() == null
                || reward.getProbability() == null) {
            throw new JsonParseException("Incomplete reward");
        }
        JsonObject json = new JsonObject();
        if (reward.isRewarded()) {
            json.addProperty("rewarded", true);
        }
        if (reward.isDisabled()) {
            json.addProperty("disabled", true);
        }
        json.addProperty("type", reward.getTypeId().toString());
        json.addProperty("probability", reward.getProbability());
        json.add("content", copy(reward.getContent()));
        return json;
    }

    private static JsonObject copy(JsonObject value) {
        return GSON.fromJson(value, JsonObject.class);
    }

    private static JsonElement required(JsonObject json, String name) {
        if (json == null || !json.has(name) || json.get(name).isJsonNull()) {
            throw new JsonParseException("Missing reward property: " + name);
        }
        return json.get(name);
    }
}
