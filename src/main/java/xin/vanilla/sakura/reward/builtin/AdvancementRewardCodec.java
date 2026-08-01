package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

public final class AdvancementRewardCodec implements RewardCodec<ResourceLocation> {
    @Override
    public ResourceLocation decode(JsonObject content) throws RewardDataException {
        try {
            return new ResourceLocation(content.get("advancement").getAsString());
        } catch (Exception exception) {
            throw new RewardDataException("Invalid advancement reward", exception);
        }
    }

    @Override
    public JsonObject encode(ResourceLocation value) throws RewardDataException {
        if (value == null) {
            throw new RewardDataException("Advancement id cannot be null");
        }
        JsonObject result = new JsonObject();
        result.addProperty("advancement", value.toString());
        return result;
    }
}
