package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;

public class MessageRewardParser implements RewardParser<Component> {

    @Override
    public @NonNull Component deserialize(JsonObject json) {
        return Component.deserialize(json);
    }

    @Override
    public JsonObject serialize(Component reward) {
        return Component.serialize(reward);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        return Component.translatable(languageCode, EnumI18nType.WORD, "reward_type_" + EnumRewardType.MESSAGE.getCode());
    }
}
