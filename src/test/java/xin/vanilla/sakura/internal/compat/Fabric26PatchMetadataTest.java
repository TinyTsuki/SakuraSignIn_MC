package xin.vanilla.sakura.internal.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class Fabric26PatchMetadataTest {
    private JsonObject metadata() throws Exception {
        try (var input = getClass().getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(input);
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals("sakura_sign_in", root.get("id").getAsString());
                return root;
            }
        }
    }

    private boolean accepts(JsonArray predicates, String version) throws Exception {
        for (var predicate : predicates) {
            if (VersionPredicate.parse(predicate.getAsString()).test(Version.parse(version))) return true;
        }
        return false;
    }

    @Test
    public void compiledMetadataAllowsTheThreeReleasedPatches() throws Exception {
        var versions = metadata().getAsJsonObject("depends").getAsJsonArray("minecraft");
        assertEquals(List.of("26.1", "26.1.1", "26.1.2"),
                versions.asList().stream().map(value -> value.getAsString()).toList());
        for (String version : List.of("26.1", "26.1.1", "26.1.2")) assertTrue(version, accepts(versions, version));
    }

    @Test
    public void futurePatchesSnapshotsAndLaterDropsRemainRejected() throws Exception {
        var versions = metadata().getAsJsonObject("depends").getAsJsonArray("minecraft");
        for (String version : List.of("26.1.3", "26.1.99", "26.1.2-rc.1", "26.2", "26.3", "1.21.1")) {
            assertFalse(version, accepts(versions, version));
        }
    }

    @Test
    public void baniraRemainsRequiredAndTheClientUiEngineRemainsOptional() throws Exception {
        var root = metadata();
        var dependencies = root.getAsJsonObject("depends");
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String baniraRange = dependencies.get("banira_codex").getAsString();
        assertEquals(properties.getProperty("banira_version_range"), baniraRange);
        assertTrue(VersionPredicate.parse(baniraRange).test(Version.parse(properties.getProperty("banira_version"))));
        assertEquals(">=25", dependencies.get("java").getAsString());
        assertFalse(dependencies.has("apricityui"));
        assertTrue(root.getAsJsonObject("entrypoints").has("main"));
        assertTrue(root.getAsJsonObject("entrypoints").has("client"));
    }
}
