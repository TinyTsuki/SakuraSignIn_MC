package xin.vanilla.sakura.data.personaldate;

import lombok.Value;

import java.util.List;

@Value
public class PersonalDateSelectionResult {
    boolean success;
    List<PlayerPersonalDateSlot> slots;
    List<String> errors;
}
