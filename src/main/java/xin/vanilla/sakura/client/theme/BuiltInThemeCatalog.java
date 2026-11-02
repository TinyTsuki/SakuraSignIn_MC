package xin.vanilla.sakura.client.theme;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BuiltInThemeCatalog {
    public static final int FORMAT_VERSION = 2;
    public static final String DEFAULT_THEME_ID = "sakura";
    private static final List<String> THEME_IDS = com.google.common.collect.ImmutableList.of("original", DEFAULT_THEME_ID, "clover", "maple", "chaos");
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogManager.getLogger();

    private BuiltInThemeCatalog() {
    }

    public static List<String> themeIds() {
        return THEME_IDS;
    }

    public static String normalize(String id) {
        String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return THEME_IDS.contains(value) ? value : DEFAULT_THEME_ID;
    }

    @FunctionalInterface
    interface Resources {
        InputStream open(ResourceLocation path) throws IOException;
    }

    public static BuiltInThemeDescriptor load(String id) {
        return load(id, null);
    }

    public static BuiltInThemeDescriptor load(String requested, ResourceManager manager) {
        String id = normalize(requested);
        if (manager != null) try {
            return read(id, path -> manager.getResource(path).getInputStream());
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Invalid resource-pack theme '{}'; using packaged theme", id, error);
        }
        try {
            lombok.var packaged = read(id, BuiltInThemeCatalog::packaged);
            return manager == null ? packaged : packaged.isolatePackagedTextures();
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Invalid packaged Sakura theme '{}'", id, error);
            if (!DEFAULT_THEME_ID.equals(id)) try {
                lombok.var packaged = read(DEFAULT_THEME_ID, BuiltInThemeCatalog::packaged);
                return manager == null ? packaged : packaged.isolatePackagedTextures();
            } catch (IOException | RuntimeException fallback) {
                error.addSuppressed(fallback);
            }
            throw new IllegalStateException("No valid packaged Sakura theme", error);
        }
    }

    private static InputStream packaged(ResourceLocation path) throws IOException {
        InputStream input = BuiltInThemeCatalog.class.getClassLoader().getResourceAsStream("assets/" + path.getNamespace() + "/" + path.getPath());
        if (input == null) throw new IOException("Missing packaged theme resource: " + path);
        return input;
    }

    static BuiltInThemeDescriptor read(String id, Resources resources) throws IOException {
        JsonObject json;
        try (InputStream in = resources.open(new ResourceLocation(SakuraSignIn.MODID, "themes/" + id + ".json"))) {
            json = new JsonParser().parse(new String(bounded(in, 65536), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        rejectAtlasKeys(json);
        if (!json.has("format") || !json.get("format").isJsonPrimitive()
                || !json.getAsJsonPrimitive("format").isNumber()
                || json.get("format").getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(FORMAT_VERSION)) != 0
                || !json.has("layout") || !json.has("textures"))
            throw new JsonParseException("Theme format 2 with layout and standalone textures required");
        BuiltInThemeDescriptor theme = GSON.fromJson(json, BuiltInThemeDescriptor.class);
        if (!id.equals(theme.getId()) || theme.getLayout() == null || theme.getTextures() == null || theme.getTextureRoot() == null)
            throw new JsonParseException("Incomplete theme descriptor");
        ResourceLocation root = new ResourceLocation(theme.getTextureRoot());
        if (!root.getPath().startsWith("textures/") || !root.getPath().endsWith("/") || root.getPath().contains(".."))
            throw new JsonParseException("Invalid theme texture root");
        validateLayout(theme.getLayout());
        Set<String> expected = keys(theme.getLayout().isSpecial());
        if (!theme.getTextures().keySet().equals(expected))
            throw new JsonParseException("Incomplete or unknown theme texture keys");
        Map<String, ThemeTexture> resolved = new LinkedHashMap<>();
        Map<ResourceLocation, ThemeTexture> cache = new HashMap<>();
        for (String key : expected) {
            JsonElement raw = json.getAsJsonObject("textures").get(key);
            if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString())
                throw new JsonParseException("Texture filename required: " + key);
            String file = theme.getTextures().get(key);
            if (file == null || file.length() > 256 || !file.matches("[a-z0-9_/-]+\\.png") || file.startsWith("/") || file.contains(".."))
                throw new JsonParseException("Invalid relative PNG filename: " + key);
            ResourceLocation path = new ResourceLocation(root.getNamespace(), root.getPath() + file);
            ThemeTexture texture = cache.get(path);
            if (texture == null) {
                try (InputStream in = resources.open(path)) {
                    texture = png(path, bounded(in, 2 * 1024 * 1024));
                }
                cache.put(path, texture);
            }
            resolved.put(key, texture);
        }
        theme.resolve(resolved);
        return theme;
    }

    static Set<String> keys(boolean special) {
        Set<String> keys = new LinkedHashSet<>(com.google.common.collect.ImmutableList.of("background.calendar", "background.tooltip", "background.editor", "icon.info", "icon.sign_in", "icon.reward_editor", "status.rewarded", "status.not_signed_in.normal", "status.signed_in.normal"));
        for (String state : com.google.common.collect.ImmutableList.of("normal", "hover", "pressed")) {
            keys.add("button.arrow." + state);
            for (String id : THEME_IDS) keys.add("selector." + id + "." + state);
        }
        for (String reward : com.google.common.collect.ImmutableList.of("buff", "point", "level", "card", "message")) keys.add("reward." + reward);
        if (special) {
            keys.add("status.not_signed_in.special");
            keys.add("status.signed_in.special");
        }
        return keys;
    }

    private static void rejectAtlasKeys(JsonElement value) {
        if (!value.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("UV") || com.google.common.collect.ImmutableList.of("texture", "coordinates", "u0", "v0", "uWidth", "vHeight", "totalWidth", "totalHeight").contains(key))
                throw new JsonParseException("Atlas configuration is not supported: " + key);
            rejectAtlasKeys(entry.getValue());
        }
    }

    private static void validateLayout(TextureCoordinate layout) {
        if (layout.getWidth() <= 0 || layout.getHeight() <= 0 || layout.getWidth() > 8192 || layout.getHeight() > 8192
                || layout.getTooltipWidth() <= 0 || layout.getTooltipHeight() <= 0 || layout.getTooltipWidth() > 8192 || layout.getTooltipHeight() > 8192
                || layout.getWeekStart() < 1 || layout.getWeekStart() > 7)
            throw new JsonParseException("Invalid logical theme dimensions or week start");
        for (Field field : TextureCoordinate.class.getDeclaredFields())
            if (field.getType() == Coordinate.class) try {
                field.setAccessible(true);
                Coordinate c = (Coordinate) field.get(layout);
                if (c == null || !Double.isFinite(c.x) || !Double.isFinite(c.y) || !Double.isFinite(c.width) || !Double.isFinite(c.height) || c.width <= 0 || c.height <= 0 || Math.abs(c.x) > 16384 || Math.abs(c.y) > 16384 || c.width > 16384 || c.height > 16384)
                    throw new JsonParseException("Invalid layout rectangle: " + field.getName());
            } catch (IllegalAccessException error) {
                throw new JsonParseException(error);
            }
    }

    private static byte[] bounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (result.size() + count > max) throw new IOException("Theme resource exceeds size limit");
            result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }

    private static ThemeTexture png(ResourceLocation path, byte[] bytes) throws IOException {
        if (bytes.length < 8 || new DataInputStream(new ByteArrayInputStream(bytes)).readLong() != 0x89504E470D0A1A0AL)
            throw new IOException("PNG required: " + path);
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid PNG: " + path);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 2048 || h > 2048 || (long) w * h > 4L * 1024 * 1024)
                    throw new IOException("Oversized theme PNG: " + path);
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("Undecodable PNG: " + path);
                try {
                    return new ThemeTexture(path, image, bytes);
                } finally {
                    image.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
