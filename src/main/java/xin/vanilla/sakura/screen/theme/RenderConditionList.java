package xin.vanilla.sakura.screen.theme;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

@Getter
@Setter
@Accessors(chain = true)
public class RenderConditionList extends ArrayList<RenderCondition> implements Serializable, Cloneable {

    /**
     * 逻辑类型： true->and，false->or
     */
    private boolean logicType;

    public RenderConditionList() {
    }

    public RenderConditionList(Collection<RenderCondition> collection) {
        super(collection);
    }

    @Override
    public RenderConditionList clone() {
        try {
            RenderConditionList cloned = new RenderConditionList();
            for (RenderCondition condition : this) {
                if (condition != null) {
                    cloned.add(condition.clone());
                }
            }
            return cloned;
        } catch (Exception e) {
            return new RenderConditionList();
        }
    }
}
