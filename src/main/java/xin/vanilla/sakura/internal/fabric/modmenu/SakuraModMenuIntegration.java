package xin.vanilla.sakura.internal.fabric.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.sakura.config.ClientConfig;

/** 将 Mod Menu 的设置按钮连接到樱花签客户端配置。 */
public final class SakuraModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigEditorScreen(
                xin.vanilla.banira.api.BaniraConfigs.holder(ClientConfig.class),
                new ConfigEditorScreen.Args().parentScreen(parent));
    }
}
