package xin.vanilla.sakura.client.theme;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class BuiltInThemeCatalogTest {
    @Test
    public void loadsEveryPackagedThemeByStableId() throws Exception {
        assertEquals(List.of("original", "sakura", "clover", "maple", "chaos"), BuiltInThemeCatalog.themeIds());
        for (String id : BuiltInThemeCatalog.themeIds()) {
            var theme = BuiltInThemeCatalog.load(id);
            assertEquals(2, theme.getFormat());
            assertEquals(id, theme.getId());
            assertEquals(!id.equals("chaos"), theme.getLayout().isSpecial());
            assertEquals(BuiltInThemeCatalog.keys(theme.getLayout().isSpecial()), theme.getTextures().keySet());
            assertNull(getClass().getClassLoader().getResource("assets/sakura_sign_in/textures/gui/sign_in_calendar_" + id + ".png"));
            for (var texture : theme.getResolved().values()) {
                var image = image(texture.location());
                assertEquals(image.getWidth(), texture.width());
                assertEquals(image.getHeight(), texture.height());
                assertEquals(0, texture.texture().u0());
                assertEquals(0, texture.texture().v0());
                assertEquals(texture.width(), texture.texture().uWidth());
                assertEquals(texture.height(), texture.texture().vHeight());
            }
        }
    }

    @Test
    public void fallsBackToSakuraAndReturnsFreshCoordinates() {
        assertEquals("sakura", BuiltInThemeCatalog.load("../external.png").getId());
        var first = BuiltInThemeCatalog.load("original");
        var second = BuiltInThemeCatalog.load("original");
        assertNotSame(first.getLayout(), second.getLayout());
        first.getLayout().getCellCoordinate().setX(320);
        assertNotEquals(320, second.getLayout().getCellCoordinate().getX(), 0);
        assertThrows(UnsupportedOperationException.class, () -> first.getTextures().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.getResolved().clear());
    }

    @Test
    public void incompleteResourcePackFallsBackToTheRequestedPackagedTheme() {
        var manager = (net.minecraft.server.packs.resources.ResourceManager) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{net.minecraft.server.packs.resources.ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) return Optional.empty();
                    throw new UnsupportedOperationException(method.getName());
                });
        for (String id : BuiltInThemeCatalog.themeIds()) {
            var theme = BuiltInThemeCatalog.load(id, manager);
            assertEquals(id, theme.getId());
            var original = BuiltInThemeCatalog.load(id).texture("background.calendar");
            var fallback = theme.texture("background.calendar");
            assertNotEquals(original.location(), fallback.location());
            assertTrue(fallback.location().getPath().startsWith("runtime/themes/"));
            assertTrue(fallback.requiresUpload());
            try (var expected = original.openPng(); var actual = fallback.openPng()) {
                assertArrayEquals(expected.readAllBytes(), actual.readAllBytes());
            } catch (IOException error) {
                throw new AssertionError(error);
            }
            for (int y = 0; y < original.height(); y++)
                for (int x = 0; x < original.width(); x++)
                    assertEquals(original.opaqueAt(x, y), fallback.opaqueAt(x, y));
        }
    }

    @Test
    public void everyStandalonePixelMatchesTheOriginalAtlas() throws Exception {
        int checked = 0;
        Map<String, String> roles = Map.ofEntries(
                Map.entry("background.calendar", "bgUV"), Map.entry("background.tooltip", "tooltipUV"),
                Map.entry("background.editor", "optionBgUV"), Map.entry("icon.info", "signInInfoUV"),
                Map.entry("icon.sign_in", "signInBtnUV"), Map.entry("icon.reward_editor", "rewardOptionBtnUV"),
                Map.entry("status.rewarded", "rewardedUV"), Map.entry("status.not_signed_in", "notSignedInUV"),
                Map.entry("status.signed_in", "signedInUV"));
        Map<String, String> states = Map.of("normal", "", "hover", "Hover", "pressed", "Tap");
        for (String id : BuiltInThemeCatalog.themeIds()) {
            JsonObject old = json("theme-migration/" + id + ".json").getAsJsonObject("coordinates");
            BufferedImage atlas;
            try (var in = resource("theme-migration/" + id + ".png")) {
                atlas = ImageIO.read(in);
            }
            var theme = BuiltInThemeCatalog.load(id);
            assertEquals(old.getAsJsonObject("bgUV").get("uWidth").getAsInt(), theme.getLayout().getWidth());
            assertEquals(old.getAsJsonObject("bgUV").get("vHeight").getAsInt(), theme.getLayout().getHeight());
            for (String key : theme.getTextures().keySet()) {
                String field;
                int offset = 0;
                if (key.startsWith("selector.")) {
                    String[] parts = key.split("\\.");
                    field = "theme" + states.get(parts[2]) + "UV";
                    offset = BuiltInThemeCatalog.themeIds().indexOf(parts[1]) * old.getAsJsonObject("themeUV").get("uWidth").getAsInt();
                } else if (key.startsWith("button.arrow.")) {
                    field = "arrow" + states.get(key.substring("button.arrow.".length())) + "UV";
                } else if (key.startsWith("reward.")) {
                    field = key.substring(7) + "UV";
                } else {
                    String role = key.replace(".normal", "").replace(".special", "");
                    field = roles.get(role);
                    if (key.endsWith(".special")) offset = 320;
                }
                JsonObject rect = old.getAsJsonObject(field);
                var actual = image(theme.texture(key).location());
                int w = rect.get("uWidth").getAsInt(), h = rect.get("vHeight").getAsInt();
                assertEquals(id + ":" + key, w, actual.getWidth());
                assertEquals(h, actual.getHeight());
                int x = rect.get("u0").getAsInt() + offset, y = rect.get("v0").getAsInt();
                assertArrayEquals(id + ":" + key, atlas.getRGB(x, y, w, h, null, 0, w), actual.getRGB(0, 0, w, h, null, 0, w));
                checked++;
            }
        }
        assertEquals(168, checked);
    }

    @Test
    public void rejectsLegacyAndMalformedDescriptors() throws Exception {
        byte[] legacy;
        try (var in = resource("theme-migration/sakura.json")) {
            legacy = in.readAllBytes();
        }
        assertThrows(JsonParseException.class, () -> BuiltInThemeCatalog.read("sakura", path -> new ByteArrayInputStream(legacy)));
        for (Consumer<JsonObject> change : List.<Consumer<JsonObject>>of(
                j -> j.addProperty("format", 1), j -> j.addProperty("format", 2.5), j -> j.addProperty("format", "2"),
                j -> j.addProperty("id", "maple"), j -> j.addProperty("textureRoot", "sakura_sign_in:../"),
                j -> j.getAsJsonObject("layout").addProperty("totalWidth", 500),
                j -> j.getAsJsonObject("layout").add("cellCoordinate", JsonNull.INSTANCE),
                j -> j.getAsJsonObject("layout").addProperty("weekStart", 8),
                j -> j.getAsJsonObject("textures").remove("icon.info"),
                j -> j.getAsJsonObject("textures").addProperty("icon.info", "../outside.png"),
                j -> j.getAsJsonObject("textures").addProperty("icon.info", "C:\\outside.png"))) {
            assertThrows(RuntimeException.class, () -> changed(change, null));
        }
    }

    @Test
    public void validatesMissingBrokenAndOversizedPngsBeforeUsingThem() throws Exception {
        assertThrows(IOException.class, () -> changed(j -> {
        }, new byte[0]));
        assertThrows(IOException.class, () -> changed(j -> {
        }, new byte[2 * 1024 * 1024 + 1]));
        var huge = new BufferedImage(2049, 1, BufferedImage.TYPE_INT_ARGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(huge, "png", bytes);
        assertThrows(IOException.class, () -> changed(j -> {
        }, bytes.toByteArray()));
        assertThrows(IOException.class, () -> BuiltInThemeCatalog.read("sakura", path -> {
            if (path.getPath().endsWith(".png")) throw new FileNotFoundException(path.toString());
            return packaged(path);
        }));
    }

    @Test
    public void acceptsReplacementFilenamesAndActualPngDimensions() throws Exception {
        var image = new BufferedImage(13, 17, BufferedImage.TYPE_INT_ARGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var theme = changed(j -> j.getAsJsonObject("textures").addProperty("icon.info", "icons/replacement.png"), bytes.toByteArray());
        assertEquals(13, theme.texture("icon.info").width());
        assertEquals(17, theme.texture("icon.info").height());
        assertTrue(theme.texture("icon.info").location().getPath().endsWith("icons/replacement.png"));
        assertEquals(300, theme.getLayout().getTooltipWidth());
        assertEquals(96, theme.getLayout().getTooltipHeight());
    }

    @Test
    public void higherResolutionPngDoesNotChangeLogicalTooltipLayout() throws Exception {
        var image = new BufferedImage(600, 192, BufferedImage.TYPE_INT_ARGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var theme = changed(j -> {
        }, bytes.toByteArray());
        assertEquals(600, theme.texture("background.tooltip").width());
        assertEquals(192, theme.texture("background.tooltip").height());
        assertEquals(300, theme.getLayout().getTooltipWidth());
        assertEquals(96, theme.getLayout().getTooltipHeight());
        var ordinary = BuiltInThemeCatalog.load("sakura").getLayout();
        assertEquals(ordinary.getTooltipCellCoordinate(), theme.getLayout().getTooltipCellCoordinate());
        assertEquals(ordinary.getTooltipDateCoordinate(), theme.getLayout().getTooltipDateCoordinate());
        assertEquals(ordinary.getTooltipScrollCoordinate(), theme.getLayout().getTooltipScrollCoordinate());
    }

    @Test
    public void alphaHitMaskUsesValidatedPixelsAndRejectsOutsideCoordinates() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 0, 0x01000000);
        image.setRGB(0, 1, 0xff000000);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var texture = changed(j -> {
        }, bytes.toByteArray()).texture("button.arrow.normal");
        assertFalse(texture.opaqueAt(0, 0));
        assertTrue(texture.opaqueAt(1, 0));
        assertTrue(texture.opaqueAt(0, 1));
        assertFalse(texture.opaqueAt(-1, 0));
        assertFalse(texture.opaqueAt(2, 0));
        assertFalse(texture.opaqueAt(0, 2));
        assertTrue(texture.isolated().opaqueAt(1, 0));
    }

    @Test
    public void texturesNoLongerContainSerializedPrivateChunks() throws Exception {
        for (String id : BuiltInThemeCatalog.themeIds())
            for (var path : new HashSet<>(BuiltInThemeCatalog.load(id).getResolved().values())) {
                try (var input = new DataInputStream(packaged(path.location()))) {
                    input.readLong();
                    String type;
                    do {
                        int size = input.readInt();
                        type = new String(input.readNBytes(4), StandardCharsets.US_ASCII);
                        assertFalse("vacb".equals(type));
                        input.skipNBytes((long) size + 4);
                    } while (!"IEND".equals(type));
                }
            }
    }

    private static BuiltInThemeDescriptor changed(Consumer<JsonObject> change, byte[] png) throws Exception {
        JsonObject json = json("assets/sakura_sign_in/themes/sakura.json");
        change.accept(json);
        return BuiltInThemeCatalog.read("sakura", path -> path.getPath().endsWith(".json")
                ? new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8))
                : png == null ? packaged(path) : new ByteArrayInputStream(png));
    }

    private static JsonObject json(String path) throws IOException {
        try (var in = resource(path); var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static BufferedImage image(ResourceLocation path) throws IOException {
        try (var in = packaged(path)) {
            return ImageIO.read(in);
        }
    }

    private static InputStream packaged(ResourceLocation path) throws IOException {
        return resource("assets/" + path.getNamespace() + "/" + path.getPath());
    }

    private static InputStream resource(String path) throws IOException {
        var in = BuiltInThemeCatalogTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) throw new FileNotFoundException(path);
        return in;
    }
}
