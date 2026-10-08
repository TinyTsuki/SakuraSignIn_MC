package xin.vanilla.sakura.reward.personaldate;

import lombok.Value;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

@Value
public class PersonalDateDeliveryResult {
    int claimedOccurrences;
    int processedRewards;
    List<PersonalDateRewardReceipt> receipts;

    public PersonalDateDeliveryResult(int claimedOccurrences, int processedRewards) {
        this(claimedOccurrences, processedRewards, Collections.emptyList());
    }

    public PersonalDateDeliveryResult(int claimedOccurrences, int processedRewards,
                                      List<PersonalDateRewardReceipt> receipts) {
        this.claimedOccurrences = claimedOccurrences;
        this.processedRewards = processedRewards;
        List<PersonalDateRewardReceipt> copy = new ArrayList<>();
        receipts.forEach(receipt -> copy.add(new PersonalDateRewardReceipt(
                receipt.getPresetName(), receipt.getReward().clone())));
        this.receipts = Collections.unmodifiableList(copy);
    }

    public boolean changed() {
        return claimedOccurrences > 0;
    }
}
