package xin.vanilla.sakura.api.reward;

import lombok.Value;

import java.util.Objects;

@Value(staticConstructor = "ofUnchecked")
public class RewardMergeKey {
    String value;

    public static RewardMergeKey of(String value) {
        String key = Objects.requireNonNull(value, "value").trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Reward merge key cannot be empty");
        }
        return ofUnchecked(key);
    }
}
