package xin.vanilla.sakura.rewards.impl;

import xin.vanilla.sakura.text.SakuraComponent;
import com.google.gson.JsonObject;
import lombok.NonNull;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.network.data.AdvancementData;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.banira.common.data.Component;

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
        return ResourceLocation.parse(advancementId);
    }

    @Override
    public JsonObject serialize(ResourceLocation reward) {
        JsonObject json = new JsonObject();
        json.addProperty("advancement", reward.toString());
        return json;
    }

    public static AdvancementData getAdvancementData(String id) {
        return SakuraClientState.getAdvancementData().stream()
                .filter(data -> data.getId().toString().equalsIgnoreCase(id))
                .findFirst().orElse(new AdvancementData(new ResourceLocation(id), null));
    }

    public static String getId(AdvancementData advancementData) {
        return getId(advancementData.getId());
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
        return advancementData.getDisplayInfo().getTitle().getString();
    }

    public static @NonNull String getDescription(AdvancementData advancementData) {
        return advancementData.getDisplayInfo().getDescription().getString();
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
        return SakuraComponent.get().transLang(languageCode, "word", "reward_type_" + ERewardType.ADVANCEMENT.getCode())
                .append(": ")
                .append(SakuraComponent.get().object(SakuraClientState.getAdvancementData().stream()
                        .filter(data -> data.getId().equals(deserialize))
                        .findFirst().orElse(new AdvancementData(deserialize, null))
                        .getDisplayInfo().getTitle()));
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
