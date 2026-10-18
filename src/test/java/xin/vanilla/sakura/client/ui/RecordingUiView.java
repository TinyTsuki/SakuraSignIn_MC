package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.*;
import xin.vanilla.banira.common.data.Component;
import java.util.*;
import java.util.function.Consumer;

final class RecordingUiView implements UiView {
    final Map<String, Runnable> actions = new HashMap<>();
    final Map<String, Consumer<String>> drafts = new HashMap<>();
    final Map<String, Consumer<UiPointer>> pointers = new HashMap<>();
    final Map<String, Component> texts = new HashMap<>(), tooltips = new HashMap<>();
    final Map<String, UiGameElement> icons = new HashMap<>();
    final Map<String, Boolean> enabled = new HashMap<>();
    final UiPageState state = new UiPageState();
    UiRowProvider rows;
    boolean nativeView;
    int closes, limit;
    public boolean nativeView() { return nativeView; }
    public UiPageState state() { return state; }
    public void theme(BaniraColorConfig value) { }
    public void text(String id, Component value) { texts.put(id, value); }
    public void tooltip(String id, Component value) { tooltips.put(id, value); }
    public void value(String id, UiValue value) { }
    public void control(String id, UiControlSpec value) { }
    public void draft(String id, String value) { }
    public void enabled(String id, boolean value) { enabled.put(id, value); }
    public AutoCloseable onAction(String id, Runnable value) { actions.put(id, value); return () -> actions.remove(id, value); }
    public AutoCloseable onLongAction(String id, Runnable value) { return onAction(id, value); }
    public AutoCloseable onValue(String id, Consumer<UiValue> value) { return () -> { }; }
    public AutoCloseable onDraft(String id, Consumer<String> value) { drafts.put(id, value); return () -> drafts.remove(id, value); }
    public AutoCloseable onPointer(String id, Consumer<UiPointer> value) { pointers.put(id, value); return () -> pointers.remove(id, value); }
    public void hudPosition(double x, double y) { }
    public void gameElement(String id, UiGameElement value) { icons.put(id, value); }
    public void rows(String id, UiRowProvider value, int max) { rows = value; limit = max; }
    public ScreenCoordinate bounds(String id) { return new ScreenCoordinate(0, 0, 400, 300); }
    public void requestClose() { closes++; }
}
