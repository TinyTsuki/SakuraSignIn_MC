package xin.vanilla.sakura.internal.dev;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class DevFpsTaskBoundaryTest {
    @Test
    public void configurationDoesNotPrepareClientSettings() throws Exception {
        String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
        assertFalse(Pattern.compile("(?m)^prepareBaniraDevFpsOverlay\\(\\)\\s*$").matcher(build).find());
        assertTrue(build.contains("tasks.register('prepareDevFpsOverlay')"));
        assertTrue(build.contains("doLast prepareBaniraDevFpsOverlay"));
    }

    @Test
    public void onlyClientExecutionDependsOnPreparation() throws Exception {
        String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
        assertTrue(Pattern.compile("tasks\\.matching\\s*\\{\\s*it\\.name\\s*==\\s*'runClient'\\s*}\\.configureEach\\s*\\{\\s*dependsOn\\s*'prepareDevFpsOverlay'\\s*}").matcher(build).find());
    }
}
