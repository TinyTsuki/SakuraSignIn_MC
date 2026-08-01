package xin.vanilla.sakura.api.reward.client;

public interface RewardEditorProvider<T> {
    void open(RewardEditContext<T> context);
}
