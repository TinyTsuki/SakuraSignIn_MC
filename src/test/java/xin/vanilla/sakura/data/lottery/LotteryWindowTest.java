package xin.vanilla.sakura.data.lottery;

import org.junit.Test;
import xin.vanilla.sakura.reward.RewardList;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.*;

public class LotteryWindowTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final LotteryPool pool = new LotteryPool("test", "Test", LotteryLimitPolicy.DAILY, 10, 0,
            LotteryPreviewMode.ALL, new RewardList());

    @Test
    public void minuteBoundariesUseServerZoneAndExcludeExactEnd() {
        pool.setStartDate("2026-11-06 13:30");
        pool.setEndDate("2026-11-06 13:45");
        assertEquals(new LotteryWindow(false, true, 60_000), at("2026-11-06T13:29"));
        assertEquals(new LotteryWindow(true, false, 900_000), at("2026-11-06T13:30"));
        assertEquals(new LotteryWindow(false, false, 0), at("2026-11-06T13:45"));
        assertFalse(LotteryPoolValidator.validDate("2026-11-06 24:00"));
        assertFalse(LotteryPoolValidator.validDate("2026-02-30 12:00"));
    }

    @Test
    public void dateOnlyKeepsEndDayInclusive() {
        pool.setStartDate("2026-11-06");
        pool.setEndDate("2026-11-06");
        assertEquals(new LotteryWindow(true, false, 60_000), at("2026-11-06T23:59"));
        assertFalse(at("2026-11-07T00:00").open());
    }

    private LotteryWindow at(String time) {
        return LotteryWindow.at(pool, LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli(), zone);
    }
}
