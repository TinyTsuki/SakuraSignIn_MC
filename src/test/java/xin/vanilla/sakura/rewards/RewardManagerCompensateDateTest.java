package xin.vanilla.sakura.rewards;

import org.junit.Test;
import xin.vanilla.sakura.enums.ETimeCoolingMethod;
import xin.vanilla.sakura.util.DateUtils;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * 历史保留边界必须与实际签到日使用同一套时间校准。
 */
public class RewardManagerCompensateDateTest {
    @Test
    public void fixedRefreshTimeKeepsEarlyFirstDayInPreviousMonth() {
        Date serverDate = DateUtils.format("2024-03-01 00:30:00");

        Date compensated = RewardManager.getCompensateDate(
                serverDate, ETimeCoolingMethod.FIXED_TIME, 1.0
        );

        assertEquals(
                YearMonth.of(2024, 2),
                YearMonth.from(compensated.toInstant().atZone(ZoneId.systemDefault()))
        );
    }
}
