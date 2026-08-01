package xin.vanilla.sakura.data.time;

import lombok.Value;

@Value
public class OnlineTimeRequirementResult {
    boolean allowed;
    boolean baselineChanged;
    String baselineDate;
    int baselineTicks;
    long totalSeconds;
    long todaySeconds;
    long missingTotalSeconds;
    long missingTodaySeconds;
}
