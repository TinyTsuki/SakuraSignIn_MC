package xin.vanilla.sakura.config.access;

import xin.vanilla.banira.common.config.ConfigCategoryViewProxy;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.sakura.config.CommonConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 将 COMMON 配置路径映射为稳定的分类视图。
 */
public final class CommonConfigAccess {
    private CommonConfigAccess() {
    }

    public static CommonConfig.RootView root(ConfigHolder holder) {
        return (CommonConfig.RootView) Proxy.newProxyInstance(
                CommonConfig.class.getClassLoader(),
                new Class<?>[]{CommonConfig.RootView.class},
                (proxy, method, args) -> handle(proxy, method, args, holder)
        );
    }

    private static Object handle(Object proxy, Method method, Object[] args, ConfigHolder holder) {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args);
        }
        switch (method.getName()) {
            case "makeUp": return view(CommonConfig.MakeUpView.class, holder, "makeUp", new CommonConfig.MakeUpCategory());
            case "cooling": return view(CommonConfig.CoolingView.class, holder, "cooling", new CommonConfig.CoolingCategory());
            case "dateTime": return view(CommonConfig.DateTimeView.class, holder, "dateTime", new CommonConfig.DateTimeCategory());
            case "reward": return view(CommonConfig.RewardView.class, holder, "reward", new CommonConfig.RewardCategory());
            case "server": return view(CommonConfig.ServerView.class, holder, "server", new CommonConfig.ServerCategory());
            case "history": return view(CommonConfig.HistoryView.class, holder, "history", new CommonConfig.HistoryCategory());
            case "command": return view(CommonConfig.CommandView.class, holder, "command", new CommonConfig.CommandCategory());
            case "concise": return view(CommonConfig.ConciseView.class, holder, "concise", new CommonConfig.ConciseCategory());
            case "permission": return view(CommonConfig.PermissionView.class, holder, "permission", new CommonConfig.PermissionCategory());
            case "holder": return holder;
            default: throw new UnsupportedOperationException(method.toString());
        }
    }

    private static <T> T view(Class<T> type, ConfigHolder holder, String path, Object defaults) {
        return ConfigCategoryViewProxy.create(type, holder, path, defaults, CommonConfigAccess::normalize);
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
            case "toString": return "CommonConfig.RootView@" + System.identityHashCode(proxy);
            default: throw new UnsupportedOperationException(method.toString());
        }
    }
}
