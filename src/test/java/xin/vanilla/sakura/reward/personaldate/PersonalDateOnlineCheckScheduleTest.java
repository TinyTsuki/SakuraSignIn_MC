package xin.vanilla.sakura.reward.personaldate;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonalDateOnlineCheckScheduleTest {
    @Test
    public void checksOnStartupDateRolloverAndSafetyIntervalOnly() {
        PersonalDateOnlineCheckSchedule schedule = new PersonalDateOnlineCheckSchedule(6000);
        LocalDate firstDay = LocalDate.of(2026, 8, 15);

        assertTrue(schedule.shouldCheck(1, firstDay));
        assertFalse(schedule.shouldCheck(2, firstDay));
        assertTrue(schedule.shouldCheck(100, firstDay.plusDays(1)));
        assertFalse(schedule.shouldCheck(101, firstDay.plusDays(1)));
        assertTrue(schedule.shouldCheck(6100, firstDay.plusDays(1)));
    }

    @Test
    public void serverTickResetStartsANewSchedule() {
        PersonalDateOnlineCheckSchedule schedule = new PersonalDateOnlineCheckSchedule(6000);
        LocalDate date = LocalDate.of(2026, 8, 15);
        schedule.shouldCheck(10000, date);

        assertTrue(schedule.shouldCheck(1, date));
    }
}
