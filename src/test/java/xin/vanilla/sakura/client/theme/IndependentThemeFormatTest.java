package xin.vanilla.sakura.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;

public class IndependentThemeFormatTest {
    private JsonObject descriptor(String id) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("assets/sakura_sign_in/themes/" + id + ".json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    public void packagedThemesUseFormatTwo() throws Exception {
        for (String id : BuiltInThemeCatalog.themeIds()) assertEquals(id, 2, descriptor(id).get("format").getAsInt());
    }

    @Test
    public void newConfigurationContainsNoAtlasOrUvCoordinates() throws Exception {
        for (String id : BuiltInThemeCatalog.themeIds()) {
            JsonObject json = descriptor(id);
            assertFalse(json.has("texture"));
            assertFalse(json.has("coordinates"));
            assertTrue(json.has("layout"));
            assertTrue(json.has("textures"));
            assertNoUv(json);
        }
    }

    private void assertNoUv(JsonElement value) {
        if (value.isJsonObject()) for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) {
            assertFalse(e.getKey(), e.getKey().endsWith("UV") || Arrays.asList("u0", "v0", "uWidth", "vHeight", "totalWidth", "totalHeight").contains(e.getKey()));
            assertNoUv(e.getValue());
        }
    }

    @Test
    public void allTextureReferencesResolveToCompleteStandalonePngFiles() throws Exception {
        for (String id : BuiltInThemeCatalog.themeIds()) {
            JsonObject json = descriptor(id);
            assertNotNull("Independent texture map", json.get("textures"));
            assertTrue(json.getAsJsonObject("textures").size() >= 32);
            String root = json.get("textureRoot").getAsString().replace(':', '/');
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                assertTrue(e.getValue().isJsonPrimitive());
                String path = e.getValue().getAsString();
                assertTrue(path.endsWith(".png"));
                assertFalse(path.contains(".."));
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("assets/" + root + path)) {
                    assertNotNull(id + ":" + path, in);
                    assertNotNull(ImageIO.read(in));
                }
            }
        }
    }

    @Test
    public void legacyPixelsRemainAvailableOnlyAsTestGoldens() throws Exception {
        for (String id : BuiltInThemeCatalog.themeIds())
            try (InputStream in = getClass().getResourceAsStream("/theme-migration/" + id + ".png")) {
                assertNotNull(in);
                assertNotNull(ImageIO.read(in));
            }
    }
}
