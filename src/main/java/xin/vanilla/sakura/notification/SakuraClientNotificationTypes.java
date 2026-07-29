package xin.vanilla.sakura.notification;

import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.api.client.notification.BaniraClientNotificationTypes;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.EI18nType;

/**
 * 客户端通知元数据只在 client setup 阶段注册。
 */
public final class SakuraClientNotificationTypes {
    private SakuraClientNotificationTypes() {
    }

    public static void register() {
        BaniraClientNotificationTypes.registerModDisplayName(
                SakuraSignIn.MODID,
                translated("mod_name")
        );
        for (String typeId : SakuraNotificationTypes.all()) {
            String leaf = typeId.substring(typeId.lastIndexOf('.') + 1);
            BaniraClientNotificationTypes.register(
                    typeId,
                    SakuraNotificationTypes.defaultDisplay(typeId),
                    translated("notification_type_" + leaf)
            );
        }
    }

    private static Component translated(String key) {
        return BaniraComponent.get().object(
                xin.vanilla.sakura.util.Component
                        .translatableClient(EI18nType.TIPS, key)
                        .toTextComponent()
        );
    }
}
