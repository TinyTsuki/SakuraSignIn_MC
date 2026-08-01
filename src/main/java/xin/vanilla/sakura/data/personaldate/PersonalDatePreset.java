package xin.vanilla.sakura.data.personaldate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xin.vanilla.sakura.reward.RewardList;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalDatePreset {
    private String id;
    private String displayName;
    private PersonalDateRecurrence recurrence;
    private List<String> calendarIds;
    private int maxDateSlots;
    private PersonalDateDeliveryMode deliveryMode;
    private int validBeforeDays;
    private int validAfterDays;
    private RewardList rewards;
}
