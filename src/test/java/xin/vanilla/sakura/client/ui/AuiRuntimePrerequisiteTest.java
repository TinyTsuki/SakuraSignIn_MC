package xin.vanilla.sakura.client.ui;

import org.junit.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuiRuntimePrerequisiteTest {
    @Test
    public void devFpsIsPreparedOnlyByClientTask() throws Exception {
        String script = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
        assertTrue(script.contains("tasks.named('runClient').configure { dependsOn 'prepareDevFpsOverlay' }"));
        assertTrue(script.contains("doLast prepareBaniraDevFpsOverlay"));
        assertFalse(script.contains("orNull == null) prepareBaniraDevFpsOverlay()"));
        assertFalse(script.matches("(?s).*(?m)^\\s*prepareBaniraDevFpsOverlay\\(\\)\\s*$.*"));
    }

    @Test
    public void officialEnginePrerequisitesRemainAvailable() throws Exception {
        Properties properties = new Properties();
        try (Reader input = Files.newBufferedReader(Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(input);
        }
        assertEquals("0.16.14", properties.getProperty("loader_version"));
        assertEquals("0.92.9+1.20.1", properties.getProperty("fabric_version"));
        assertEquals("17", properties.getProperty("fabric.loom.runtimeJavaCompatibilityVersion"));
        assertEquals("61.0", System.getProperty("java.class.version"));
    }
}
