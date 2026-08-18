package xin.vanilla.sakura.internal.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import xin.vanilla.sakura.client.SakuraClientBootstrap;

/** Fabric 客户端入口，避免独立服务端加载客户端类型。 */
public final class FabricSakuraClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SakuraClientBootstrap.init();
    }
}
