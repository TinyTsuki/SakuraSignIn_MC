package xin.vanilla.sakura.api.reward;

import com.google.gson.JsonObject;

public interface RewardCodec<T> {

    T decode(JsonObject content) throws RewardDataException;

    JsonObject encode(T value) throws RewardDataException;
}
