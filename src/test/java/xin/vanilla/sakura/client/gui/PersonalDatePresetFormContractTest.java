package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 个性化日期的计数值应直接输入，并保留可翻译历法标签。 */
public class PersonalDatePresetFormContractTest {
    @Test
    public void dateCountsAreValidatedInputsRatherThanFixedDropdowns() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/client/gui/PersonalDatePresetForm.java")),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("numberWidget(\"slots\""));
        assertTrue(source.contains("numberWidget(\"before\""));
        assertTrue(source.contains("numberWidget(\"after\""));
        assertTrue(source.contains(".tooltip(text(titleKey + \"_tooltip\"))"));
        assertFalse(source.contains("dropdownValues("));
    }
}
