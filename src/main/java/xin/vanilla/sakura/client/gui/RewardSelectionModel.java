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
}
