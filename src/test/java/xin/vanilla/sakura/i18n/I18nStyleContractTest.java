package xin.vanilla.sakura.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 两种语言必须使用相同键集合，说明文本不保留多余句尾标点。
 */
public class I18nStyleContractTest {
    private static final Path LANG = Paths.get("src/main/resources/assets/sakura_sign_in/lang");
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");
    private static final Pattern LEGACY_SECTION = Pattern.compile(
            "SakuraComponent\\.get\\(\\)\\.trans(?:Client)?\\s*\\("
                    + "(?:(?!\\);).){0,300}?\"(?:title|tips|option|message|command)\"",
            Pattern.DOTALL);
    private static final Pattern LEGACY_FULL_KEY = Pattern.compile(
            "\"(?:title|tips|option|message|command)\\.sakura_sign_in\\.");
    private static final Pattern STATIC_TEXT_KEY = Pattern.compile(
            "Text\\.trans\\s*\\(\\s*SakuraSignIn\\.MODID\\s*,\\s*\"([^\"]+)\"\\s*(?=[,)])");
    private static final Pattern STATIC_COMPONENT_KEY = Pattern.compile(
            "SakuraComponent\\.get\\(\\)\\.(?:trans|transClient|translateClient|transLang)\\s*\\("
                    + "(?:(?!\\);).){0,300}?\"(key|word|format)\"\\s*,\\s*\"([^\"]+)\"\\s*(?=[,)])",
            Pattern.DOTALL);

    @Test
    public void languageFilesHaveMatchingKeysAndNoTerminalPunctuation() throws Exception {
        JsonObject english = read("en_us.json");
        JsonObject chinese = read("zh_cn.json");

        assertEquals(keys(english), keys(chinese));
        assertNoTerminalPunctuation(english);
        assertNoTerminalPunctuation(chinese);
        assertUsesUnifiedKeyTypes(english);
        assertUsesUnifiedKeyTypes(chinese);
        assertEquals("樱花签", chinese.get("key.sakura_sign_in.categories").getAsString());
    }

    @Test
    public void productionSourcesDoNotUseLegacyI18nSections() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    assertFalse(path + " uses a legacy Sakura i18n section",
                            LEGACY_SECTION.matcher(source).find());
                    assertFalse(path + " uses a legacy full Sakura i18n key",
                            LEGACY_FULL_KEY.matcher(source).find());
                } catch (Exception exception) {
                    throw new AssertionError("Unable to read " + path, exception);
                }
            });
        }
    }

    @Test
    public void staticProductionTranslationKeysExist() throws Exception {
        Set<String> translations = keys(read("zh_cn.json"));
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    assertStaticKeysExist(path, source, translations);
                } catch (Exception exception) {
                    throw new AssertionError("Unable to read " + path, exception);
                }
            });
        }
    }

    private static void assertStaticKeysExist(Path path, String source, Set<String> translations) {
        Matcher textMatcher = STATIC_TEXT_KEY.matcher(source);
        while (textMatcher.find()) {
            assertTrue(path + " references missing i18n key " + textMatcher.group(1),
                    translations.contains(textMatcher.group(1)));
        }
        Matcher componentMatcher = STATIC_COMPONENT_KEY.matcher(source);
        while (componentMatcher.find()) {
            String key = componentMatcher.group(1) + ".sakura_sign_in."
                    + componentMatcher.group(2);
            assertTrue(path + " references missing i18n key " + key,
                    translations.contains(key));
        }
    }

    private static void assertUsesUnifiedKeyTypes(JsonObject translations) {
        for (String key : keys(translations)) {
            assertTrue("Unsupported i18n key type: " + key,
                    key.startsWith("key.sakura_sign_in.")
                            || key.startsWith("word.sakura_sign_in.")
                            || key.startsWith("format.sakura_sign_in."));
        }
    }

    private static void assertNoTerminalPunctuation(JsonObject translations) {
        for (Map.Entry<String, JsonElement> entry : translations.entrySet()) {
            String value = entry.getValue().getAsString();
            assertFalse(entry.getKey() + " ends with punctuation: " + value,
                    value.matches("(?s).*[。！？.!?]$"));
        }
    }

    private static Set<String> keys(JsonObject translations) {
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : translations.entrySet()) {
            keys.add(entry.getKey());
        }
        return keys;
    }

    private static JsonObject read(String name) throws Exception {
        try (Reader reader = Files.newBufferedReader(LANG.resolve(name), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }
}
