package xin.vanilla.sakura.text;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 防止 Sakura 再次维护与 Banira 重复的文本和翻译实现。
 */
public class BaniraTextBoundaryTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void oldTextImplementationsAreRemoved() {
        assertFalse(Files.exists(MAIN.resolve("util/Component.java")));
        assertFalse(Files.exists(MAIN.resolve("util/I18nUtils.java")));
        assertFalse(Files.exists(MAIN.resolve("enums/EI18nType.java")));
        assertFalse(Files.exists(MAIN.resolve("screen/component/Text.java")));
    }

    @Test
    public void productionSourcesUseBaniraTextTypes() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String source = read(path);
                        assertFalse(path + " imports the old component",
                                source.contains("xin.vanilla.sakura.util.Component"));
                        assertFalse(path + " imports the old translator",
                                source.contains("xin.vanilla.sakura.util.I18nUtils"));
                        assertFalse(path + " imports the old i18n enum",
                                source.contains("xin.vanilla.sakura.enums.EI18nType"));
                        assertFalse(path + " imports the old client text",
                                source.contains("xin.vanilla.sakura.screen.component.Text"));
                    });
        }

        String scope = read(MAIN.resolve("text/SakuraComponent.java"));
        assertTrue(scope.contains("extends AbstractComponent"));

        String reward = read(MAIN.resolve("rewards/Reward.java"));
        assertFalse("Common reward model links client text",
                reward.contains("xin.vanilla.banira.client"));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
