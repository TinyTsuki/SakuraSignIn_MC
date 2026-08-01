package xin.vanilla.sakura.data.personaldate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客户端只能替换日期选择，领取游标始终以服务端旧值为准。 */
public final class PersonalDateSelectionService {
    public PersonalDateSelectionResult replace(List<PersonalDatePreset> presets,
                                               List<PlayerPersonalDateSlot> current,
                                               List<PlayerPersonalDateSlot> candidates) {
        if (presets == null || current == null || candidates == null) {
            return failure("incomplete");
        }
        Map<String, PersonalDatePreset> presetsById = new LinkedHashMap<>();
        for (PersonalDatePreset preset : presets) {
            if (!PersonalDatePresetValidator.validate(preset).isEmpty()
                    || presetsById.put(preset.getId(), preset) != null) {
                return failure("preset");
            }
        }

        Map<String, PlayerPersonalDateSlot> currentByKey = new LinkedHashMap<>();
        current.stream().filter(slot -> slot != null)
                .forEach(slot -> currentByKey.put(slotKey(slot), copy(slot)));
        Map<String, PlayerPersonalDateSlot> candidateByKey = new LinkedHashMap<>();
        for (PlayerPersonalDateSlot candidate : candidates) {
            if (candidate == null) {
                return failure("slot");
            }
            PersonalDatePreset preset = presetsById.get(candidate.getPresetId());
            String key = slotKey(candidate);
            if (preset == null || candidateByKey.containsKey(key)
                    || !PersonalDateSlotValidator.isValid(preset, candidate)) {
                return failure("slot:" + key);
            }
            PlayerPersonalDateSlot accepted = copy(candidate);
            PlayerPersonalDateSlot previous = currentByKey.get(key);
            accepted.setLastClaimedOccurrenceKey(previous == null
                    ? "" : previous.getLastClaimedOccurrenceKey());
            candidateByKey.put(key, accepted);
        }

        Map<String, PlayerPersonalDateSlot> result = new LinkedHashMap<>();
        currentByKey.forEach((key, previous) -> {
            PlayerPersonalDateSlot replacement = candidateByKey.remove(key);
            if (replacement != null) {
                result.put(key, replacement);
                return;
            }
            PlayerPersonalDateSlot inert = copy(previous);
            if (presetsById.containsKey(inert.getPresetId())) {
                inert.setMonth(0);
                inert.setDay(0);
            }
            result.put(key, inert);
        });
        result.putAll(candidateByKey);
        return new PersonalDateSelectionResult(true,
                Collections.unmodifiableList(new ArrayList<>(result.values())),
                Collections.emptyList());
    }

    private static PersonalDateSelectionResult failure(String error) {
        return new PersonalDateSelectionResult(false, Collections.emptyList(),
                Collections.singletonList(error));
    }

    private static String slotKey(PlayerPersonalDateSlot slot) {
        return slot.getPresetId() + "#" + slot.getSlotIndex();
    }

    private static PlayerPersonalDateSlot copy(PlayerPersonalDateSlot slot) {
        return PlayerPersonalDateSlot.deserializeNBT(slot.serializeNBT());
    }
}
