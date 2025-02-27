package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;

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
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        return Component.translatable(languageCode, EI18nType.WORD, "reward_type_" + ERewardType.COMMAND.getCode());
    }
}
