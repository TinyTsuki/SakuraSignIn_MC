package xin.vanilla.sakura.internal.server.dev;

import org.junit.Test;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SakuraNetworkSmokeWorkloadTest {

    @Test
    public void createsBoundedHistoricalFixtureOutsideTheCurrentMonth() {
        PlayerSignInData data = new PlayerSignInData();
        Date now = Date.from(LocalDate.of(2026, 8, 31)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());

        SakuraNetworkSmokeWorkload.seedHistoricalRecords(data, UUID.randomUUID(), now);

        assertEquals(SakuraNetworkSmokeWorkload.HISTORY_RECORD_COUNT,
                data.getSignInRecords().size());
        assertEquals(SakuraNetworkSmokeWorkload.HISTORY_MONTHS,
                data.getMonthIndexes().size());
        assertTrue(data.getMonthIndexes().containsKey("2026-07"));
        assertFalse(data.getMonthIndexes().containsKey("2026-08"));
        for (SignInRecord record : data.getSignInRecords()) {
            LocalDate date = record.getCompensateTime().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            assertTrue(date.isBefore(LocalDate.of(2026, 8, 1)));
            assertTrue(record.isRewarded());
        }
    }
}
