package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 玩家摘要与月份详情必须使用两个独立负载。
 */
public class PlayerDataSyncPacketTest {
    @Test
    public void summaryRoundTripKeepsIndexesWithoutHistoryDetails() {
        UUID uuid = UUID.randomUUID();
        Date signedDay = DateUtils.format("2024-01-08 12:00:00");
        PlayerSignInData source = new PlayerSignInData();
        source.markSigned(signedDay, true);
        source.setSignInRecords(Arrays.asList(record("2024-01-08 12:00:00")));
        source.setPersonalDateSlots(Collections.singletonList(
                new PlayerPersonalDateSlot("server_day", 1, CalendarIds.CHINESE_LUNAR,
                        8, 15, "YEARLY:2024")
        ));

        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
        new PlayerDataSyncPacket(uuid, source).toBytes(buffer);
        IPlayerSignInData restored = new PlayerDataSyncPacket(buffer).getData();

        assertTrue(restored.isSignedOn(signedDay));
        assertTrue(restored.isRewardedOn(signedDay));
        assertTrue(restored.getSignInRecords().isEmpty());
        assertEquals(source.getPersonalDateSlots(), restored.getPersonalDateSlots());
    }

    @Test
    public void monthPacketContainsOnlyRequestedMonth() {
        UUID uuid = UUID.randomUUID();
        PlayerMonthSyncPacket source = new PlayerMonthSyncPacket(
                uuid,
                "2024-02",
                Arrays.asList(
                        record("2024-01-31 12:00:00"),
                        record("2024-02-01 12:00:00"),
                        record("2024-02-20 12:00:00")
                )
        );

        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
        source.toBytes(buffer);
        PlayerMonthSyncPacket restored = new PlayerMonthSyncPacket(buffer);

        assertEquals("2024-02", restored.getMonth());
        assertEquals(2, restored.getRecords().size());
        assertFalse(restored.getRecords().stream()
                .anyMatch(record -> "2024-01".equals(PlayerMonthSyncPacket.monthOf(record))));
    }

    private static SignInRecord record(String dateTime) {
        SignInRecord record = new SignInRecord();
        Date date = DateUtils.format(dateTime);
        record.setCompensateTime(date);
        record.setSignInTime(date);
        record.setSignInUUID(UUID.randomUUID().toString());
        record.setRewardList(new RewardList());
        return record;
    }
}
