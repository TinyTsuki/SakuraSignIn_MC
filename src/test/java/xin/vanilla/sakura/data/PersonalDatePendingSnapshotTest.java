package xin.vanilla.sakura.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.Test;
import xin.vanilla.sakura.network.packet.PlayerMonthSyncPacket;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.data.personaldate.PersonalDateRewardSnapshot;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class PersonalDatePendingSnapshotTest {
    @Test
    public void deferredSnapshotSurvivesRecordAndNetworkRoundTrip() throws Exception {
        CompoundNBT tag = record();
        SignInRecord loaded = SignInRecord.readFromNBT(tag);
        assertEquals(1, loaded.writeToNBT().getList("personalDateRewards", 10).size());
        assertEquals(tag, loaded.writeToNBT());
        assertEquals(tag, PlayerMonthSyncPacket.decodeRecords("2026-10",
                Collections.singletonList(tag.toString())).get(0).writeToNBT());
    }

    @Test
    public void clonedDeferredRewardDoesNotShareMutablePayload() throws Exception {
        SignInRecord original = SignInRecord.readFromNBT(record());
        SignInRecord copy = original.clone();
        List<PersonalDateRewardSnapshot> snapshots = copy.getPersonalDateRewards();
        RewardList rewards = snapshots.get(0).getRewards();
        rewards.get(0).setProbability(BigDecimal.ZERO);
        assertTrue(original.writeToNBT().getList("personalDateRewards", 10).getCompound(0)
                .getString("rewards").contains("\"probability\":1"));
    }

    @Test
    public void oldRecordWithoutPersonalDateMetadataKeepsIdenticalEncoding() {
        CompoundNBT old = record();
        old.remove("personalDateRewards");
        assertEquals(old, SignInRecord.readFromNBT(old).writeToNBT());
    }

    @Test
    public void invalidPendingPayloadCannotSilentlyDisappear() {
        CompoundNBT invalid = record();
        invalid.getList("personalDateRewards", 10).getCompound(0).putString("rewards", "not json");
        assertThrows(RuntimeException.class, () -> SignInRecord.readFromNBT(invalid));
    }

    @Test
    public void malformedPendingContainerCannotBeTreatedAsAbsent() {
        CompoundNBT invalid = record();
        invalid.putString("personalDateRewards", "incorrect type");
        assertThrows(RuntimeException.class, () -> SignInRecord.readFromNBT(invalid));
        ListNBT wrongList = new ListNBT();
        wrongList.add(net.minecraft.nbt.StringNBT.valueOf("incorrect element"));
        invalid.put("personalDateRewards", wrongList);
        assertThrows(RuntimeException.class, () -> SignInRecord.readFromNBT(invalid));
    }

    public static CompoundNBT record() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("compensateTime", "2026-10-09 12:00:00");
        tag.putString("signInTime", "2026-10-09 12:00:00");
        tag.putString("signInUUID", "00000000-0000-0000-0000-000000001665");
        tag.putBoolean("rewarded", false);
        tag.putString("rewardList", "[]");
        CompoundNBT snapshot = new CompoundNBT();
        snapshot.putString("presetId", "birthday");
        snapshot.putString("presetName", "Birthday");
        snapshot.putInt("slotIndex", 0);
        snapshot.putString("calendarId", "minecraft:gregorian");
        snapshot.putString("occurrenceKey", "YEARLY:2026");
        snapshot.putString("targetDate", "2026-10-09");
        snapshot.putString("rewards", "[{\"type\":\"sakura_sign_in:sign_in_card\",\"probability\":1,\"content\":{\"signInCard\":1}}]");
        ListNBT snapshots = new ListNBT();
        snapshots.add(snapshot);
        tag.put("personalDateRewards", snapshots);
        return tag;
    }
}
