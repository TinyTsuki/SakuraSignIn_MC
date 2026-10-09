package xin.vanilla.sakura.data.personaldate;

import lombok.Value;
import net.minecraft.nbt.CompoundTag;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.reward.RewardListJsonCodec;

import java.time.LocalDate;

@Value
public class PersonalDateRewardSnapshot {
    String presetId;
    String presetName;
    int slotIndex;
    String calendarId;
    String occurrenceKey;
    LocalDate targetDate;
    RewardList rewards;

    public PersonalDateRewardSnapshot(String presetId, String presetName, int slotIndex,
                                      String calendarId, String occurrenceKey,
                                      LocalDate targetDate, RewardList rewards) {
        if (presetId == null || presetId.isEmpty() || slotIndex < 0 || slotIndex >= 2048
                || calendarId == null || calendarId.isEmpty() || targetDate == null || rewards == null
                || occurrenceKey == null
                || !(occurrenceKey.equals("YEARLY:" + targetDate.getYear())
                || occurrenceKey.equals("MONTHLY:" + java.time.YearMonth.from(targetDate)))) {
            throw new IllegalArgumentException("Invalid personal date reward snapshot");
        }
        this.presetId = presetId;
        this.presetName = presetName == null || presetName.isEmpty() ? presetId : presetName;
        this.slotIndex = slotIndex;
        this.calendarId = calendarId;
        this.occurrenceKey = occurrenceKey;
        this.targetDate = targetDate;
        this.rewards = rewards.clone();
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("presetId", presetId);
        tag.putString("presetName", presetName);
        tag.putInt("slotIndex", slotIndex);
        tag.putString("calendarId", calendarId);
        tag.putString("occurrenceKey", occurrenceKey);
        tag.putString("targetDate", targetDate.toString());
        tag.putString("rewards", RewardListJsonCodec.encode(rewards).toString());
        return tag;
    }

    public static PersonalDateRewardSnapshot deserializeNBT(CompoundTag tag) {
        RewardList rewards = RewardListJsonCodec.decodeLenient(tag.getString("rewards"),
                failure -> { throw failure; });
        return new PersonalDateRewardSnapshot(tag.getString("presetId"), tag.getString("presetName"),
                tag.getInt("slotIndex"), tag.getString("calendarId"), tag.getString("occurrenceKey"),
                LocalDate.parse(tag.getString("targetDate")), rewards);
    }

    public PersonalDateRewardSnapshot copy() {
        return new PersonalDateRewardSnapshot(presetId, presetName, slotIndex, calendarId,
                occurrenceKey, targetDate, rewards);
    }
}
