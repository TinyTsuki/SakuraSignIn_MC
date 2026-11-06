package xin.vanilla.sakura.test;

/**
 * 为依赖物品数据组件的纯 JUnit 测试安装最小 NeoForge 加载上下文。
 */
public final class NeoForgeUnitTestBootstrap {
    private NeoForgeUnitTestBootstrap() {
    }

    public static void bootstrap() {
        NativeBootstrap.initialize();
    }
}
