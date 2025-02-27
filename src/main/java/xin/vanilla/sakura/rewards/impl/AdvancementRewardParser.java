package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.network.AdvancementData;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;

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

    public static String getId(AdvancementHolder advancement) {
        return getId(advancement.id());
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

    public static @NonNull String getDisplayName(AdvancementHolder advancement) {
        String result = "";
        DisplayInfo display = advancement.value().display().orElse(null);
        if (display != null)
            result = display.getTitle().getString();
        return result;
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        ResourceLocation deserialize = deserialize(json);
        return Component.translatable(languageCode, EI18nType.WORD, "reward_type_" + ERewardType.ADVANCEMENT.getCode())
                .append(": ")
                .append(Component.original(SakuraSignIn.getAdvancementData().stream()
                        .filter(data -> data.id().equals(deserialize))
                        .findFirst().orElse(new AdvancementData(deserialize, null))
                        .displayInfo().getTitle()));
    }

    public @NonNull
    static String getDescription(AdvancementHolder advancement) {
        String result = "";
        DisplayInfo display = advancement.value().display().orElse(null);
        if (display != null)
            result = display.getDescription().getString();
        return result;
    }
}
