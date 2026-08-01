package xin.vanilla.sakura.reward.impl;

import xin.vanilla.sakura.SakuraComponent;
import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.reward.RewardParser;
import xin.vanilla.banira.common.data.Component;

public class ExpPointRewardParser implements RewardParser<Integer> {

    @Override
    public @NonNull Integer deserialize(JsonObject json) {
        try {
            return json.get("expPoint").getAsInt();
        } catch (Exception e) {
            LOGGER.error("Failed to parse exp point reward", e);
            return 0;
        }
    }

    @Override
    public JsonObject serialize(Integer reward) {
        JsonObject json = new JsonObject();
        json.addProperty("expPoint", reward);
        return json;
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        int num = deserialize(json);
        return SakuraComponent.get().transLang(languageCode, "word", "reward_type_" + ERewardType.EXP_POINT.getCode())
                .append(withNum ? "x" + num : "");
    }
}
