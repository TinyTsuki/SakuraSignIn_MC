package xin.vanilla.sakura.config;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientShortcutConfigContractTest {
    @Test
    public void configurableActionsUseKeyCaptureAndDoNotExposeMouseLeft() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/config/ClientConfig.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("@ConfigEntry.Gui.KeyChords"));
        assertFalse(source.contains("GLFW_MOUSE_BUTTON_LEFT"));
        assertFalse(source.matches("(?s).*private\\s+.*mouseLeft.*"));
    }
}
