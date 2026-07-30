package xin.vanilla.sakura.test;

import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.banira.platform.BaniraRegistryService;

import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯 JUnit 不执行模组入口，只安装测试所需的最小公共平台注册表。
 */
public final class BaniraTestPlatform {
    private static final Map<Object, String> KEYS = new ConcurrentHashMap<>();
    private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();

    private BaniraTestPlatform() {
    }

    public static void install() {
        BaniraRegistryService registry = (BaniraRegistryService) Proxy.newProxyInstance(
                BaniraRegistryService.class.getClassLoader(),
                new Class<?>[]{BaniraRegistryService.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.endsWith("Key")) {
                        return args[0] != null ? KEYS.get(args[0]) : null;
                    }
                    if ("item".equals(name) || "effect".equals(name)
                            || "block".equals(name) || "entityType".equals(name)
                            || "biome".equals(name)) {
                        return args[0] != null ? VALUES.get(String.valueOf(args[0])) : null;
                    }
                    return Collections.emptyList();
                }
        );
        BaniraPlatform platform = (BaniraPlatform) Proxy.newProxyInstance(
                BaniraPlatform.class.getClassLoader(),
                new Class<?>[]{BaniraPlatform.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "registryService":
                            return registry;
                        case "loaderType":
                            return "test";
                        case "minecraftVersion":
                            return "1.16.5";
                        case "configDir":
                            return Paths.get("build", "test-config");
                        case "modDisplayName":
                            return args[0];
                        case "modMainClass":
                            return Object.class;
                        case "modIdFromMainClass":
                            return "sakura_sign_in";
                        case "isClient":
                        case "isDedicatedServer":
                        case "isDevelopment":
                        case "isModLoaded":
                            return false;
                        case "lastKnownUsername":
                            return null;
                        default:
                            throw new UnsupportedOperationException(
                                    "Test platform does not provide " + method.getName());
                    }
                }
        );
        BaniraPlatforms.installIfAbsent(platform);
    }

    public static void register(String id, Object value) {
        KEYS.put(value, id);
        VALUES.put(id, value);
    }
}
