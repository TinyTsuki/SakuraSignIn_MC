package xin.vanilla.sakura.client.theme;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;

/**
 * 内置主题的纹理与布局描述，数据来源于打包的 UTF-8 JSON。
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public final class BuiltInThemeDescriptor {
    private int format;
    private String id;
    private String texture;
    private TextureCoordinate coordinates;

    public ResourceLocation textureLocation() {
        return ResourceLocation.parse(texture);
    }
}
