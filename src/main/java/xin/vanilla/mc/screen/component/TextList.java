package xin.vanilla.mc.screen.component;

import java.util.ArrayList;
import java.util.Collections;

public class TextList extends ArrayList<Text> {
    public TextList() {
    }

    public TextList(Text... elements) {
        super(elements.length);
        Collections.addAll(this, elements);
    }

    public TextList put(Text... elements) {
        Collections.addAll(this, elements);
        return this;
    }

    public Text get(int index) {
        if (index >= this.size()) {
            return super.get(index % this.size());
        } else {
            return super.get(index);
        }
    }
}
