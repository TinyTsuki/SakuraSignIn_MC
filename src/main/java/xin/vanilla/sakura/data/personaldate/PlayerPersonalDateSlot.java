package xin.vanilla.sakura.data.personaldate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundNBT;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerPersonalDateSlot {
    private String presetId;
    private int slotIndex;
    private PersonalDateCalendar calendar;
    private int month;
    private int day;
    private String lastClaimedOccurrenceKey = "";

    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("presetId", presetId == null ? "" : presetId);
        tag.putInt("slotIndex", slotIndex);
        tag.putString("calendar", calendar == null ? "" : calendar.name());
        tag.putInt("month", month);
        tag.putInt("day", day);
        tag.putString("lastClaimedOccurrenceKey",
                lastClaimedOccurrenceKey == null ? "" : lastClaimedOccurrenceKey);
        return tag;
    }

    public static PlayerPersonalDateSlot deserializeNBT(CompoundNBT tag) {
        PlayerPersonalDateSlot slot = new PlayerPersonalDateSlot();
        slot.presetId = tag.getString("presetId");
        slot.slotIndex = tag.getInt("slotIndex");
        slot.calendar = parseCalendar(tag.getString("calendar"));
        slot.month = tag.getInt("month");
        slot.day = tag.getInt("day");
        slot.lastClaimedOccurrenceKey = tag.getString("lastClaimedOccurrenceKey");
        return slot;
    }

    private static PersonalDateCalendar parseCalendar(String value) {
        try {
            return PersonalDateCalendar.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
