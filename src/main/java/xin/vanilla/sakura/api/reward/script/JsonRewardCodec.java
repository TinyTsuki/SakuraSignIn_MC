package xin.vanilla.sakura.api.reward.script;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

/**
 * Script rewards use JSON values so clients can edit them without executing scripts.
 */
public final class JsonRewardCodec implements RewardCodec<JsonObject> {
    public static JsonObject copy(JsonObject content) {
        // Gson may reuse a JsonObject passed to fromJson; parse text to isolate mutable trees.
        return content == null ? null : new com.google.gson.JsonParser().parse(content.toString()).getAsJsonObject();
    }

    @Override
    public JsonObject decode(JsonObject content) throws RewardDataException {
        if (content == null || content.toString().length() > 16384) {
            throw new RewardDataException("Script reward data must be an object of at most 16384 characters");
        }
        return copy(content);
    }

    @Override
    public JsonObject encode(JsonObject content) throws RewardDataException {
        return decode(content);
    }
}
