package xin.vanilla.sakura.reward.personaldate;

import java.time.LocalDate;

/** 日期变化立即检查，其他时间只按低频间隔兜底。 */
public final class PersonalDateOnlineCheckSchedule {
    private final long safetyIntervalTicks;
    private long lastTick = Long.MIN_VALUE;
    private long nextSafetyTick = Long.MIN_VALUE;
    private LocalDate lastDate;

    public PersonalDateOnlineCheckSchedule(long safetyIntervalTicks) {
        if (safetyIntervalTicks < 1) {
            throw new IllegalArgumentException("safetyIntervalTicks");
        }
        this.safetyIntervalTicks = safetyIntervalTicks;
    }

    public boolean shouldCheck(long currentTick, LocalDate currentDate) {
        if (currentDate == null) {
            return false;
        }
        boolean serverRestarted = lastTick != Long.MIN_VALUE && currentTick < lastTick;
        boolean dateChanged = !currentDate.equals(lastDate);
        boolean safetyDue = nextSafetyTick == Long.MIN_VALUE || currentTick >= nextSafetyTick;
        lastTick = currentTick;
        if (!serverRestarted && !dateChanged && !safetyDue) {
            return false;
        }
        lastDate = currentDate;
        nextSafetyTick = currentTick + safetyIntervalTicks;
        return true;
    }
}
