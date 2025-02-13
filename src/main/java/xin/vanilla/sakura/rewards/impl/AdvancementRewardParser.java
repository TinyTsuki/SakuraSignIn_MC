package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.network.AdvancementData;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.I18nUtils;

public class AdvancementRewardParser implements RewardParser<ResourceLocation> {

    @Override
    public @NonNull ResourceLocation deserialize(JsonObject json) {
        String advancementId;
        try {
            advancementId = json.get("advancement").getAsString();
        } catch (Exception e) {
            LOGGER.error("Failed to parse advancement reward", e);
            advancementId = SakuraSignIn.MODID + ":unknownAdvancement";
        }
        return new ResourceLocation(advancementId);
    }

    @Override
    public JsonObject serialize(ResourceLocation reward) {
        JsonObject json = new JsonObject();
        json.addProperty("advancement", reward.toString());
        return json;
    }

    public static AdvancementData getAdvancementData(String id) {
        return SakuraSignIn.getAdvancementData().stream()
                .filter(data -> data.id().toString().equalsIgnoreCase(id))
                .findFirst().orElse(new AdvancementData(new ResourceLocation(id), null));
    }

    public static String getId(AdvancementData advancementData) {
        return getId(advancementData.id());
    }

    public static String getId(Advancement advancement) {
        return getId(advancement.getId());
    }

    public static String getId(ResourceLocation resourceLocation) {
        return resourceLocation.toString();
    }

    public static AdvancementData getAdvancementData(ResourceLocation resourceLocation) {
        return getAdvancementData(resourceLocation.toString());
    }

    public static @NonNull String getDisplayName(AdvancementData advancementData) {
        return advancementData.displayInfo().getTitle().getString();
    }

    public static @NonNull String getDescription(AdvancementData advancementData) {
        return advancementData.displayInfo().getDescription().getString();
    }

    public static @NonNull String getDisplayName(Advancement advancement) {
        String result = "";
        DisplayInfo display = advancement.getDisplay();
        if (display != null)
            result = display.getTitle().getString();
        return result;
    }

    @Override
    public @NonNull String getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull String getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        ResourceLocation deserialize = deserialize(json);
        String rewardType = I18nUtils.getTranslation(EI18nType.WORD, "reward_type_" + ERewardType.ADVANCEMENT.getCode(), languageCode);
        return String.format("%s: %s", rewardType
                , SakuraSignIn.getAdvancementData().stream()
                        .filter(data -> data.id().equals(deserialize))
                        .findFirst().orElse(new AdvancementData(deserialize, null))
                        .displayInfo().getTitle().getString());
    }

    public @NonNull
    static String getDescription(Advancement advancement) {
        String result = "";
        DisplayInfo display = advancement.getDisplay();
        if (display != null)
            result = advancement.getDisplay().getDescription().getString();
        return result;
    }
}
