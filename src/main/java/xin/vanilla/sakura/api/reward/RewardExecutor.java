package xin.vanilla.sakura.api.reward;

public interface RewardExecutor<T> {

    RewardGrantResult grant(RewardGrantContext context, T value);
}
