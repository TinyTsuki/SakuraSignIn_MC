package xin.vanilla.sakura.data.lottery;

import xin.vanilla.sakura.reward.RewardList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 奖池复制和查找集中在一处，避免配置、网络与界面共享可变引用。 */
public final class LotteryPools {
    private LotteryPools() {
    }

    public static List<LotteryPool> copy(List<LotteryPool> pools) {
        List<LotteryPool> result = new ArrayList<>();
        if (pools != null) {
            pools.stream().filter(java.util.Objects::nonNull)
                    .map(LotteryPools::copy)
                    .forEach(result::add);
        }
        return result;
    }

    public static LotteryPool copy(LotteryPool pool) {
        return new LotteryPool(pool.getId(), pool.getDisplayName(), pool.getLimitPolicy(),
                pool.getMaxDraws(), pool.getCooldownSeconds(),
                new RewardList(pool.getRewards()).clone());
    }

    public static Optional<LotteryPool> find(List<LotteryPool> pools, String id) {
        if (pools == null || id == null) {
            return Optional.empty();
        }
        return pools.stream().filter(pool -> pool != null && id.equals(pool.getId())).findFirst();
    }
}
