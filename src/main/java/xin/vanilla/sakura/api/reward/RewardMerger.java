package xin.vanilla.sakura.api.reward;

import java.util.Optional;

public interface RewardMerger<T> {

    RewardMergeKey key(T value);

    Optional<T> merge(T first, T second);
}
