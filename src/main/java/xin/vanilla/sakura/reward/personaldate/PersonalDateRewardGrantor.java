package xin.vanilla.sakura.reward.personaldate;

import xin.vanilla.sakura.data.personaldate.PersonalDateOccurrence;
import xin.vanilla.sakura.reward.Reward;

@FunctionalInterface
public interface PersonalDateRewardGrantor {
    void grant(Reward reward, PersonalDateOccurrence occurrence);
}
