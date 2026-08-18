package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

public final class EffectRewardCodec implements RewardCodec<MobEffectInstance> {
    @Override
    public MobEffectInstance decode(JsonObject content) throws RewardDataException {
        try {
            String effectId = content.get("effect").getAsString();
            ResourceLocation location = ResourceLocation.tryParse(effectId);
            MobEffect effect = location == null ? null : Registry.MOB_EFFECT.getOptional(location).orElse(null);
            if (effect == null || Registry.MOB_EFFECT.getKey(effect) == null) {
                throw new RewardDataException("Unknown effect: " + effectId);
            }
            int duration = content.get("duration").getAsInt();
            int amplifier = content.get("amplifier").getAsInt();
            if (duration <= 0 || amplifier < 0) {
                throw new RewardDataException("MobEffect duration must be positive and amplifier cannot be negative");
            }
            return new MobEffectInstance(effect, duration, amplifier);
        } catch (RewardDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RewardDataException("Invalid effect reward", exception);
        }
    }

    @Override
    public JsonObject encode(MobEffectInstance value) throws RewardDataException {
        ResourceLocation effectId = value == null ? null : Registry.MOB_EFFECT.getKey(value.getEffect());
        if (value == null || effectId == null || value.getDuration() <= 0) {
            throw new RewardDataException("MobEffect reward must contain a registered effect with positive duration");
        }
        JsonObject result = new JsonObject();
        result.addProperty("effect", effectId.toString());
        result.addProperty("duration", value.getDuration());
        result.addProperty("amplifier", value.getAmplifier());
        return result;
    }
}
