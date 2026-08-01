package xin.vanilla.sakura.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 奖励编辑器的选择状态，提供普通、Ctrl 切换与 Shift 连续区间选择。
 */
public final class RewardSelectionModel {
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private String anchor;
    private String primary;

    public void select(String id, List<String> orderedIds, boolean ctrl, boolean shift) {
        if (id == null || !orderedIds.contains(id)) {
            return;
        }
        if (shift && anchor != null && orderedIds.contains(anchor)) {
            if (!ctrl) {
                selectedIds.clear();
            }
            int from = orderedIds.indexOf(anchor);
            int to = orderedIds.indexOf(id);
            int start = Math.min(from, to);
            int end = Math.max(from, to);
            selectedIds.addAll(orderedIds.subList(start, end + 1));
        } else if (ctrl) {
            if (!selectedIds.remove(id)) {
                selectedIds.add(id);
            }
            anchor = id;
        } else {
            selectedIds.clear();
            selectedIds.add(id);
            anchor = id;
        }
        primary = selectedIds.contains(id)
                ? id
                : selectedIds.stream().reduce((first, second) -> second).orElse(null);
    }

    public void selectOnly(String id) {
        selectedIds.clear();
        if (id != null) {
            selectedIds.add(id);
        }
        anchor = id;
        primary = id;
    }

    public void selectOnly(Collection<String> ids) {
        selectedIds.clear();
        if (ids != null) {
            ids.stream().filter(id -> id != null && !id.isEmpty()).forEach(selectedIds::add);
        }
        anchor = selectedIds.stream().findFirst().orElse(null);
        primary = selectedIds.stream().reduce((first, second) -> second).orElse(null);
    }

    /**
     * 中键切换奖励组，并移除该组内已有的奖励选择。
     */
    public void toggleGroup(String groupId, Collection<String> groupRewardIds) {
        boolean selected = selectedIds.contains(groupId);
        removeGroupState(groupId, groupRewardIds);
        if (!selected && groupId != null) {
            selectedIds.add(groupId);
            anchor = groupId;
        }
        refreshPrimary(groupId);
    }

    /**
     * 中键按“奖励 -> 组 -> 未选择”循环，只修改命中的组。
     */
    public void cycleMiddleReward(String rewardId, String groupId,
                                  Collection<String> groupRewardIds) {
        if (selectedIds.contains(groupId)) {
            removeGroupState(groupId, groupRewardIds);
            refreshPrimary(null);
            return;
        }
        if (selectedIds.contains(rewardId)) {
            removeGroupState(groupId, groupRewardIds);
            selectedIds.add(groupId);
            anchor = groupId;
            refreshPrimary(groupId);
            return;
        }
        removeGroupState(groupId, groupRewardIds);
        if (rewardId != null) {
            selectedIds.add(rewardId);
            anchor = rewardId;
        }
        refreshPrimary(rewardId);
    }

    public boolean isSelected(String id) {
        return selectedIds.contains(id);
    }

    public String primary() {
        return primary;
    }

    public List<String> selectedIds() {
        return Collections.unmodifiableList(new ArrayList<>(selectedIds));
    }

    public void retainAll(Collection<String> validIds) {
        selectedIds.retainAll(validIds);
        if (!selectedIds.contains(anchor)) {
            anchor = null;
        }
        if (!selectedIds.contains(primary)) {
            primary = selectedIds.stream().reduce((first, second) -> second).orElse(null);
        }
    }

    public void clear() {
        selectedIds.clear();
        anchor = null;
        primary = null;
    }

    private void removeGroupState(String groupId, Collection<String> groupRewardIds) {
        selectedIds.remove(groupId);
        if (groupRewardIds != null) {
            selectedIds.removeAll(groupRewardIds);
        }
        if ((groupId != null && groupId.equals(anchor))
                || (groupRewardIds != null && groupRewardIds.contains(anchor))) {
            anchor = null;
        }
    }

    private void refreshPrimary(String preferred) {
        primary = preferred != null && selectedIds.contains(preferred)
                ? preferred
                : selectedIds.stream().reduce((first, second) -> second).orElse(null);
    }
}
