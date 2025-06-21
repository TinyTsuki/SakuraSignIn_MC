package xin.vanilla.sakura.screen.theme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class ThemeComponentList extends ArrayList<ThemeComponent> {

    public ThemeComponentList() {
    }

    public ThemeComponentList(ThemeComponent... elements) {
        super(elements.length);
        Collections.addAll(this, elements);
    }

    public ThemeComponentList(Collection<ThemeComponent> collection) {
        super(collection);
    }

    public ThemeComponentList put(ThemeComponent... elements) {
        Collections.addAll(this, elements);
        return this;
    }

    public ThemeComponentList sorted() {
        this.sort(Comparator.comparing(ThemeComponent::getId));
        return this;
    }

    public ThemeComponent getFirst() {
        return this.get(0);
    }

    public ThemeComponent getLast() {
        return this.get(this.size() - 1);
    }

    public ThemeComponent get(int index) {
        if (index >= this.size()) {
            return super.get(index % this.size());
        } else {
            return super.get(index);
        }
    }
}
