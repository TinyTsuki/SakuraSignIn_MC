package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;

public class EconomyParser implements RewardParser<Double> {

	@Override
	public @NonNull Double deserialize(JsonObject json) {
		try {
			return json.get("economy").getAsDouble();
		} catch (Exception e) {
			LOGGER.error("Failed to parse signInCard reward", e);
			return 0d;
		}
	}

	@Override
	public JsonObject serialize(Double reward) {
		JsonObject json = new JsonObject();
		json.addProperty("economy", reward);
		return json;
	}

	@Override
	public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
		return getDisplayName(languageCode, json, false);
	}

	@Override
	public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
		double num = deserialize(json);
		return Component.translatable(languageCode, EnumI18nType.WORD, "reward_type_" + EnumRewardType.ECONOMY.getCode())
				.append(withNum ? "x" + num : "");
	}
}
