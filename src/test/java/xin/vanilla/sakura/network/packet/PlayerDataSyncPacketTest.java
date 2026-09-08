package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.network.month.MonthDataTransfer;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.banira.common.util.DateUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 玩家摘要与月份详情必须使用两个独立负载。
 */
public class PlayerDataSyncPacketTest {
    @Test
    public void monthPacketKeepsARecordLargerThanTheOldStringLimit() throws Exception {
        xin.vanilla.sakura.test.BaniraTestPlatform.install();
        SignInRecord record = record("2024-02-01 12:00:00");
        char[] padding = new char[40000];
        Arrays.fill(padding, 'x');
        com.google.gson.JsonObject item = new com.google.gson.JsonObject();
        item.addProperty("item", "minecraft:carrot");
        item.addProperty("count", 1);
        item.addProperty("nbt", "{smoke:\"" + new String(padding) + "\"}");
        record.getRewardList().add(new xin.vanilla.sakura.reward.Reward(item,
                xin.vanilla.sakura.api.reward.SakuraRewardTypes.ITEM));
        List<PlayerMonthSyncPacket> packets = PlayerMonthSyncPacket.prepare(
                UUID.randomUUID(), "2024-02", Collections.singletonList(record));
        assertTrue(packets.size() > 1);
        MonthDataTransfer.Receiver receiver = new MonthDataTransfer.Receiver(System::nanoTime);
        for (int i = 0; i < packets.size(); i++) {
            net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                packets.get(i).toBytes(nativeBuffer(buffer));
                PlayerMonthSyncPacket decoded = new PlayerMonthSyncPacket(nativeBuffer(buffer));
                Optional<List<String>> complete = receiver.accept(decoded.getPart());
                assertEquals(i == packets.size() - 1, complete.isPresent());
                if (complete.isPresent()) {
                    assertEquals(record.writeToNBT(), PlayerMonthSyncPacket.decodeRecords(
                            decoded.getMonth(), complete.get()).get(0).writeToNBT());
                }
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    private static xin.vanilla.banira.common.network.BaniraPacketBuffer nativeBuffer(net.minecraft.network.FriendlyByteBuf buffer) {
        return (xin.vanilla.banira.common.network.BaniraPacketBuffer) java.lang.reflect.Proxy.newProxyInstance(
                PlayerDataSyncPacketTest.class.getClassLoader(),
                new Class<?>[]{xin.vanilla.banira.common.network.BaniraPacketBuffer.class}, (proxy, method, arguments) -> {
                    String name = method.getName().replace("Uuid", "UUID");
                    try {
                        return buffer.getClass().getMethod(name, method.getParameterTypes()).invoke(buffer, arguments);
                    } catch (java.lang.reflect.InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

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
        PlayerMonthSyncPacket source = PlayerMonthSyncPacket.prepare(
                uuid,
                "2024-02",
                Arrays.asList(
                        record("2024-01-31 12:00:00"),
                        record("2024-02-01 12:00:00"),
                        record("2024-02-20 12:00:00")
                )
        ).get(0);

        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
        source.toBytes(buffer);
        PlayerMonthSyncPacket restored = new PlayerMonthSyncPacket(buffer);

        assertEquals("2024-02", restored.getMonth());
        List<SignInRecord> records = PlayerMonthSyncPacket.decodeRecords(restored.getMonth(),
                new MonthDataTransfer.Receiver(System::nanoTime).accept(restored.getPart()).get());
        assertEquals(2, records.size());
        assertFalse(records.stream()
                .anyMatch(record -> "2024-01".equals(PlayerMonthSyncPacket.monthOf(record))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void monthDecodeRejectsARecordFromAnotherMonth() {
        PlayerMonthSyncPacket.decodeRecords("2024-02", Collections.singletonList(
                record("2024-01-01 12:00:00").writeToNBT().toString()));
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
