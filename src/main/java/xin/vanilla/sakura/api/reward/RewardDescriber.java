package xin.vanilla.sakura.api.reward;

import xin.vanilla.banira.common.data.Component;

/**
 * 不依赖客户端类的奖励名称描述，供服务端通知与客户端展示复用。
 */
public interface RewardDescriber<T> {

    Component describe(String languageCode, T value, boolean withAmount);
}
