package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.I18nUtils;

public class CommandRewardParser implements RewardParser<String> {

    @Override
    public @NonNull String deserialize(JsonObject json) {
        try {
            return json.get("command").getAsString();
        } catch (Exception e) {
            LOGGER.error("Failed to parse command reward", e);
            return "";
        }
    }

    @Override
    public JsonObject serialize(String reward) {
        JsonObject json = new JsonObject();
        json.addProperty("command", reward);
        return json;
    }

    @Override
    public @NonNull String getDisplayName(JsonObject json) {
        return getDisplayName(json, false);
    }

    @Override
    public @NonNull String getDisplayName(JsonObject json, boolean withNum) {
        return I18nUtils.get(String.format("reward.sakura_sign_in.reward_type_%s", ERewardType.COMMAND.getCode()));
    }
}
