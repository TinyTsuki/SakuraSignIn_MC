package xin.vanilla.sakura.api.reward.client;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 标准编辑器工厂；具体界面由调用方提供的打开逻辑实现。
 */
public final class StandardRewardEditors {
    private StandardRewardEditors() {
    }

    public static <T> RewardEditorProvider<T> custom(
            Consumer<RewardEditContext<T>> opener) {
        Objects.requireNonNull(opener, "opener");
        return opener::accept;
    }
}
