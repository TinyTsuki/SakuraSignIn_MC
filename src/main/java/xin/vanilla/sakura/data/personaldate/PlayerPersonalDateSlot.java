package xin.vanilla.sakura.data.personaldate;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import java.util.Map;
import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
public class PlayerPersonalDateSlot {
    private String presetId;
    private int slotIndex;
    private String calendarId;
    private int month;
    private int day;
    private String lastClaimedOccurrenceKey = "";
    private Map<String, String> claimedPeriodCursors = new LinkedHashMap<>();

    public PlayerPersonalDateSlot(String presetId, int slotIndex, String calendarId,
                                  int month, int day, String lastClaimedOccurrenceKey) {
        this.presetId = presetId;
        this.slotIndex = slotIndex;
        this.calendarId = calendarId;
        this.month = month;
        this.day = day;
        setLastClaimedOccurrenceKey(lastClaimedOccurrenceKey);
    }

    public void setLastClaimedOccurrenceKey(String key) {
        if (key == null || key.isEmpty()) {
            lastClaimedOccurrenceKey = "";
            claimedPeriodCursors.clear();
            return;
        }
        remember(lastClaimedOccurrenceKey);
        remember(key);
        String prefix = prefix(key);
        lastClaimedOccurrenceKey = prefix == null ? key : claimedPeriodCursors.get(prefix);
    }

    public String claimedKey(PersonalDateRecurrence recurrence) {
        String name = recurrence.name();
        String current = lastClaimedOccurrenceKey != null && lastClaimedOccurrenceKey.startsWith(name + ":")
                ? lastClaimedOccurrenceKey : "";
        String stored = claimedPeriodCursors.getOrDefault(name, "");
        return current.compareTo(stored) > 0 ? current : stored;
    }

    public void copyClaimState(PlayerPersonalDateSlot previous) {
        lastClaimedOccurrenceKey = previous == null ? "" : previous.lastClaimedOccurrenceKey;
        claimedPeriodCursors = previous == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(previous.claimedPeriodCursors);
        remember(lastClaimedOccurrenceKey);
    }

    public void mergeClaimState(PlayerPersonalDateSlot previous) {
        if (previous == null) return;
        previous.claimedPeriodCursors.values().forEach(this::remember);
        remember(previous.lastClaimedOccurrenceKey);
        remember(lastClaimedOccurrenceKey);
        if (lastClaimedOccurrenceKey == null || lastClaimedOccurrenceKey.isEmpty()) {
            lastClaimedOccurrenceKey = previous.lastClaimedOccurrenceKey;
        }
        String prefix = prefix(lastClaimedOccurrenceKey);
        if (prefix != null) lastClaimedOccurrenceKey = claimedPeriodCursors.get(prefix);
    }

    private void remember(String key) {
        String prefix = prefix(key);
        if (prefix != null) claimedPeriodCursors.merge(prefix, key,
                (first, second) -> first.compareTo(second) >= 0 ? first : second);
    }

    private static String prefix(String key) {
        if (key == null) return null;
        if (key.startsWith("YEARLY:")) return "YEARLY";
        if (key.startsWith("MONTHLY:")) return "MONTHLY";
        return null;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("presetId", presetId == null ? "" : presetId);
        tag.putInt("slotIndex", slotIndex);
        tag.putString("calendarId", calendarId == null ? "" : calendarId);
        tag.putInt("month", month);
        tag.putInt("day", day);
        tag.putString("lastClaimedOccurrenceKey",
                lastClaimedOccurrenceKey == null ? "" : lastClaimedOccurrenceKey);
        if (claimedPeriodCursors.size() > 1) {
            CompoundTag cursors = new CompoundTag();
            claimedPeriodCursors.forEach(cursors::putString);
            tag.put("claimedPeriodCursors", cursors);
        }
        return tag;
    }

    public static PlayerPersonalDateSlot deserializeNBT(CompoundTag tag) {
        PlayerPersonalDateSlot slot = new PlayerPersonalDateSlot();
        slot.presetId = tag.getString("presetId");
        slot.slotIndex = tag.getInt("slotIndex");
        slot.calendarId = tag.getString("calendarId");
        slot.month = tag.getInt("month");
        slot.day = tag.getInt("day");
        slot.lastClaimedOccurrenceKey = tag.getString("lastClaimedOccurrenceKey");
        CompoundTag cursors = tag.getCompound("claimedPeriodCursors");
        for (String period : new String[]{"YEARLY", "MONTHLY"}) {
            String key = cursors.getString(period);
            if (key.startsWith(period + ":")) slot.remember(key);
        }
        slot.remember(slot.lastClaimedOccurrenceKey);
        return slot;
    }

}
