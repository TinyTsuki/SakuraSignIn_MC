package xin.vanilla.sakura.client.data;

import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardJsonCodec;

import static xin.vanilla.sakura.config.reward.RewardConfigManager.GSON;

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
        JsonObject json = RewardJsonCodec.encode(this);
        json.addProperty("key", this.getKey());
        return json;
    }

    public static RewardClipboard fromJson(JsonObject json) {
        Reward reward = RewardJsonCodec.decode(json);
        RewardClipboard clipboard = fromReward(reward, json.has("key")
                ? json.get("key").getAsString() : "");
        return clipboard;
    }

    public static RewardClipboard fromReward(Reward reward, String key) {
        RewardClipboard clipboard = new RewardClipboard();
        clipboard.setRewarded(reward.isRewarded());
        clipboard.setDisabled(reward.isDisabled());
        clipboard.setTypeId(reward.getTypeId());
        clipboard.setProbability(reward.getProbability());
        clipboard.setContent(GSON.fromJson(reward.getContent(), JsonObject.class));
        clipboard.setKey(key);
        return clipboard;
    }

    @NonNull
    public String getKey() {
        return this.key == null ? "" : this.key;
    }

    public Reward toReward() {
        return RewardJsonCodec.decode(this.toJsonObject());
    }
}
