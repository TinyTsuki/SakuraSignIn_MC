package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.common.data.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

final class RecordingUiView implements UiView {
    final Map<String, Runnable> actions = new HashMap<>();
    final Map<String, Runnable> longActions = new HashMap<>();
    final Map<String, Consumer<String>> drafts = new HashMap<>();
    final Map<String, Consumer<UiPointer>> pointers = new HashMap<>();
    final Map<String, BooleanSupplier> scrollRoutes = new HashMap<>();
    final Map<String, String> scrollTargets = new HashMap<>();
    final Map<String, Component> texts = new HashMap<>(), tooltips = new HashMap<>();
    final Map<String, UiGameElement> icons = new HashMap<>();
    final Map<String, Boolean> enabled = new HashMap<>();
    final Map<String, String> draftValues = new HashMap<>();
    final Map<String, UiValue> values = new HashMap<>();
    final Map<String, UiControlSpec> controls = new HashMap<>();
    final Map<String, Consumer<UiValue>> changes = new HashMap<>();
    final Map<String, ScreenCoordinate> areas = new HashMap<>();
    final Map<String, ScreenCoordinate> geometry = new HashMap<>();
    final UiPageState state = new UiPageState();
    UiRowProvider rows;
    final Map<String, UiRowProvider> providers = new LinkedHashMap<>();
    boolean nativeView;
    int closes, limit, writes;

    public boolean nativeView() {
        return nativeView;
    }

    public UiPageState state() {
        return state;
    }

    public void theme(BaniraColorConfig value) {
    }

    public void text(String id, Component value) {
        writes++;
        texts.put(id, value);
    }

    public void tooltip(String id, Component value) {
        tooltips.put(id, value);
    }

    public void value(String id, UiValue value) {
        values.put(id, value);
    }

    public void control(String id, UiControlSpec value) {
        controls.put(id, value);
    }

    public void draft(String id, String value) {
        draftValues.put(id, value);
    }

    public void enabled(String id, boolean value) {
        writes++;
        enabled.put(id, value);
    }

    public AutoCloseable onAction(String id, Runnable value) {
        actions.put(id, value);
        return () -> actions.remove(id, value);
    }

    public AutoCloseable onLongAction(String id, Runnable value) {
        longActions.put(id, value);
        return () -> longActions.remove(id, value);
    }

    public AutoCloseable onValue(String id, Consumer<UiValue> value) {
        changes.put(id, value);
        return () -> changes.remove(id, value);
    }

    public AutoCloseable onDraft(String id, Consumer<String> value) {
        drafts.put(id, value);
        return () -> drafts.remove(id, value);
    }

    public AutoCloseable onPointer(String id, Consumer<UiPointer> value) {
        pointers.put(id, value);
        return () -> pointers.remove(id, value);
    }

    public AutoCloseable forwardScroll(String source, String target, BooleanSupplier active) {
        scrollRoutes.put(source, active);
        scrollTargets.put(source, target);
        return () -> {
            scrollRoutes.remove(source, active);
            scrollTargets.remove(source, target);
        };
    }

    public void hudPosition(double x, double y) {
    }

    public void gameElement(String id, UiGameElement value) {
        icons.put(id, value);
    }

    public void gameElement(String id, UiGameElement value, ScreenCoordinate area) {
        icons.put(id, value);
        areas.put(id, area);
    }

    public void rows(String id, UiRowProvider value, int max) {
        providers.put(id, value);
        if (!"fields_middle".equals(id) && !"fields_end".equals(id)) rows = value;
        limit = max;
    }

    public ScreenCoordinate bounds(String id) {
        return geometry.getOrDefault(id, new ScreenCoordinate(0, 0, 400, 300));
    }

    public void requestClose() {
        closes++;
    }
}
