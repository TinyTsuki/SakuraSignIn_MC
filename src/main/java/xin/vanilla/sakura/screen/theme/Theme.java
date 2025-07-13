package xin.vanilla.sakura.screen.theme;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.screen.coordinate.Coordinate;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Theme implements Serializable {
    /**
     * 主题名称
     */
    private String name;
    /**
     * 主题作者
     */
    private String author = "";
    /**
     * 主题版本
     */
    private String version = "";
    /**
     * 主题描述
     */
    private String description = "";
    /**
     * 编辑模式
     */
    private boolean editing;
    /**
     * 是否启用原版背景渲染
     */
    private boolean minecraftBackground;
    /**
     * 主题组件定义
     */
    private ThemeComponentList components = new ThemeComponentList();
    /**
     * 夜间主题组件定义
     */
    private ThemeComponentList darkComponents = new ThemeComponentList();
    /**
     * 纹理地图</br>
     * TextureId -> Coordinate
     */
    private Map<String, Coordinate> textureMap = new HashMap<>();


    /**
     * 主题缓存参数
     */
    private transient RenderCondition.Args args = new RenderCondition.Args();


    /**
     * 主题纹理路径
     */
    private transient File file;
    /**
     * 主题纹理资源
     */
    private transient ResourceLocation resourceLocation;

    /**
     * 主题纹理缓存</br>
     * TextureId -> ResourceLocation
     */
    private transient Map<String, ResourceLocation> textureCache = new HashMap<>();
    /**
     * 主题配置路径
     */
    private transient File configFile;
    /**
     * 主题纹理图片总宽度
     */
    private transient int totalWidth;
    /**
     * 主题纹理图片总高度
     */
    private transient int totalHeight;

    public Theme(boolean editing) {
        this.editing = editing;
    }

    public ThemeComponentList getVisible() {
        if (ClientConfig.THEME_ARGS.get().contains("darkComponents")
                || args.getParams().contains("darkComponents")
        ) {
            return new ThemeComponentList(this.darkComponents.stream()
                    .filter(component -> component.isVisible(args))
                    .sorted(Comparator.comparing(ThemeComponent::getId))
                    .collect(Collectors.toList()))
                    ;
        } else {
            return new ThemeComponentList(this.components.stream()
                    .filter(component -> component.isVisible(args))
                    .sorted(Comparator.comparing(ThemeComponent::getId))
                    .collect(Collectors.toList()))
                    ;
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", this.name);
        json.addProperty("author", this.author);
        json.addProperty("version", this.version);
        json.addProperty("description", this.description);
        json.addProperty("editing", this.editing);
        json.addProperty("minecraftBackground", this.minecraftBackground);
        json.add("components", this.components.toJson());
        json.add("darkComponents", this.darkComponents.toJson());

        JsonObject textureMapJson = new JsonObject();
        this.textureMap.forEach((key, value) -> textureMapJson.add(key, value.toJson()));
        json.add("textureMap", textureMapJson);

        return json;
    }

}
