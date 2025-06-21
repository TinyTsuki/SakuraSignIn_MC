package xin.vanilla.sakura.screen.theme;

import lombok.Data;
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
     * 是否允许二次编辑
     */
    private boolean editable;
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
     * 纹理地图
     */
    private Map<String, Coordinate> texureMap = new HashMap<>();


    /**
     * 主题纹理路径
     */
    private transient File file;
    /**
     * 主题资源
     */
    private transient ResourceLocation resourceLocation;
    /**
     * 主题纹理图片总宽度
     */
    private transient int totalWidth;
    /**
     * 主题纹理图片总高度
     */
    private transient int totalHeight;

    /**
     * 主题缓存参数
     */
    private RenderCondition.Args args = new RenderCondition.Args();

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

}
