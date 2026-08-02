package xin.vanilla.sakura.reward.lottery;

import lombok.Value;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.Reward;

@Value
public class LotteryDrawResult {
    LotteryDrawStatus status;
    LotteryPool pool;
    Reward reward;
    long retryAfterSeconds;
    int remainingDraws;

    public boolean isSuccess() {
        return status == LotteryDrawStatus.SUCCESS;
    }
}
