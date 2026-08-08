package xin.vanilla.sakura;

import xin.vanilla.banira.common.util.Translator;

/** 将语言资源锚定到 Sakura 自身 JAR，避免生产环境只枚举到默认语言。 */
public final class SakuraLang extends Translator {
    private static final SakuraLang INSTANCE = new SakuraLang();

    private SakuraLang() {
        super(SakuraSignIn.MODID, SakuraSignIn.class);
        registerInCache();
    }

    public static void initialize() {
        // 触发静态实例注册即可。
    }
}
