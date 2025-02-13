package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.I18nUtils;

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
    public @NonNull String getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull String getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        return I18nUtils.getTranslation(EI18nType.WORD, "reward_type_" + ERewardType.MESSAGE.getCode(), languageCode);
    }
}
