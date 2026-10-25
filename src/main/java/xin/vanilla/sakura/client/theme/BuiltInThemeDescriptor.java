package xin.vanilla.sakura.client.theme;

import lombok.Getter;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import java.util.*;

@Getter
public final class BuiltInThemeDescriptor {
    private int format;
    private String id;
    private String textureRoot;
    private TextureCoordinate layout;
    private Map<String, String> textures;
    private transient Map<String, ThemeTexture> resolved;
    void resolve(Map<String, ThemeTexture> values) {
        textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
        resolved = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    BuiltInThemeDescriptor isolatePackagedTextures() {
        Map<String, ThemeTexture> isolated = new LinkedHashMap<>();
        resolved.forEach((key, value) -> isolated.put(key, value.isolated()));
        resolve(isolated);
        return this;
    }
    public ThemeTexture texture(String key) {
        ThemeTexture result = resolved == null ? null : resolved.get(key);
        if(result == null) throw new IllegalArgumentException("Missing theme texture: " + key);
        return result;
    }
}
