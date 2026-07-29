package xin.vanilla.sakura.config.access;

import xin.vanilla.banira.common.config.ConfigCategoryViewProxy;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.sakura.config.ClientConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 将 CLIENT 配置路径映射为稳定的分类视图。
 */
public final class ClientConfigAccess {
    private ClientConfigAccess() {
    }

    public static ClientConfig.RootView root(ConfigHolder holder) {
        return (ClientConfig.RootView) Proxy.newProxyInstance(
                ClientConfig.class.getClassLoader(),
                new Class<?>[]{ClientConfig.RootView.class},
                (proxy, method, args) -> handle(proxy, method, args, holder)
        );
    }

    private static Object handle(Object proxy, Method method, Object[] args, ConfigHolder holder) {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args);
        }
        switch (method.getName()) {
            case "display": return view(ClientConfig.DisplayView.class, holder, "display", new ClientConfig.DisplayCategory());
            case "rewardKeys": return view(ClientConfig.RewardKeysView.class, holder, "rewardKeys", new ClientConfig.RewardKeysCategory());
            case "signKeys": return view(ClientConfig.SignKeysView.class, holder, "signKeys", new ClientConfig.SignKeysCategory());
            case "holder": return holder;
            default: throw new UnsupportedOperationException(method.toString());
        }
    }

    private static <T> T view(Class<T> type, ConfigHolder holder, String path, Object defaults) {
        return ConfigCategoryViewProxy.create(type, holder, path, defaults, ClientConfigAccess::normalize);
    }

    private static Object normalize(String name, Object value, Object defaults) throws Exception {
        if (value != null) {
            return value;
        }
        Field field = defaults.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(defaults);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "ClientConfig.RootView@" + System.identityHashCode(proxy);
            default: throw new UnsupportedOperationException(method.toString());
        }
    }
}
