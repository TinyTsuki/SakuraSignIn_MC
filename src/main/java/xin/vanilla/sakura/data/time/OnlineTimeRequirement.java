package xin.vanilla.sakura.data.time;

import java.time.LocalDate;

/** Uses the vanilla lifetime play-time statistic and a persisted daily baseline. */
public final class OnlineTimeRequirement {
    private OnlineTimeRequirement() {
    }

    public static OnlineTimeRequirementResult evaluate(String storedDate, int storedBaselineTicks,
                                                       LocalDate today, int currentPlayTicks,
                                                       long requiredTotalSeconds,
                                                       long requiredTodaySeconds) {
        if (today == null) {
            throw new IllegalArgumentException("Current date is required");
        }
        int safeTicks = Math.max(0, currentPlayTicks);
        String date = today.toString();
        boolean reset = !date.equals(storedDate) || storedBaselineTicks < 0
                || storedBaselineTicks > safeTicks;
        int baseline = reset ? safeTicks : storedBaselineTicks;
        long totalSeconds = safeTicks / 20L;
        long todaySeconds = Math.max(0, safeTicks - baseline) / 20L;
        long missingTotal = Math.max(0L, requiredTotalSeconds - totalSeconds);
        long missingToday = Math.max(0L, requiredTodaySeconds - todaySeconds);
        return new OnlineTimeRequirementResult(missingTotal == 0L && missingToday == 0L,
                reset, date, baseline, totalSeconds, todaySeconds, missingTotal, missingToday);
    }
}
