package xin.vanilla.sakura.network.packet;

import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.data.personaldate.PersonalDateCalendar;
import xin.vanilla.sakura.data.personaldate.PersonalDateCalendarPolicy;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PersonalDatePacketTest {
    @Test
    public void presetPacketKeepsServerMetadataAndRewards() {
        JsonObject content = new JsonObject();
        content.addProperty("text", "reward");
        PersonalDatePreset preset = new PersonalDatePreset(
                "server_day", "Server Day", PersonalDateRecurrence.YEARLY,
                PersonalDateCalendarPolicy.PLAYER_CHOICE, 2,
                PersonalDateDeliveryMode.ONLINE, 3, 7,
                new RewardList(Collections.singletonList(
                        new Reward(content, SakuraRewardTypes.MESSAGE, BigDecimal.ONE)))
        );
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();

        new PersonalDatePresetSyncPacket(Collections.singletonList(preset)).toBytes(buffer);
        PersonalDatePresetSyncPacket restored = new PersonalDatePresetSyncPacket(buffer);

        assertEquals(Collections.singletonList(preset), restored.getPresets());
    }

    @Test
    public void slotUpdatePacketNeverCarriesClaimCursor() {
        PlayerPersonalDateSlot slot = new PlayerPersonalDateSlot(
                "server_day", 1, PersonalDateCalendar.LUNAR, 8, 15, "YEARLY:2026");
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();

        new PersonalDateSlotUpdatePacket(Collections.singletonList(slot)).toBytes(buffer);
        PersonalDateSlotUpdatePacket restored = new PersonalDateSlotUpdatePacket(buffer);

        assertEquals(1, restored.getSlots().size());
        assertEquals("", restored.getSlots().get(0).getLastClaimedOccurrenceKey());
    }
}
