package xin.vanilla.sakura.api.reward;

import java.util.Collections;
import java.util.List;

public interface RewardValidator<T> {

    List<RewardViolation> validate(T value);

    static <T> RewardValidator<T> acceptAll() {
        return value -> Collections.emptyList();
    }
}
