package xin.vanilla.sakura.api.reward.client;

import xin.vanilla.sakura.reward.Reward;

/**
 * 展示层只读取语言、数量模式与原始奖励，不持有界面实现。
 */
public interface RewardDisplayContext {
    Reward reward();

    String languageCode();

    boolean withAmount();
}
