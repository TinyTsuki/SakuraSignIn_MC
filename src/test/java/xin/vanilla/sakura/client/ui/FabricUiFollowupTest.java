package xin.vanilla.sakura.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class FabricUiFollowupTest {
    @Test
    public void validationPublishesTheRemappedSourceArtifact() throws Exception {
        String script = Files.readString(Path.of("gradle/aui-validation-publication.gradle"), StandardCharsets.UTF_8);
        assertTrue(script.contains("artifact tasks.named('remapJar')"));
        assertTrue(script.contains("artifact tasks.named('remapSourcesJar')"));
        assertFalse(script.contains("artifact tasks.named('sourcesJar')"));
    }

    @Test
    public void clientFpsPreparationRemainsDeferred() throws Exception {
        String script = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
        assertFalse(Pattern.compile("(?m)^\\s*prepareBaniraDevFpsOverlay\\(\\)\\s*$").matcher(script).find());
        assertTrue(script.contains("dependsOn 'prepareDevFpsOverlay'"));
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
