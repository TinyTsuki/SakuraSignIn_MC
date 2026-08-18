package xin.vanilla.sakura;

import lombok.NonNull;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.banira.api.BaniraCommonSettings;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.ScopedComponent;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.util.Translator;

/**
 * 樱花签语言入口，统一绑定 Banira 翻译器与本模组资源。
 */
public final class SakuraLang extends Translator {

    public static final SakuraLang INSTANCE = new SakuraLang();

    private SakuraLang() {
        // Sakura 在加载器入口构造阶段初始化，显式身份可避免依赖平台反查时机。
        super(SakuraSignIn.MODID, SakuraSignIn.class);
        registerInCache();
    }

    public static SakuraLang get() {
        return INSTANCE;
    }

    public static boolean hasTranslation(@NonNull EnumI18nType type, @NonNull String key) {
        return INSTANCE.hasTranslation(type, key, Translator.getClientLanguage());
    }

    public static String getClientLanguage() {
        return Translator.getClientLanguage();
    }

    public static String getServerLanguage() {
        return BaniraCommonSettings.defaultLanguage();
    }

    public static String getServerPlayerLanguage(ServerPlayer player) {
        return Translator.getServerPlayerLanguage(player);
    }

    public static Component transLangAuto(String languageCode, String key, Object... args) {
        if (args == null || args.length == 0) {
            return new ScopedComponent(SakuraSignIn.MODID)
                    .transLang(languageCode, EnumI18nType.WORD, key);
        }
        return new ScopedComponent(SakuraSignIn.MODID)
                .transLang(languageCode, EnumI18nType.FORMAT, key, args);
    }
}
