package xin.vanilla.sakura;

import lombok.NonNull;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.common.data.AbstractComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumI18nType;

/**
 * Sakura 的 Banira 文本入口，仅负责绑定模组命名空间。
 */
public final class SakuraComponent extends AbstractComponent {
    private static final SakuraComponent INSTANCE = new SakuraComponent();

    private SakuraComponent() {
    }

    public static SakuraComponent get() {
        return INSTANCE;
    }

    @Override
    protected @NonNull String modId() {
        return SakuraSignIn.MODID;
    }

    public Component trans(String section, String key, Object... args) {
        return super.trans(EnumI18nType.NONE, key(section, key), args);
    }

    public Component trans(ServerPlayer player, String section, String key, Object... args) {
        return super.trans(player, EnumI18nType.NONE, key(section, key), args);
    }

    public Component transClient(String section, String key, Object... args) {
        return super.transClient(EnumI18nType.NONE, key(section, key), args);
    }

    public String translateClient(String section, String key, Object... args) {
        return transClient(section, key, args).toString();
    }

    public Component transLang(String languageCode, String section, String key, Object... args) {
        return super.transLang(languageCode, EnumI18nType.NONE, key(section, key), args);
    }

    public static String key(String section, String key) {
        if ("none".equalsIgnoreCase(section) || section == null || section.isEmpty()) {
            return key;
        }
        return section.toLowerCase() + "." + SakuraSignIn.MODID + "." + key;
    }
}
