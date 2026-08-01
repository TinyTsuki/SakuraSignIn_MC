package xin.vanilla.sakura.reward;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import xin.vanilla.sakura.api.reward.RewardTypeId;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;

/** 签到详情中的奖励快照允许逐项容错，不能拖垮整份玩家数据。 */
public final class RewardListJsonCodec {
    private static final Gson GSON = new Gson();

    private RewardListJsonCodec() {
    }

    public static JsonArray encode(RewardList rewards) {
        JsonArray result = new JsonArray();
        if (rewards == null) {
            return result;
        }
        for (Reward reward : rewards) {
            if (reward != null) {
                result.add(RewardJsonCodec.encode(reward));
            }
        }
        return result;
    }

    public static RewardList decodeLenient(String json,
                                            Consumer<RuntimeException> warningSink) {
        Objects.requireNonNull(warningSink, "warningSink");
        try {
            JsonElement parsed = new JsonParser().parse(json == null ? "[]" : json);
            if (!parsed.isJsonArray()) {
                throw new JsonParseException("Reward list must be a JSON array");
            }
            return decodeLenient(parsed.getAsJsonArray(), warningSink);
        } catch (RuntimeException exception) {
            warningSink.accept(exception);
            return new RewardList();
        }
    }

    public static RewardList decodeLenient(JsonArray source,
                                            Consumer<RuntimeException> warningSink) {
        Objects.requireNonNull(warningSink, "warningSink");
        RewardList rewards = new RewardList();
        if (source == null) {
            return rewards;
        }
        for (JsonElement element : source) {
            try {
                rewards.add(decodeOne(element));
            } catch (RuntimeException exception) {
                warningSink.accept(exception);
            }
        }
        return rewards;
    }

    private static Reward decodeOne(JsonElement element) {
        JsonObject json = element.getAsJsonObject();
        if (!json.has("type") || json.get("type").isJsonNull()) {
            throw new JsonParseException("Missing reward property: type");
        }
        String rawType = json.get("type").getAsString();
        if (rawType.indexOf(':') > 0) {
            return RewardJsonCodec.decode(json);
        }
        RewardTypeId typeId = LegacyRewardTypeIds.resolve(rawType);
        if (!json.has("content") || !json.get("content").isJsonObject()) {
            throw new JsonParseException("Missing reward property: content");
        }
        BigDecimal probability = json.has("probability")
                ? json.get("probability").getAsBigDecimal() : BigDecimal.ONE;
        Reward reward = new Reward(GSON.fromJson(json.getAsJsonObject("content"), JsonObject.class),
                typeId, probability);
        reward.setRewarded(json.has("rewarded") && json.get("rewarded").getAsBoolean());
        reward.setDisabled(json.has("disabled") && json.get("disabled").getAsBoolean());
        return reward;
    }
}
