package xin.vanilla.sakura.domain.player;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundNBT;

/**
 * 一个月最多 31 天，两个位图分别记录签到与领奖状态。
 */
@Data
@NoArgsConstructor
public class MonthSignInIndex {
    private String month;
    private int signedDays;
    private int rewardedDays;

    public MonthSignInIndex(String month) {
        this.month = month;
    }

    public void markSigned(int day, boolean rewarded) {
        int bit = dayBit(day);
        signedDays |= bit;
        if (rewarded) {
            rewardedDays |= bit;
        }
    }

    public boolean isSigned(int day) {
        return (signedDays & dayBit(day)) != 0;
    }

    public boolean isRewarded(int day) {
        return (rewardedDays & dayBit(day)) != 0;
    }

    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("month", month);
        tag.putInt("signedDays", signedDays);
        tag.putInt("rewardedDays", rewardedDays);
        return tag;
    }

    public static MonthSignInIndex deserializeNBT(CompoundNBT tag) {
        MonthSignInIndex index = new MonthSignInIndex(tag.getString("month"));
        index.signedDays = tag.getInt("signedDays");
        index.rewardedDays = tag.getInt("rewardedDays");
        return index;
    }

    private static int dayBit(int day) {
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("Day must be between 1 and 31: " + day);
        }
        return 1 << (day - 1);
    }
}
