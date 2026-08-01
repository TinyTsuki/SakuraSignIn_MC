package xin.vanilla.sakura.api.reward;

/**
 * 测试侧重置入口，避免生产 API 暴露可变注册表操作。
 */
public final class RewardRegistryTestSupport {
    private RewardRegistryTestSupport() {
    }

    public static void reset() {
        SakuraRewards.clearForTests();
    }
}
