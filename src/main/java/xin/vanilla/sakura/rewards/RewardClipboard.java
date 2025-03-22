package xin.vanilla.sakura.rewards;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

import static xin.vanilla.sakura.config.RewardOptionDataManager.GSON;

@Setter
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class RewardClipboard extends Reward {
    private String key;

    @Override
    public RewardClipboard clone() {
        try {
            RewardClipboard cloned = (RewardClipboard) super.clone();
            cloned.setContent(GSON.fromJson(GSON.toJson(this.getContent()), JsonObject.class));
            return cloned;
        } catch (Exception e) {
            return new RewardClipboard();
        }
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        if (this.isRewarded()) {
            json.addProperty("rewarded", true);
        }
        if (this.isDisabled()) {
            json.addProperty("disabled", true);
        }
        json.addProperty("type", this.getType().name());
        json.addProperty("probability", this.getProbability());
        json.add("content", this.getContent());
        json.addProperty("key", this.getKey());
        return json;
    }

    @NonNull
    public String getKey() {
        return this.key == null ? "" : this.key;
    }

    public Reward toReward() {
        return GSON.fromJson(this.toJsonObject(), new TypeToken<Reward>() {
        }.getType());
    }
}
