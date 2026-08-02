package xin.vanilla.sakura.reward.lottery;

import lombok.Value;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.Reward;

import java.util.List;

/** 一次服务端权威连抽的完整结果。 */
@Value
public class LotteryDrawBatchResult {
    LotteryDrawStatus status;
    LotteryPool pool;
    List<Reward> rewards;
    long retryAfterSeconds;
    int remainingDraws;

    public boolean isSuccess() {
        return status == LotteryDrawStatus.SUCCESS && rewards != null && !rewards.isEmpty();
    }
}
