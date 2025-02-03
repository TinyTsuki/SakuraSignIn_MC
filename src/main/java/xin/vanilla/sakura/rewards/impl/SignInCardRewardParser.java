package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.I18nUtils;

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
    public @NonNull String getDisplayName(JsonObject json) {
        return getDisplayName(json, false);
    }

    @Override
    public @NonNull String getDisplayName(JsonObject json, boolean withNum) {
        int num = deserialize(json);
        return I18nUtils.get(String.format("reward.sakura_sign_in.reward_type_%s", ERewardType.SIGN_IN_CARD.getCode())) + (withNum ? "x" + num : "");
    }
}
