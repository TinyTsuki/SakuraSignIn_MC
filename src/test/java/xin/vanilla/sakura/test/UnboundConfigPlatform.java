package xin.vanilla.sakura.test;

import org.junit.rules.ExternalResource;
import xin.vanilla.banira.platform.BaniraConfigService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;

import java.lang.reflect.Proxy;

/** Exercises configured DEFAULTS behavior without registering a fake native mod. */
public final class UnboundConfigPlatform extends ExternalResource {
    private BaniraPlatform previous;
    @Override protected void before() {
        BaniraTestPlatform.install(); previous = BaniraPlatforms.get();
        BaniraConfigService service = (BaniraConfigService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BaniraConfigService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("handle")) return null;
                    throw new UnsupportedOperationException(method.toString());
                });
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("configService")) return service;
                    return method.invoke(previous, args);
                }));
    }
    @Override protected void after() { BaniraPlatforms.install(previous); }
}
