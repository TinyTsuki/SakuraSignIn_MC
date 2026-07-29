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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * 两种语言必须使用相同键集合，说明文本不保留多余句尾标点。
 */
public class I18nStyleContractTest {
    private static final Path LANG = Paths.get("src/main/resources/assets/sakura_sign_in/lang");

    @Test
    public void languageFilesHaveMatchingKeysAndNoTerminalPunctuation() throws Exception {
        JsonObject english = read("en_us.json");
        JsonObject chinese = read("zh_cn.json");

        assertEquals(keys(english), keys(chinese));
        assertNoTerminalPunctuation(english);
        assertNoTerminalPunctuation(chinese);
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
