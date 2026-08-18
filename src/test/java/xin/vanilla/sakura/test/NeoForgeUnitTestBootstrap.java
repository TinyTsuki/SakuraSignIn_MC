package xin.vanilla.sakura.test;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

/**
 * 为依赖物品数据组件的纯 JUnit 测试安装最小 NeoForge 加载上下文。
 */
public final class NeoForgeUnitTestBootstrap {
    private static boolean bootstrapped;

    private NeoForgeUnitTestBootstrap() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        try {
            LoadingModList loadingModList = LoadingModList.of(
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Map.of());
            Field field = FMLLoader.class.getDeclaredField("loadingModList");
            field.setAccessible(true);
            field.set(null, loadingModList);
            Field layerField = FMLLoader.class.getDeclaredField("gameLayer");
            layerField.setAccessible(true);
            layerField.set(null, ModuleLayer.boot());
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            bootstrapped = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize NeoForge unit-test registries", exception);
        }
    }
}
