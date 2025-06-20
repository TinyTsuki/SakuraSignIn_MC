package xin.vanilla.sakura.screen.theme;

import lombok.Data;
import lombok.experimental.Accessors;
import xin.vanilla.sakura.enums.EnumThemeComponentType;

import java.io.Serializable;

/**
 * 主题组件
 */
@Data
@Accessors(chain = true)
public class ThemeComponent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;

    private final long parentId;

    /**
     * 组件类型
     */
    private EnumThemeComponentType type;
    /**
     * 组件参数，仅EnumThemeComponentType为SWITCH时有用
     */
    private String param;

    /**
     * 正常渲染队列
     */
    private RenderInfoList renderList = new RenderInfoList(RenderType.NORMAL);
    /**
     * 鼠标悬浮时渲染队列
     */
    private RenderInfoList hoverRenderList = new RenderInfoList(RenderType.HOVER);
    /**
     * 鼠标或键盘按下时渲染队列
     */
    private RenderInfoList tapRenderList = new RenderInfoList(RenderType.TAP);

    public ThemeComponent() {
        this(0);
    }

    public ThemeComponent(int parentId) {
        this.id = System.currentTimeMillis();
        this.parentId = parentId;
    }

    public enum RenderType {
        NORMAL,
        HOVER,
        TAP,
    }

    public RenderInfoList getRenderList(RenderType renderType) {
        switch (renderType) {
            case HOVER:
                return this.hoverRenderList;
            case TAP:
                return this.tapRenderList;
            case NORMAL:
            default:
                return this.renderList;
        }
    }

}
