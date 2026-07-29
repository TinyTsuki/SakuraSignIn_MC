package xin.vanilla.sakura.rewards.impl;

import xin.vanilla.sakura.text.SakuraComponent;
import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.banira.common.data.Component;

public class ExpLevelRewardParser implements RewardParser<Integer> {

    @Override
    public @NonNull Integer deserialize(JsonObject json) {
        try {
            return json.get("expLevel").getAsInt();
        } catch (Exception e) {
            LOGGER.error("Failed to parse exp level reward", e);
            return 0;
        }
    }

    @Override
    public JsonObject serialize(Integer reward) {
        JsonObject json = new JsonObject();
        json.addProperty("expLevel", reward);
        return json;
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        int num = deserialize(json);
        return SakuraComponent.get().transLang(languageCode, "word", "reward_type_" + ERewardType.EXP_LEVEL.getCode())
                .append(withNum ? "x" + num : "");
    }
}
