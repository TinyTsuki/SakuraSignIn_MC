package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.NonNull;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.registries.ForgeRegistries;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.util.Component;

public class EffectRewardParser implements RewardParser<MobEffectInstance> {

    @Override
    public @NonNull MobEffectInstance deserialize(JsonObject json) {
        MobEffectInstance MobEffectInstance;
        try {
            String effectId = json.get("effect").getAsString();
            int duration = json.get("duration").getAsInt();
            int amplifier = json.get("amplifier").getAsInt();

            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
            if (effect == null) {
                throw new JsonParseException("Unknown potion effect ID: " + effectId);
            }
            MobEffectInstance = new MobEffectInstance(effect, duration, amplifier);
        } catch (Exception e) {
            LOGGER.error("Failed to parse effect reward", e);
            MobEffectInstance = new MobEffectInstance(MobEffects.LUCK, 0, 0);
        }
        return MobEffectInstance;
    }

    @Override
    public JsonObject serialize(MobEffectInstance reward) {
        JsonObject json = new JsonObject();
        json.addProperty("effect", ForgeRegistries.MOB_EFFECTS.getKey(reward.getEffect()).toString());
        json.addProperty("duration", reward.getDuration());
        json.addProperty("amplifier", reward.getAmplifier());
        return json;
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        return Component.translatable(languageCode, EI18nType.WORD, "reward_type_" + ERewardType.EFFECT.getCode())
                .append(": ")
                .append(Component.original(this.deserialize(json).getEffect().getDisplayName()));
    }

    public static @NonNull String getDisplayName(MobEffectInstance instance) {
        return getDisplayName(instance.getEffect());
    }

    public static @NonNull String getDisplayName(MobEffect effect) {
        return effect.getDisplayName().getString().replaceAll("\\[(.*)]", "$1");
    }

    public static String getId(MobEffectInstance instance) {
        return getId(instance.getEffect()) + " " + instance.getDuration() + " " + instance.getAmplifier();
    }

    public static String getId(MobEffect effect) {
        ResourceLocation resource = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (resource == null) return "minecraft:luck";
        else return resource.toString();
    }

    public static MobEffect getEffect(String id) {
        String resourceId = id;
        if (id.contains(" ") && id.split(" ").length == 3) resourceId = resourceId.substring(0, id.indexOf(" "));
        return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(resourceId));
    }

    public static MobEffectInstance getMobEffectInstance(String id, int duration, int amplifier) {
        id = id.split(" ")[0] + " " + duration + " " + amplifier;
        return getMobEffectInstance(id);
    }

    public static MobEffectInstance getMobEffectInstance(String id) {
        MobEffectInstance result = new MobEffectInstance(MobEffects.LUCK);
        try {
            result = getMobEffectInstance(id, false);
        } catch (CommandSyntaxException ignored) {
        }
        return result;
    }

    public static MobEffectInstance getMobEffectInstance(String id, boolean throwException) throws CommandSyntaxException {
        MobEffect effect = getEffect(id);
        if (effect == null) {
            throw new RuntimeException("Unknown effect ID: " + id);
        }
        int amplifier = 0;
        int duration = 0;
        if (id.contains(" ") && id.split(" ").length == 3) {
            try {
                String[] split = id.split(" ");
                amplifier = Integer.parseInt(split[1]);
                duration = Integer.parseInt(split[2]);
            } catch (Exception e) {
                if (throwException) throw e;
                LOGGER.error("Failed to parse Effect data", e);
            }
        }
        return new MobEffectInstance(effect, duration, amplifier);
    }
}
