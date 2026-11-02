package xin.vanilla.sakura.client.theme;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;
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
    public void fallsBackToSakuraAndReturnsFreshCoordinates() {
        assertEquals("sakura", BuiltInThemeCatalog.load("../external.png").getId());
        BuiltInThemeDescriptor first = BuiltInThemeCatalog.load("original");
        BuiltInThemeDescriptor second = BuiltInThemeCatalog.load("original");
        assertNotSame(first.getLayout(), second.getLayout());
        double originalX = second.getLayout().getCellCoordinate().getX();
        first.getLayout().getCellCoordinate().setX(320);
        assertEquals(originalX, second.getLayout().getCellCoordinate().getX(), 0);
    }

    @Test
    public void texturesNoLongerContainSerializedPrivateChunks() throws Exception {
        pngsDoNotContainSerializedPrivateChunks();
    }

    @Test
    public void loadsEveryPackagedThemeByStableId() throws Exception {
        assertEquals(com.google.common.collect.ImmutableList.of("original", "sakura", "clover", "maple", "chaos"), BuiltInThemeCatalog.themeIds());
        for (String id : BuiltInThemeCatalog.themeIds()) {
            lombok.var theme = BuiltInThemeCatalog.load(id);
            assertEquals(2, theme.getFormat());
            assertEquals(id, theme.getId());
            assertEquals(!id.equals("chaos"), theme.getLayout().isSpecial());
            assertEquals(BuiltInThemeCatalog.keys(theme.getLayout().isSpecial()), theme.getTextures().keySet());
            assertNull(getClass().getClassLoader().getResource("assets/sakura_sign_in/textures/gui/sign_in_calendar_" + id + ".png"));
            for (lombok.var texture : theme.getResolved().values()) {
                lombok.var image = image(texture.location());
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
    public void normalizesIdsAndKeepsLayoutsIndependent() {
        assertEquals("sakura", BuiltInThemeCatalog.load("../external.png").getId());
        lombok.var first = BuiltInThemeCatalog.load("original");
        lombok.var second = BuiltInThemeCatalog.load("original");
        assertNotSame(first.getLayout(), second.getLayout());
        first.getLayout().getCellCoordinate().setX(320);
        assertNotEquals(320, second.getLayout().getCellCoordinate().getX(), 0);
        assertThrows(UnsupportedOperationException.class, () -> first.getTextures().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.getResolved().clear());
    }

    @Test
    public void incompleteResourcePackFallsBackToTheRequestedPackagedTheme() {
        lombok.var manager = (net.minecraft.resources.IResourceManager) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{net.minecraft.resources.IResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) throw new FileNotFoundException(String.valueOf(args[0]));
                    throw new UnsupportedOperationException(method.getName());
                });
        for (String id : BuiltInThemeCatalog.themeIds()) {
            lombok.var theme = BuiltInThemeCatalog.load(id, manager);
            assertEquals(id, theme.getId());
            lombok.var original = BuiltInThemeCatalog.load(id).texture("background.calendar");
            lombok.var fallback = theme.texture("background.calendar");
            assertNotEquals(original.location(), fallback.location());
            assertTrue(fallback.location().getPath().startsWith("runtime/themes/"));
            assertTrue(fallback.requiresUpload());
            try (java.io.InputStream expected = original.openPng(); java.io.InputStream actual = fallback.openPng()) {
                assertArrayEquals(com.google.common.io.ByteStreams.toByteArray(expected), com.google.common.io.ByteStreams.toByteArray(actual));
            } catch (IOException error) {
                throw new AssertionError(error);
            }
            for (int y = 0; y < original.height(); y++)
                for (int x = 0; x < original.width(); x++)
                    assertEquals(original.opaqueAt(x, y), fallback.opaqueAt(x, y));
        }
    }

    @Test
    public void managedResourceHandlesCloseOnSuccessfulAndFailedReads() {
        for (boolean invalid : new boolean[]{false, true}) {
            java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicInteger closed = new java.util.concurrent.atomic.AtomicInteger();
            net.minecraft.resources.IResourceManager manager =
                    (net.minecraft.resources.IResourceManager) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(), new Class<?>[]{net.minecraft.resources.IResourceManager.class},
                            (proxy, method, args) -> {
                                if (!method.getName().equals("getResource"))
                                    throw new UnsupportedOperationException(method.getName());
                                ResourceLocation path = (ResourceLocation) args[0];
                                InputStream stream = invalid
                                        ? new ByteArrayInputStream("{\"format\":1}".getBytes(StandardCharsets.UTF_8))
                                        : resource("assets/" + path.getNamespace() + "/" + path.getPath());
                                opened.incrementAndGet();
                                java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
                                return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class<?>[]{net.minecraft.resources.IResource.class}, (handle, operation, values) -> {
                                            if (operation.getName().equals("getInputStream")) return stream;
                                            if (operation.getName().equals("close")) {
                                                if (released.compareAndSet(false, true)) {
                                                    closed.incrementAndGet();
                                                    stream.close();
                                                }
                                                return null;
                                            }
                                            throw new UnsupportedOperationException(operation.getName());
                                        });
                            });
            assertEquals("sakura", BuiltInThemeCatalog.load("sakura", manager).getId());
            assertTrue(opened.get() > 0);
            assertEquals(opened.get(), closed.get());
            if (invalid) assertEquals(1, opened.get());
        }
    }

    @Test
    public void everyStandalonePixelMatchesTheOriginalAtlas() throws Exception {
        int checked = 0;
        Map<String, String> roles = com.google.common.collect.ImmutableMap.<String, String>builder()
                .put("background.calendar", "bgUV").put("background.tooltip", "tooltipUV")
                .put("background.editor", "optionBgUV").put("icon.info", "signInInfoUV")
                .put("icon.sign_in", "signInBtnUV").put("icon.reward_editor", "rewardOptionBtnUV")
                .put("status.rewarded", "rewardedUV").put("status.not_signed_in", "notSignedInUV")
                .put("status.signed_in", "signedInUV").build();
        Map<String, String> states = com.google.common.collect.ImmutableMap.of("normal", "", "hover", "Hover", "pressed", "Tap");
        for (String id : BuiltInThemeCatalog.themeIds()) {
            JsonObject old = json("theme-migration/" + id + ".json").getAsJsonObject("coordinates");
            BufferedImage atlas;
            try (java.io.InputStream in = resource("theme-migration/" + id + ".png")) {
                atlas = ImageIO.read(in);
            }
            lombok.var theme = BuiltInThemeCatalog.load(id);
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
                lombok.var actual = image(theme.texture(key).location());
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
        try (java.io.InputStream in = resource("theme-migration/sakura.json")) {
            legacy = com.google.common.io.ByteStreams.toByteArray(in);
        }
        assertThrows(JsonParseException.class, () -> BuiltInThemeCatalog.read("sakura", path -> new ByteArrayInputStream(legacy)));
        for (Consumer<JsonObject> change : java.util.Arrays.<Consumer<JsonObject>>asList(
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
        lombok.var huge = new BufferedImage(2049, 1, BufferedImage.TYPE_INT_ARGB);
        lombok.var bytes = new ByteArrayOutputStream();
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
        lombok.var image = new BufferedImage(13, 17, BufferedImage.TYPE_INT_ARGB);
        lombok.var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        lombok.var theme = changed(j -> j.getAsJsonObject("textures").addProperty("icon.info", "icons/replacement.png"), bytes.toByteArray());
        assertEquals(13, theme.texture("icon.info").width());
        assertEquals(17, theme.texture("icon.info").height());
        assertTrue(theme.texture("icon.info").location().getPath().endsWith("icons/replacement.png"));
        assertEquals(300, theme.getLayout().getTooltipWidth());
        assertEquals(96, theme.getLayout().getTooltipHeight());
    }

    @Test
    public void higherResolutionPngDoesNotChangeLogicalTooltipLayout() throws Exception {
        lombok.var image = new BufferedImage(600, 192, BufferedImage.TYPE_INT_ARGB);
        lombok.var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        lombok.var theme = changed(j -> {
        }, bytes.toByteArray());
        assertEquals(600, theme.texture("background.tooltip").width());
        assertEquals(192, theme.texture("background.tooltip").height());
        assertEquals(300, theme.getLayout().getTooltipWidth());
        assertEquals(96, theme.getLayout().getTooltipHeight());
        lombok.var ordinary = BuiltInThemeCatalog.load("sakura").getLayout();
        assertEquals(ordinary.getTooltipCellCoordinate(), theme.getLayout().getTooltipCellCoordinate());
        assertEquals(ordinary.getTooltipDateCoordinate(), theme.getLayout().getTooltipDateCoordinate());
        assertEquals(ordinary.getTooltipScrollCoordinate(), theme.getLayout().getTooltipScrollCoordinate());
    }

    @Test
    public void alphaHitMaskUsesValidatedPixelsAndRejectsOutsideCoordinates() throws Exception {
        lombok.var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 0, 0x01000000);
        image.setRGB(0, 1, 0xff000000);
        lombok.var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        lombok.var texture = changed(j -> {
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
    public void pngsDoNotContainSerializedPrivateChunks() throws Exception {
        for (String id : BuiltInThemeCatalog.themeIds())
            for (lombok.var path : new HashSet<>(BuiltInThemeCatalog.load(id).getResolved().values())) {
                try (DataInputStream input = new DataInputStream(packaged(path.location()))) {
                    input.readLong();
                    String type;
                    do {
                        int size = input.readInt();
                        byte[] typeBytes = new byte[4];
                        input.readFully(typeBytes);
                        type = new String(typeBytes, StandardCharsets.US_ASCII);
                        assertFalse("vacb".equals(type));
                        com.google.common.io.ByteStreams.skipFully(input, (long) size + 4);
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
        try (java.io.InputStream in = resource(path); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static BufferedImage image(ResourceLocation path) throws IOException {
        try (java.io.InputStream in = packaged(path)) {
            return ImageIO.read(in);
        }
    }

    private static InputStream packaged(ResourceLocation path) throws IOException {
        return resource("assets/" + path.getNamespace() + "/" + path.getPath());
    }

    private static InputStream resource(String path) throws IOException {
        lombok.var in = BuiltInThemeCatalogTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) throw new FileNotFoundException(path);
        return in;
    }
}
