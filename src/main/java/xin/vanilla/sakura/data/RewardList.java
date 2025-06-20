package xin.vanilla.sakura.data;

import com.google.gson.JsonArray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class RewardList extends ArrayList<Reward> implements Serializable, Cloneable {
    public RewardList() {
    }

    public RewardList(Collection<Reward> collection) {
        super(collection);
    }

    public RewardList(Reward reward) {
        this.add(reward);
    }

    @Override
    public RewardList clone() {
        try {
            RewardList cloned = new RewardList();
            for (Reward reward : this) {
                if (reward != null) {
                    cloned.add(reward.clone());
                }
            }
            return cloned;
        } catch (Exception e) {
            return new RewardList();
        }
    }

    public JsonArray toJsonArray() {
        JsonArray jsonArray = new JsonArray();
        for (Reward reward : this) {
            if (reward != null) {
                jsonArray.add(reward.toJsonObject());
            }
        }
        return jsonArray;
    }
}
