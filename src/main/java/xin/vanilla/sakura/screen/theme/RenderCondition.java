package xin.vanilla.sakura.screen.theme;

import lombok.Data;
import lombok.experimental.Accessors;
import xin.vanilla.sakura.enums.EnumRenderConditionType;

import java.io.Serializable;

/**
 * 渲染条件
 */
@Data
@Accessors(chain = true)
public class RenderCondition implements Serializable, Cloneable {
    /**
     * 条件类型
     */
    private EnumRenderConditionType type;
    /**
     * 条件集合
     */
    private RenderConditionList children;
    /**
     * 条件值
     */
    private String param;

    @Override
    public RenderCondition clone() {
        try {
            RenderCondition cloned = (RenderCondition) super.clone();
            if (this.children != null) {
                cloned.setChildren(this.children.clone());
            }
            return cloned;
        } catch (Exception e) {
            return new RenderCondition();
        }
    }
}
