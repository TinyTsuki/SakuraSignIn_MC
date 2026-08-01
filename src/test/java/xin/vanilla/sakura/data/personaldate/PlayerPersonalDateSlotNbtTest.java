package xin.vanilla.sakura.data.personaldate;

import org.junit.Test;
import xin.vanilla.sakura.data.calendar.CalendarIds;

import static org.junit.Assert.assertEquals;

public class PlayerPersonalDateSlotNbtTest {
    @Test
    public void preservesStableSlotAndClaimCursor() {
        PlayerPersonalDateSlot source = new PlayerPersonalDateSlot(
                "server_day", 3, CalendarIds.CHINESE_LUNAR, 8, 15, "YEARLY:2026");

        PlayerPersonalDateSlot restored = PlayerPersonalDateSlot.deserializeNBT(source.serializeNBT());

        assertEquals(source, restored);
    }
}
