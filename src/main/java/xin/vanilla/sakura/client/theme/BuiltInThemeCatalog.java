package xin.vanilla.sakura.client.theme;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 只加载随模组发布的主题，配置中保存稳定 ID，不再接受文件系统路径。
 */
public final class BuiltInThemeCatalog {
    public static final int FORMAT_VERSION = 1;
    public static final String DEFAULT_THEME_ID = "sakura";
    private static final String RESOURCE_ROOT = "assets/" + SakuraSignIn.MODID + "/themes/";
    private static final List<String> THEME_IDS = Collections.unmodifiableList(Arrays.asList(
            "original", DEFAULT_THEME_ID, "clover", "maple", "chaos"
    ));
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogManager.getLogger();

    private BuiltInThemeCatalog() {
    }

    public static List<String> themeIds() {
        return THEME_IDS;
    }

    public static BuiltInThemeDescriptor load(String requestedId) {
        return load(requestedId, null);
    }

    /**
     * 客户端优先从资源管理器读取描述文件，因此资源包可以替换 JSON 与纹理。
     */
    public static BuiltInThemeDescriptor load(String requestedId, ResourceManager resourceManager) {
        String id = normalize(requestedId);
        try {
            return read(id, resourceManager);
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            LOGGER.error("Failed to load built-in Sakura theme '{}'", id, exception);
            if (!DEFAULT_THEME_ID.equals(id)) {
                try {
                    return read(DEFAULT_THEME_ID, resourceManager);
                } catch (IOException | JsonParseException | IllegalArgumentException fallbackException) {
                    LOGGER.error("Failed to load default Sakura theme", fallbackException);
                }
            }
            return new BuiltInThemeDescriptor(
                    FORMAT_VERSION,
                    DEFAULT_THEME_ID,
                    SakuraSignIn.MODID + ":textures/gui/sign_in_calendar_sakura.png",
                    TextureCoordinate.getDefault()
            );
        }
    }

    public static String normalize(String requestedId) {
        String normalized = requestedId == null
                ? ""
                : requestedId.trim().toLowerCase(Locale.ROOT);
        return THEME_IDS.contains(normalized) ? normalized : DEFAULT_THEME_ID;
    }

    private static BuiltInThemeDescriptor read(String id, ResourceManager resourceManager) throws IOException {
        String resourcePath = RESOURCE_ROOT + id + ".json";
        if (resourceManager != null) {
            String relativePath = resourcePath.substring(("assets/" + SakuraSignIn.MODID + "/").length());
            Resource resource = resourceManager.getResource(
                    new ResourceLocation(SakuraSignIn.MODID, relativePath))
                    .orElseThrow(() -> new IOException("Missing theme descriptor: " + resourcePath));
            try (InputStream input = resource.open()) {
                return readDescriptor(id, input);
            }
        }
        try (InputStream input = BuiltInThemeCatalog.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing theme descriptor: " + resourcePath);
            }
            return readDescriptor(id, input);
        }
    }

    private static BuiltInThemeDescriptor readDescriptor(String id, InputStream input) {
        BuiltInThemeDescriptor descriptor = GSON.fromJson(
                new InputStreamReader(input, StandardCharsets.UTF_8),
                BuiltInThemeDescriptor.class
        );
        validate(id, descriptor);
        return descriptor;
    }

    private static void validate(String requestedId, BuiltInThemeDescriptor descriptor) {
        if (descriptor == null
                || descriptor.getFormat() != FORMAT_VERSION
                || !requestedId.equals(descriptor.getId())
                || descriptor.getTexture() == null
                || descriptor.getCoordinates() == null) {
            throw new JsonParseException("Invalid theme descriptor: " + requestedId);
        }
        descriptor.textureLocation();
        TextureCoordinate coordinates = descriptor.getCoordinates();
        if (coordinates.getTotalWidth() <= 0 || coordinates.getTotalHeight() <= 0) {
            throw new JsonParseException("Invalid texture dimensions: " + requestedId);
        }
        for (Field field : TextureCoordinate.class.getDeclaredFields()) {
            if (!Coordinate.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                if (field.get(coordinates) == null) {
                    throw new JsonParseException("Missing coordinate '" + field.getName()
                            + "' in theme: " + requestedId);
                }
            } catch (IllegalAccessException exception) {
                throw new JsonParseException("Cannot validate theme: " + requestedId, exception);
            }
        }
    }
}
