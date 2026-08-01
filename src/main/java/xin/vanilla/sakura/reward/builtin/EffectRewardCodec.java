package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

public final class EffectRewardCodec implements RewardCodec<EffectInstance> {
    @Override
    public EffectInstance decode(JsonObject content) throws RewardDataException {
        try {
            String effectId = content.get("effect").getAsString();
            ResourceLocation location = ResourceLocation.tryParse(effectId);
            Effect effect = location == null ? null : Registry.MOB_EFFECT.getOptional(location).orElse(null);
            if (effect == null || Registry.MOB_EFFECT.getKey(effect) == null) {
                throw new RewardDataException("Unknown effect: " + effectId);
            }
            int duration = content.get("duration").getAsInt();
            int amplifier = content.get("amplifier").getAsInt();
            if (duration <= 0 || amplifier < 0) {
                throw new RewardDataException("Effect duration must be positive and amplifier cannot be negative");
            }
            return new EffectInstance(effect, duration, amplifier);
        } catch (RewardDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RewardDataException("Invalid effect reward", exception);
        }
    }

    @Override
    public JsonObject encode(EffectInstance value) throws RewardDataException {
        ResourceLocation effectId = value == null ? null : Registry.MOB_EFFECT.getKey(value.getEffect());
        if (value == null || effectId == null || value.getDuration() <= 0) {
            throw new RewardDataException("Effect reward must contain a registered effect with positive duration");
        }
        JsonObject result = new JsonObject();
        result.addProperty("effect", effectId.toString());
        result.addProperty("duration", value.getDuration());
        result.addProperty("amplifier", value.getAmplifier());
        return result;
    }
}
