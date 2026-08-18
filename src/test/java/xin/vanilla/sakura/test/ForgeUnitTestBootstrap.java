package xin.vanilla.sakura.test;

import cpw.mods.modlauncher.api.IModuleLayerManager;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Optional;

/**
 * 为依赖物品数据组件的纯 JUnit 测试安装最小 Forge 加载上下文。
 */
public final class ForgeUnitTestBootstrap {
    private static boolean bootstrapped;

    private ForgeUnitTestBootstrap() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        try {
            LoadingModList loadingModList = LoadingModList.of(
                    Collections.emptyList(), Collections.emptyList(), null);
            loadingModList.setBrokenFiles(Collections.emptyList());
            Field field = FMLLoader.class.getDeclaredField("loadingModList");
            field.setAccessible(true);
            field.set(null, loadingModList);
            IModuleLayerManager layerManager = (IModuleLayerManager) Proxy.newProxyInstance(
                    IModuleLayerManager.class.getClassLoader(),
                    new Class<?>[]{IModuleLayerManager.class},
                    (proxy, method, args) -> Optional.of(ModuleLayer.boot()));
            Field layerField = FMLLoader.class.getDeclaredField("moduleLayerManager");
            layerField.setAccessible(true);
            layerField.set(null, layerManager);
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            bootstrapped = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to initialize Forge unit-test registries", exception);
        }
    }
}
