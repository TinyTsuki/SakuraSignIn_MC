package xin.vanilla.sakura.data.personaldate;

import lombok.Value;

import java.time.LocalDate;

@Value
public class PersonalDateOccurrence {
    String presetId;
    int slotIndex;
    LocalDate targetDate;
    String occurrenceKey;
}
