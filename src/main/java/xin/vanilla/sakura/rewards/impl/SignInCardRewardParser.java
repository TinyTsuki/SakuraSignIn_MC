package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;

public class SignInCardRewardParser implements RewardParser<Integer> {

    @Override
    public @NonNull Integer deserialize(JsonObject json) {
        try {
            return json.get("signInCard").getAsInt();
        } catch (Exception e) {
            LOGGER.error("Failed to parse signInCard reward", e);
            return 0;
        }
    }

    @Override
    public JsonObject serialize(Integer reward) {
        JsonObject json = new JsonObject();
        json.addProperty("signInCard", reward);
        return json;
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        int num = deserialize(json);
        return Component.translatable(languageCode, EnumI18nType.WORD, "reward_type_" + EnumRewardType.SIGN_IN_CARD.getCode())
                .append(withNum ? "x" + num : "");
    }
}
