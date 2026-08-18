package xin.vanilla.sakura.data.player;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;

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

    public void merge(MonthSignInIndex other) {
        if (other == null || !month.equals(other.month)) {
            return;
        }
        signedDays |= other.signedDays;
        rewardedDays |= other.rewardedDays;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("month", month);
        tag.putInt("signedDays", signedDays);
        tag.putInt("rewardedDays", rewardedDays);
        return tag;
    }

    public static MonthSignInIndex deserializeNBT(CompoundTag tag) {
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
