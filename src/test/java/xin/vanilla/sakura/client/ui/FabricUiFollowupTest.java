package xin.vanilla.sakura.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class FabricUiFollowupTest {
    @Test
    public void validationPublishesTheUnobfuscatedArchives() throws Exception {
        String script = Files.readString(Path.of("gradle/aui-validation-publication.gradle"), StandardCharsets.UTF_8);
        assertTrue(script.contains("artifact tasks.named('jar')"));
        assertTrue(script.contains("artifact tasks.named('sourcesJar')"));
        assertFalse(script.contains("artifact tasks.named('remapJar')"));
    }


    @Test
    public void bothIconLicenseTextsArePackaged() throws Exception {
        for (String name : new String[]{"lucide.txt", "reward-editor-lucide.txt"}) {
            try (var input = getClass().getResourceAsStream("/META-INF/licenses/" + name)) {
                assertNotNull(name, input);
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.contains("Lucide"));
                assertTrue(text.contains("Copyright"));
                assertTrue(text.contains("Permission"));
            }
        }
    }

    @Test
    public void publicationRetainsItsIsolationGuard() throws Exception {
        String script = Files.readString(Path.of("gradle/aui-validation-publication.gradle"), StandardCharsets.UTF_8);
        assertTrue(script.stripLeading().startsWith("if (providers.gradleProperty('banira.validationOutputRoot').isPresent()) {"));
        assertTrue(script.contains("pom.withXml"));
        assertTrue(script.contains("Unsafe validation Maven root"));
    }
}
