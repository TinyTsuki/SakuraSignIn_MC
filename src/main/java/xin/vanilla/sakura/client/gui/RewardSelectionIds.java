package xin.vanilla.sakura.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 将混合选择转换为稳定、有序的组与奖励 ID。
 */
public final class RewardSelectionIds {
    private RewardSelectionIds() {
    }

    public static List<String> groupKeys(Collection<String> selectedIds, String groupPrefix) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (selectedIds != null && groupPrefix != null) {
            for (String id : selectedIds) {
                if (id != null && id.startsWith(groupPrefix)) {
                    keys.add(id.substring(groupPrefix.length()));
                }
            }
        }
        return new ArrayList<>(keys);
    }

    /** 折叠状态不影响完整规则中的奖励 ID。 */
    public static List<String> rewardIds(Map<String, ? extends List<?>> groups) {
        List<String> ids = new ArrayList<>();
        if (groups != null) {
            groups.forEach((key, rewards) -> ids.addAll(rewardIdsForGroup(groups, key)));
        }
        return ids;
    }

    public static List<String> rewardIdsForGroup(Map<String, ? extends List<?>> groups,
                                                  String groupKey) {
        List<String> ids = new ArrayList<>();
        List<?> rewards = groups == null ? null : groups.get(groupKey);
        if (groupKey != null && rewards != null) {
            for (int i = 0; i < rewards.size(); i++) {
                ids.add(groupKey + "," + i);
            }
        }
        return ids;
    }

    public static List<String> selectionIds(Map<String, ? extends List<?>> groups,
                                             String groupPrefix) {
        List<String> ids = new ArrayList<>();
        if (groups != null && groupPrefix != null) {
            groups.forEach((key, rewards) -> {
                ids.add(groupPrefix + key);
                ids.addAll(rewardIdsForGroup(groups, key));
            });
        }
        return ids;
    }
}
