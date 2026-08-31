package xin.vanilla.sakura.internal.server.dev;

import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.reward.RewardManager;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/** 仅开发期使用的有界签到历史与奖励查询负载。 */
public final class SakuraNetworkSmokeWorkload {
    static final int HISTORY_MONTHS = 180;
    static final int RECORDS_PER_MONTH = 20;
    static final int HISTORY_RECORD_COUNT = HISTORY_MONTHS * RECORDS_PER_MONTH;
    private static final int QUERIES_PER_TICK = 4;
    private static final int TICKS = 320;

    private final IPlayerSignInData data;
    private final Date targetDate;
    private int ticks;

    public SakuraNetworkSmokeWorkload(IPlayerSignInData data, Date targetDate) {
        this.data = data;
        this.targetDate = targetDate;
    }

    public static void seedHistoricalRecords(IPlayerSignInData data, UUID playerId, Date now) {
        YearMonth newest = YearMonth.from(now.toInstant().atZone(ZoneId.systemDefault()))
                .minusMonths(1L);
        for (int monthOffset = 0; monthOffset < HISTORY_MONTHS; monthOffset++) {
            YearMonth month = newest.minusMonths(monthOffset);
            for (int day = 1; day <= RECORDS_PER_MONTH; day++) {
                LocalDate localDate = month.atDay(day);
                Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                SignInRecord record = new SignInRecord();
                record.setCompensateTime(date);
                record.setSignInTime(date);
                record.setSignInUUID(playerId.toString());
                record.setRewarded(true);
                data.getSignInRecords().add(record);
                data.markSigned(date, true);
            }
        }
    }

    public boolean tick() {
        if (ticks >= TICKS) return true;
        for (int query = 0; query < QUERIES_PER_TICK; query++) {
            RewardManager.getRewardListByDate(targetDate, data, false, true);
            data.isSignedOn(targetDate);
            data.isRewardedOn(targetDate);
        }
        ticks++;
        return ticks >= TICKS;
    }

    public int ticks() {
        return ticks;
    }
}
