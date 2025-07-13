package xin.vanilla.sakura.screen.theme;

import com.google.gson.JsonArray;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class RenderInfoList extends ArrayList<RenderInfo> {

    private final AtomicInteger index = new AtomicInteger(0);

    @Getter
    private final ThemeComponent.RenderType renderType;

    public RenderInfoList() {
        this.renderType = ThemeComponent.RenderType.NORMAL;
    }

    public RenderInfoList(ThemeComponent.RenderType renderType) {
        this.renderType = renderType;
    }

    public RenderInfoList(RenderInfo... elements) {
        super(elements.length);
        this.renderType = ThemeComponent.RenderType.NORMAL;
        Collections.addAll(this, elements);
    }

    public RenderInfoList put(RenderInfo... elements) {
        Collections.addAll(this, elements);
        return this;
    }

    public RenderInfo getFirst() {
        return this.get(0);
    }

    public RenderInfo getLast() {
        return this.get(this.size() - 1);
    }

    public RenderInfo get(int index) {
        if (index >= this.size()) {
            return super.get(index % this.size());
        } else {
            return super.get(index);
        }
    }

    public RenderInfo getAndIncrement() {
        RenderInfo renderInfo = this.get(index.getAndIncrement());
        if (index.get() >= this.size()) {
            index.set(0);
        }
        return renderInfo;
    }

    public JsonArray toJson() {
        JsonArray jsonArray = new JsonArray();
        this.forEach(renderInfo -> jsonArray.add(renderInfo.toJson()));
        return jsonArray;
    }
}
