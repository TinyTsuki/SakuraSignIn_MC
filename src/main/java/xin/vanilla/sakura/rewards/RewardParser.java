package xin.vanilla.sakura.rewards;


import com.google.gson.JsonObject;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.util.Component;

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
    Component getDisplayName(String languageCode, JsonObject json);

    @NonNull
    Component getDisplayName(String languageCode, JsonObject json, boolean withNum);
}
