package xin.vanilla.sakura.api.reward.client;

/**
 * 跨版本稳定的奖励图标绘制能力，由当前分支客户端适配原生渲染参数。
 */
public interface RewardRenderContext extends RewardDisplayContext {
    int x();

    int y();

    int size();

    void drawItem(Object itemStack);

    void drawEffect(Object effectInstance);

    void drawBuiltInIcon(String iconId);

    void drawAmount(String text);

    void drawPlaceholder(String typeId);
}
