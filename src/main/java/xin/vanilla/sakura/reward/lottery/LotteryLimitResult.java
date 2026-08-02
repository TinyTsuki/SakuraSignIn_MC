package xin.vanilla.sakura.reward.lottery;

import lombok.Value;

@Value
public class LotteryLimitResult {
    boolean allowed;
    long retryAfterSeconds;
    int remainingDraws;
}
