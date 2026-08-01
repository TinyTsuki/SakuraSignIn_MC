package xin.vanilla.sakura.client.data;

import com.google.gson.JsonArray;
import lombok.NonNull;
import lombok.Setter;
import xin.vanilla.sakura.reward.RewardList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

@Setter
public class RewardClipboardList extends ArrayList<RewardClipboard> implements Serializable, Cloneable {

    public RewardClipboardList() {
    }

    public RewardClipboardList(Collection<RewardClipboard> collection) {
        super(collection);
    }

    @Override
    public RewardClipboardList clone() {
        try {
            RewardClipboardList cloned = new RewardClipboardList();
            for (RewardClipboard reward : this) {
                if (reward != null) {
                    cloned.add(reward.clone());
                }
            }
            return cloned;
        } catch (Exception e) {
            return new RewardClipboardList();
        }
    }

    @NonNull
    public String getKey() {
        return this.stream().filter(Objects::nonNull).map(RewardClipboard::getKey).findFirst().orElse("");
    }

    public JsonArray toJsonArray() {
        JsonArray jsonArray = new JsonArray();
        for (RewardClipboard reward : this) {
            if (reward != null) {
                jsonArray.add(reward.toJsonObject());
            }
        }

        return jsonArray;
    }

    public RewardList toRewardList() {
        RewardList rewards = new RewardList();
        for (RewardClipboard reward : this) {
            if (reward != null) {
                rewards.add(reward.toReward());
            }
        }
        return rewards;
    }

}
