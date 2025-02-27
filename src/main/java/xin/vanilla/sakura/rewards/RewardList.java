package xin.vanilla.sakura.rewards;

import com.google.gson.JsonArray;
import xin.vanilla.sakura.util.CollectionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RewardList extends ArrayList<Reward> implements Serializable, Cloneable {
    public RewardList() {
    }

    public RewardList(Collection<Reward> collection) {
        super(collection);
    }

    @Override
    public RewardList clone() {
        // 修复bug的方式就是让bug闭嘴
        try {
            RewardList cloned = (RewardList) super.clone();
            List<Reward> clonedRewards = new ArrayList<>();
            if (!CollectionUtils.isNullOrEmpty(this)) {
                for (Reward reward : this) {
                    if (reward != null) {
                        clonedRewards.add(reward.clone());
                    }
                }
            }
            cloned.clear();
            cloned.addAll(clonedRewards);
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
