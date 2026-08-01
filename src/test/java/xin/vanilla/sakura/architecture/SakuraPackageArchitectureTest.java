package xin.vanilla.sakura.architecture;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 约束 Sakura 的公共包结构和 Banira 工具复用边界。
 */
public class SakuraPackageArchitectureTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");
    private static final Pattern ENUM_DECLARATION = Pattern.compile(
            "\\benum\\s+\\w+([^\\{]*)\\{");

    @Test
    public void obsoletePackagesAndDuplicateUtilitiesAreRemoved() {
        assertFalse(Files.exists(MAIN.resolve("domain")));
        assertFalse(Files.exists(MAIN.resolve("text")));
        assertTrue(Files.exists(MAIN.resolve("SakuraComponent.java")));

        List<String> duplicateUtilities = Arrays.asList(
                "AbstractGuiUtils.java", "CollectionUtils.java", "DateUtils.java",
                "FieldUtils.java", "GLFWKey.java", "StringUtils.java",
                "TextureUtils.java", "EMCColor.java");
        for (String file : duplicateUtilities) {
            assertFalse(file + " should come from Banira",
                    Files.exists(MAIN.resolve("util").resolve(file))
                            || Files.exists(MAIN.resolve("enums").resolve(file)));
        }
        assertFalse(Files.exists(MAIN.resolve("config/KeyValue.java")));
        assertFalse(Files.exists(MAIN.resolve("config/StringList.java")));
        assertFalse(Files.exists(MAIN.resolve("config/ArraySet.java")));
        assertFalse(Files.exists(MAIN.resolve("reward/config")));
    }

    @Test
    public void allEnumsExposeDescriptions() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = read(path);
                Matcher matcher = ENUM_DECLARATION.matcher(source);
                while (matcher.find()) {
                    assertTrue(path + " enum must implement IEnumDescribable",
                            matcher.group(1).contains("IEnumDescribable"));
                }
            });
        }
    }

    @Test
    public void sakuraDoesNotImportItsRemovedUtilityCopies() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source = read(path);
                assertFalse(path + " imports the removed KeyValue",
                        source.contains("xin.vanilla.sakura.config.KeyValue"));
                assertFalse(path + " imports the removed utility package",
                        source.matches("(?s).*import xin\\.vanilla\\.sakura\\.util\\.(AbstractGuiUtils|CollectionUtils|DateUtils|FieldUtils|GLFWKey|StringUtils|TextureUtils);.*"));
            });
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
