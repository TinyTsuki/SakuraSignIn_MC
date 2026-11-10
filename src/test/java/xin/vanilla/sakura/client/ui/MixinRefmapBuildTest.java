package xin.vanilla.sakura.client.ui;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.*;

public class MixinRefmapBuildTest {


    @Test
    public void missingAndEmptyGeneratedMappingsAreRejected() {
        var config = "{\"refmap\":\"example.refmap.json\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(AssertionError.class, () -> verify("example", Map.of("example.mixins.json", config)));
        assertThrows(AssertionError.class, () -> verify("example", Map.of("example.mixins.json", config,
                "example.refmap.json", "{\"mappings\":{}}".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void matchingGeneratedMappingsAreAccepted() {
        verify("example", Map.of("example.mixins.json", "{\"refmap\":\"example.refmap.json\"}".getBytes(StandardCharsets.UTF_8),
                "example.refmap.json", "{\"mappings\":{\"Mixin\":{\"field\":\"mapped\"}}}".getBytes(StandardCharsets.UTF_8)));
    }

    static void verify(String id, Map<String, byte[]> files) {
        assertNotNull("Missing Mixin configuration", files.get(id + ".mixins.json"));
        var config = JsonParser.parseString(new String(files.get(id + ".mixins.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(id + ".refmap.json", config.get("refmap").getAsString());
        assertNotNull("Missing generated Mixin refmap", files.get(id + ".refmap.json"));
        var refmap = JsonParser.parseString(new String(files.get(id + ".refmap.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue("Empty generated Mixin mappings", refmap.getAsJsonObject("mappings").size() > 0);
    }
}
