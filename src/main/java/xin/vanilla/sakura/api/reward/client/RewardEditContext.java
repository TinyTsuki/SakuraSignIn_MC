package xin.vanilla.sakura.api.reward.client;

import lombok.Value;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 类型编辑器只提交领域值，配置写入和概率由 Sakura 外层负责。
 */
@Value
public class RewardEditContext<T> {
    Object parentScreen;
    @Nullable
    T initialValue;
    Consumer<T> submit;
    Runnable cancel;
}
