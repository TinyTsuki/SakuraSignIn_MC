package xin.vanilla.sakura.i18n;

import org.junit.Test;
import xin.vanilla.banira.common.util.ITranslator;
import xin.vanilla.banira.common.util.Translator;
import xin.vanilla.sakura.SakuraLang;
import xin.vanilla.sakura.SakuraSignIn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SakuraBaniraLanguageIntegrationTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void languageEntryMatchesOtherBaniraSubmods() throws Exception {
        Path languagePath = MAIN.resolve("SakuraLang.java");
        assertTrue(Files.exists(languagePath));

        String entrypoint = read(MAIN.resolve("SakuraSignIn.java"));
        String languageEntry = read(languagePath);
        String languageCommand = read(MAIN.resolve("command/impl/LanguageCommand.java"));
        String config = read(MAIN.resolve("config/CommonConfig.java"));
        String utilities = read(MAIN.resolve("util/SakuraUtils.java"));

        assertTrue(languageEntry.contains("class SakuraLang extends Translator"));
        assertTrue(languageEntry.contains("super(SakuraSignIn.MODID, SakuraSignIn.class)"));
        assertTrue(languageEntry.contains("registerInCache()"));
        assertTrue(languageEntry.contains("public static SakuraLang get()"));
        assertTrue(languageEntry.contains("return BaniraCommonSettings.defaultLanguage()"));
        assertFalse(entrypoint.contains("getI18nFiles()"));
        assertTrue(languageCommand.contains("SakuraLang.get().getI18nFiles()"));
        assertFalse(languageCommand.contains("Translator.of(SakuraSignIn.MODID)"));
        assertFalse(config.contains("defaultLanguage"));
        assertTrue(utilities.contains("return Translator.getValidLanguage(player, language)"));
    }

    @Test
    public void baniraTranslatorDiscoversAndResolvesSakuraLanguages() {
        ITranslator translator = SakuraLang.get();

        assertSame(translator, Translator.of(SakuraSignIn.MODID));
        assertTrue(translator.getI18nFiles().contains("en_us"));
        assertTrue(translator.getI18nFiles().contains("zh_cn"));
        assertEquals("樱花签", translator.getTranslation("key.sakura_sign_in.categories", "zh_cn"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
