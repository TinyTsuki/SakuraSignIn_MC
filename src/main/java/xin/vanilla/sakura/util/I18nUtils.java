package xin.vanilla.sakura.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.EI18nType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class I18nUtils {
    private static final Map<String, JsonObject> LANGUAGES = new HashMap<>();
    private static final String DEFAULT_LANGUAGE = SakuraSignIn.DEFAULT_LANGUAGE;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String LANG_PATH = String.format("/assets/%s/lang/", SakuraSignIn.MODID);
    private static final String LANG_FILE_PATH = String.format("%s%%s.json", LANG_PATH);

    static {
        loadLanguage(DEFAULT_LANGUAGE);
        getI18nFiles().forEach(I18nUtils::loadLanguage);
    }

    /**
     * 加载语言文件
     */
    public static void loadLanguage(@NonNull String languageCode) {
        languageCode = languageCode.toLowerCase(Locale.ROOT);
        if (!LANGUAGES.containsKey(languageCode)) {
            try {
                try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(I18nUtils.class.getResourceAsStream(String.format(LANG_FILE_PATH, languageCode))), StandardCharsets.UTF_8)) {
                    JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
                    LANGUAGES.put(languageCode, jsonObject);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load language file: {}", languageCode, e);
            }
        }
    }

    /**
     * 获取翻译文本
     */
    public static String getTranslationClient(@NonNull EI18nType type, @NonNull String key) {
        return getTranslation(getKey(type, key), SakuraUtils.getClientLanguage());
    }

    /**
     * 获取翻译文本
     */
    public static String getTranslation(@NonNull EI18nType type, @NonNull String key, @NonNull String languageCode) {
        return getTranslation(getKey(type, key), languageCode);
    }

    /**
     * 获取翻译文本
     */
    public static String getTranslation(@NonNull String key, @NonNull String languageCode) {
        languageCode = languageCode.toLowerCase(Locale.ROOT);
        JsonObject language = LANGUAGES.getOrDefault(languageCode, LANGUAGES.get(DEFAULT_LANGUAGE));
        if (language != null && language.has(key)) {
            return language.get(key).getAsString();
        }
        return key;
    }

    public static String getKey(@NonNull EI18nType type, @NonNull String key) {
        String result;
        if (type == EI18nType.PLAIN || type == EI18nType.NONE) {
            result = key;
        } else {
            result = String.format("%s.%s.%s", type.name().toLowerCase(), SakuraSignIn.MODID, key);
        }
        return result;
    }

    public static Component enabled(@NonNull String languageCode, boolean enabled) {
        return Component.translatable(languageCode, EI18nType.WORD, enabled ? "enabled" : "disabled");
    }

    /**
     * 获取I18n文件列表
     */
    public static List<String> getI18nFiles() {
        List<String> result = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(I18nUtils.class.getResourceAsStream(LANG_PATH + "0_i18n_files.txt")),
                StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                // 将每一行添加到列表中
                if (StringUtils.isNotNullOrEmpty(line))
                    result.add(line);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get I18n file name list", e);
        }
        return result;
    }
}
