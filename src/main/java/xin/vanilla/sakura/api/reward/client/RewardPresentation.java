package xin.vanilla.sakura.api.reward.client;

import xin.vanilla.banira.common.data.Component;

import java.util.Collections;
import java.util.List;

public interface RewardPresentation<T> {
    Component displayName(RewardDisplayContext context, T value);

    default List<Component> tooltip(RewardDisplayContext context, T value) {
        return Collections.singletonList(displayName(context, value));
    }

    void renderIcon(RewardRenderContext context, T value);
}
