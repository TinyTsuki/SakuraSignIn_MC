package xin.vanilla.sakura.reward.personaldate;

import xin.vanilla.sakura.data.IPlayerSignInData;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDateEvaluator;
import xin.vanilla.sakura.data.personaldate.PersonalDateOccurrence;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.data.personaldate.PersonalDateRewardSnapshot;
import xin.vanilla.sakura.data.SignInRecord;
import xin.vanilla.sakura.reward.Reward;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 汇总有效周期并推进领取游标，不依赖玩家实体或加载器事件。
 */
public final class PersonalDateRewardService {
    private final PersonalDateEvaluator evaluator;

    public PersonalDateRewardService() {
        this(new PersonalDateEvaluator());
    }

    PersonalDateRewardService(PersonalDateEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public PersonalDateDeliveryResult deliver(PersonalDateDeliveryMode mode,
                                              LocalDate currentDate,
                                              List<PersonalDatePreset> presets,
                                              IPlayerSignInData playerData,
                                              PersonalDateRewardGrantor grantor) {
        if (mode == null || currentDate == null || presets == null
                || playerData == null || grantor == null) {
            throw new IllegalArgumentException("Personal date delivery arguments are incomplete");
        }
        Map<String, PersonalDatePreset> presetsById = new HashMap<>();
        presets.stream().filter(preset -> preset != null && preset.getDeliveryMode() == mode)
                .forEach(preset -> presetsById.put(preset.getId(), preset));

        List<PendingOccurrence> pending = new ArrayList<>();
        Set<String> seenSlots = new HashSet<>();
        for (PlayerPersonalDateSlot slot : playerData.getPersonalDateSlots()) {
            if (slot == null || !seenSlots.add(slot.getPresetId() + "#" + slot.getSlotIndex())) {
                continue;
            }
            PersonalDatePreset preset = presetsById.get(slot.getPresetId());
            if (preset == null) {
                continue;
            }
            evaluator.findActiveOccurrences(preset, slot, currentDate)
                    .stream().filter(occurrence -> !hasPending(playerData, occurrence))
                    .forEach(occurrence -> pending.add(
                            new PendingOccurrence(preset, slot, occurrence)));
        }
        pending.sort(Comparator
                .comparing((PendingOccurrence value) -> value.occurrence.getTargetDate())
                .thenComparing(value -> value.preset.getId())
                .thenComparingInt(value -> value.slot.getSlotIndex()));

        int claimed = 0;
        int rewards = 0;
        for (PendingOccurrence value : pending) {
            for (Reward reward : value.preset.getRewards()) {
                grantor.grant(reward.clone(), value.occurrence);
                rewards++;
            }
            value.slot.setLastClaimedOccurrenceKey(value.occurrence.getOccurrenceKey());
            claimed++;
        }
        return new PersonalDateDeliveryResult(claimed, rewards);
    }

    public List<PersonalDateRewardSnapshot> reserveForSignIn(LocalDate currentDate,
                                                            List<PersonalDatePreset> presets,
                                                            IPlayerSignInData data) {
        List<PersonalDateRewardSnapshot> snapshots = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> periods = new HashSet<>();
        for (PlayerPersonalDateSlot slot : data.getPersonalDateSlots()) {
            if (slot == null || !seen.add(slot.getPresetId() + "#" + slot.getSlotIndex())) continue;
            for (PersonalDatePreset preset : presets) {
                if (preset == null || preset.getDeliveryMode() != PersonalDateDeliveryMode.SIGN_IN
                        || !preset.getId().equals(slot.getPresetId())) continue;
                for (PersonalDateOccurrence occurrence : evaluator.findActiveOccurrences(preset, slot, currentDate)) {
                    if (!hasPending(data, occurrence) && periods.add(occurrence.getPresetId() + "#"
                            + occurrence.getSlotIndex() + "#" + occurrence.getOccurrenceKey())) {
                        snapshots.add(new PersonalDateRewardSnapshot(preset.getId(), preset.getDisplayName(),
                                slot.getSlotIndex(), slot.getCalendarId(), occurrence.getOccurrenceKey(),
                                occurrence.getTargetDate(), preset.getRewards()));
                    }
                }
            }
        }
        return snapshots;
    }

    private static boolean hasPending(IPlayerSignInData data, PersonalDateOccurrence occurrence) {
        return data.getSignInRecords().stream().filter(record -> !record.isRewarded())
                .flatMap(record -> record.getPersonalDateRewards().stream())
                .anyMatch(snapshot -> snapshot.getPresetId().equals(occurrence.getPresetId())
                        && snapshot.getSlotIndex() == occurrence.getSlotIndex()
                        && snapshot.getOccurrenceKey().equals(occurrence.getOccurrenceKey()));
    }

    public static void completeClaim(IPlayerSignInData data, PersonalDateRewardSnapshot snapshot) {
        PlayerPersonalDateSlot slot = data.getPersonalDateSlots().stream()
                .filter(value -> value.getPresetId().equals(snapshot.getPresetId())
                        && value.getSlotIndex() == snapshot.getSlotIndex()).findFirst().orElse(null);
        if (slot == null) {
            slot = new PlayerPersonalDateSlot(snapshot.getPresetId(), snapshot.getSlotIndex(),
                    snapshot.getCalendarId(), 0, 0, "");
            data.getPersonalDateSlots().add(slot);
        }
        slot.setLastClaimedOccurrenceKey(snapshot.getOccurrenceKey());
    }

    private static final class PendingOccurrence {
        private final PersonalDatePreset preset;
        private final PlayerPersonalDateSlot slot;
        private final PersonalDateOccurrence occurrence;

        private PendingOccurrence(PersonalDatePreset preset, PlayerPersonalDateSlot slot,
                                  PersonalDateOccurrence occurrence) {
            this.preset = preset;
            this.slot = slot;
            this.occurrence = occurrence;
        }
    }
}
