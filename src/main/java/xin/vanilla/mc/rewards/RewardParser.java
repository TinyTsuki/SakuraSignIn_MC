package xin.vanilla.mc.rewards;


import com.google.gson.JsonObject;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface RewardParser<T> {
    Logger LOGGER = LogManager.getLogger();

    /**
     * 反序列化奖励对象
     */
    @NonNull
    T deserialize(JsonObject json);

    /**
     * 序列化奖励对象
     */
    JsonObject serialize(T reward);

    @NonNull
    String getDisplayName(JsonObject json);

    @NonNull
    String getDisplayName(JsonObject json, boolean withNum);
}
