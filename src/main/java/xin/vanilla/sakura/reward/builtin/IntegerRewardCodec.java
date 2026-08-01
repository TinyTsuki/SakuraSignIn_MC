package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

final class IntegerRewardCodec implements RewardCodec<Integer> {
    private final String field;

    IntegerRewardCodec(String field) {
        this.field = field;
    }

    @Override
    public Integer decode(JsonObject content) throws RewardDataException {
        try {
            int value = content.get(field).getAsInt();
            if (value <= 0) {
                throw new RewardDataException(field + " must be positive");
            }
            return value;
        } catch (RewardDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RewardDataException("Invalid integer reward field: " + field, exception);
        }
    }

    @Override
    public JsonObject encode(Integer value) throws RewardDataException {
        if (value == null || value <= 0) {
            throw new RewardDataException(field + " must be positive");
        }
        JsonObject result = new JsonObject();
        result.addProperty(field, value);
        return result;
    }
}
