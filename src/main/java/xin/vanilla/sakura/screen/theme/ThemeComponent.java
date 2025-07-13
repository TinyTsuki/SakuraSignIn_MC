package xin.vanilla.sakura.screen.theme;

import com.google.gson.JsonObject;
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
     * 组件渲染条件
     */
    private RenderCondition condition;

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


    private transient boolean selected;

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

    public boolean isVisible(RenderCondition.Args args) {
        return this.condition == null || condition.isValid(args);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", this.id);
        json.addProperty("parentId", this.parentId);
        json.addProperty("type", this.type.name());
        json.addProperty("param", this.param);
        if (this.condition != null) {
            json.add("condition", this.condition.toJson());
        }
        json.add("renderList", this.renderList.toJson());
        json.add("hoverRenderList", this.hoverRenderList.toJson());
        json.add("tapRenderList", this.tapRenderList.toJson());
        return json;
    }

}
