package xin.vanilla.sakura.client.ui;

import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import java.util.List;
import java.util.stream.Collectors;

/** Tracks submitted values independently of the active renderer. */
public final class PersonalDateDraftState {
    private String submitted;
    public PersonalDateDraftState(List<PlayerPersonalDateSlot> slots) { submitted(slots); }
    public boolean dirty(List<PlayerPersonalDateSlot> slots) { return !submitted.equals(signature(slots)); }
    public void submitted(List<PlayerPersonalDateSlot> slots) { submitted = signature(slots); }
    private static String signature(List<PlayerPersonalDateSlot> slots) {
        return slots.stream().map(slot -> slot.getPresetId() + ":" + slot.getSlotIndex() + ":" + slot.getCalendarId() + ":" + slot.getMonth() + ":" + slot.getDay())
                .sorted().collect(Collectors.joining(";"));
    }
}
