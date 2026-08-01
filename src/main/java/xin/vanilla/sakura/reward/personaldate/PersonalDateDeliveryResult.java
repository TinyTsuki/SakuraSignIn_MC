package xin.vanilla.sakura.reward.personaldate;

import lombok.Value;

@Value
public class PersonalDateDeliveryResult {
    int claimedOccurrences;
    int processedRewards;

    public boolean changed() {
        return claimedOccurrences > 0;
    }
}
