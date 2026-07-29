package xin.vanilla.sakura.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.junit.Test;
import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.config.KeyValue;
import xin.vanilla.sakura.rewards.RewardList;
import xin.vanilla.sakura.util.DateUtils;

import java.util.Date;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 客户端必须收到永久月位图，而不只收到可清理的详情记录。
 */
public class PlayerDataSyncPacketTest {
    @Test
    public void roundTripKeepsSignedAndRewardedMonthIndexes() {
        UUID uuid = UUID.randomUUID();
        Date signedDay = DateUtils.format("2024-01-08 12:00:00");
        PlayerSignInData source = new PlayerSignInData();
        source.markSigned(signedDay, true);

        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new PlayerDataSyncPacket(uuid, source).toBytes(buffer);
        buffer.readerIndex(0);
        IPlayerSignInData restored = new PlayerDataSyncPacket(buffer).getData();

        assertTrue(restored.isSignedOn(signedDay));
        assertTrue(restored.isRewardedOn(signedDay));
    }

    @Test
    public void directPlayerDataBufferRoundTripReadsEachCollectionSizeOnce() {
        UUID uuid = UUID.randomUUID();
        Date signedDay = DateUtils.format("2024-01-08 12:00:00");
        SignInRecord record = new SignInRecord();
        record.setCompensateTime(signedDay);
        record.setSignInTime(signedDay);
        record.setSignInUUID(uuid.toString());
        record.setRewardList(new RewardList());
        PlayerSignInData source = new PlayerSignInData();
        source.markSigned(signedDay, false);
        source.setSignInRecords(Collections.singletonList(record));
        source.setCdkRecords(Collections.singletonList(
                new KeyValue<>("WELCOME", new KeyValue<>(signedDay, true))
        ));

        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        source.writeToBuffer(buffer);
        buffer.readerIndex(0);
        PlayerSignInData restored = new PlayerSignInData();
        restored.readFromBuffer(buffer);

        assertTrue(restored.isSignedOn(signedDay));
        assertEquals(1, restored.getSignInRecords().size());
        assertEquals("WELCOME", restored.getCdkRecords().get(0).getKey());
    }
}
