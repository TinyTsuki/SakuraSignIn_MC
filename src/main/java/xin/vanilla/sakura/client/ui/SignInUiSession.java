package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.Texture;
import xin.vanilla.banira.client.gui.widget.ButtonWidget.PresetStyle;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class SignInUiSession implements UiPageSession {
    public record Cell(int date, Component day, Component tooltip, Texture icon, boolean currentMonth, boolean actionable) {
        public Cell(int date, Component day, Component tooltip, Texture icon) {
            this(date, day, tooltip, icon, true, true);
        }
    }

    public record RewardLine(Component name, Component probability, UiGameElement icon) {
    }

    public interface Port {
        long revision();

        void refresh();

        default void reload() {
            refresh();
        }

        List<Cell> cells();

        Component title();

        Component info();

        List<Component> weekdays();

        Component tips();

        boolean openingTips();

        void dismissTips(boolean persist);

        void move(int months, int years);

        void activate(int date);

        void theme(int index, boolean special);

        Texture themeIcon(int index);

        boolean key(int code, int scan, boolean pressed);

        default List<Component> statistics() {
            return List.of(info(), empty(), empty());
        }

        default int today() {
            return 0;
        }

        default Component status(int date) {
            return empty();
        }

        default boolean detailsVisible(int date) {
            return true;
        }

        default List<RewardLine> rewards(int date) {
            return List.of();
        }

        default long detailRevision() {
            return 0;
        }
    }

    private final Port port;
    private final BooleanSupplier connection;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private UiView view;
    private long generation, revision = Long.MIN_VALUE;
    private boolean closed;
    private int pressedTheme = -1;
    private List<Cell> cells = List.of();
    private int selectedDate;
    private long detailRevision = Long.MIN_VALUE;

    public SignInUiSession(Port port, BooleanSupplier connection) {
        this.port = Objects.requireNonNull(port);
        this.connection = Objects.requireNonNull(connection);
    }

    private boolean live(UiView owner, long token) {
        return !closed && view == owner && generation == token && connection.getAsBoolean();
    }

    @Override
    public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = Objects.requireNonNull(next);
        if (next.nativeView()) return;
        port.reload();
        long token = generation;
        next.gameElement("brand", SakuraUiPages.moduleIcon());
        next.gameElement("close_icon", UiGameElement.preset(PresetStyle.CLOSE));
        text("detail_heading", tr("aui_reward_details"));
        text("cards_label", tr("aui_cards"));
        text("continuous_label", tr("aui_consecutive"));
        text("total_label", tr("aui_total"));
        action("previous-month", next, token, () -> port.move(-1, 0));
        action("next-month", next, token, () -> port.move(1, 0));
        action("previous-year", next, token, () -> port.move(0, -1));
        action("next-year", next, token, () -> port.move(0, 1));
        icon("previous-month", PresetStyle.ARROW_LEFT);
        icon("next-month", PresetStyle.ARROW_RIGHT);
        icon("previous-year", PresetStyle.ARROW_UP);
        icon("next-year", PresetStyle.ARROW_DOWN);
        next.tooltip("previous-month", SakuraComponent.get().transClient("format", "use_s_key", "←"));
        next.tooltip("next-month", SakuraComponent.get().transClient("format", "use_s_key", "→"));
        next.tooltip("previous-year", SakuraComponent.get().transClient("format", "use_s_key", "↑"));
        next.tooltip("next-year", SakuraComponent.get().transClient("format", "use_s_key", "↓"));
        bindings.add(next.onAction("close", () -> {
            if (view == next && generation == token) next.requestClose();
        }));
        icon("close", PresetStyle.CLOSE);
        next.tooltip("close", xin.vanilla.banira.BaniraComponent.get().transClientAuto("config_editor_close"));
        bindings.add(next.onAction("tips-confirm", () -> {
            if (live(next, token)) {
                port.dismissTips(false);
                refresh();
            }
        }));
        bindings.add(next.onAction("tips-disable", () -> {
            if (live(next, token)) {
                port.dismissTips(true);
                refresh();
            }
        }));
        text("tips-confirm", tr("confirm"));
        text("tips-disable", tr("no_remind"));
        for (int index = 0; index < 5; index++) {
            int theme = index;
            action("theme" + index, next, token, () -> port.theme(theme, false));
            bindings.add(next.onPointer("theme" + index, event -> {
                if (event.kind() == UiPointer.Kind.DOWN && event.button() == 1) {
                    if (live(next, token) && !port.openingTips() && inside(next, "theme" + theme, event))
                        pressedTheme = theme;
                } else if (event.kind() == UiPointer.Kind.UP && pressedTheme == theme) {
                    pressedTheme = -1;
                    if (event.button() == 1 && live(next, token) && !port.openingTips() && inside(next, "theme" + theme, event)) {
                        port.theme(theme, true);
                        refresh();
                    }
                }
            }));
            next.tooltip("theme" + index, tr("click_to_change_theme"));
        }
        for (int index = 0; index < 42; index++) {
            int position = index;
            bindings.add(next.onPointer("day" + index, pointer -> {
                if (pointer.kind() == UiPointer.Kind.MOVE && live(next, token) && !port.openingTips()
                        && position < cells.size() && cells.get(position).actionable() && cells.get(position).date() != selectedDate
                        && inside(next, "day" + position, pointer)) select(cells.get(position));
            }));
            bindings.add(next.onAction("day" + index, () -> {
                if (!live(next, token) || port.openingTips() || position >= cells.size() || !cells.get(position).actionable()) return;
                Cell selected = cells.get(position);
                select(selected);
                port.activate(selected.date());
                refresh();
            }));
        }
        refresh();
    }

    private void action(String id, UiView owner, long token, Runnable action) {
        bindings.add(owner.onAction(id, () -> {
            if (live(owner, token) && !port.openingTips()) {
                action.run();
                refresh();
            }
        }));
    }

    private static boolean inside(UiView owner, String id, UiPointer pointer) {
        var bounds = owner.bounds(id);
        return pointer.finite() && bounds != null && Double.isFinite(bounds.x()) && Double.isFinite(bounds.y())
                && Double.isFinite(bounds.width()) && Double.isFinite(bounds.height()) && bounds.width() > 0 && bounds.height() > 0
                && pointer.x() >= bounds.x() && pointer.x() < bounds.x() + bounds.width()
                && pointer.y() >= bounds.y() && pointer.y() < bounds.y() + bounds.height();
    }

    private void icon(String id, PresetStyle icon) {
        view.gameElement(id, UiGameElement.preset(icon));
    }

    private void text(String id, Component text) {
        view.text(id, text);
        view.tooltip(id, text);
    }

    private static Component tr(String key) {
        return SakuraComponent.get().transClient("word", key);
    }

    private static Component empty() {
        return SakuraComponent.get().literal("");
    }

    private void refresh() {
        if (view == null || view.nativeView() || closed) return;
        long next = port.revision();
        if (next == revision) return;
        revision = next;
        detailRevision = port.detailRevision();
        cells = List.copyOf(port.cells());
        if (cells.size() > 42) throw new IllegalArgumentException("Calendar cell budget");
        view.gameElement("brand", SakuraUiPages.moduleIcon());
        for (String name : List.of("not_signed", "unclaimed", "claimed")) {
            view.gameElement("legend_" + name, UiGameElement.sprite(xin.vanilla.sakura.client.theme.SakuraThemeTextures.status(
                    name.equals("not_signed") ? "not_signed_in" : name.equals("claimed") ? "rewarded" : "signed_in").texture()));
            text("legend_" + name + "_label", tr("aui_" + name));
        }
        text("title", tr("aui_calendar"));
        text("month", port.title());
        text("tips", port.tips());
        view.enabled("tips_panel", port.openingTips());
        List<Component> statistics = port.statistics();
        text("cards", statistics.get(0));
        text("continuous", statistics.get(1));
        text("total", statistics.get(2));
        List<Component> weekdays = port.weekdays();
        for (int index = 0; index < 7; index++) text("weekday" + index, weekdays.get(index));
        view.enabled("tips-confirm", port.openingTips());
        view.enabled("tips-disable", port.openingTips());
        for (String id : List.of("previous-month", "next-month", "previous-year", "next-year"))
            view.enabled(id, connection.getAsBoolean() && !port.openingTips());
        for (int index = 0; index < 5; index++) {
            view.gameElement("theme" + index, UiGameElement.sprite(port.themeIcon(index)));
            view.enabled("theme" + index, connection.getAsBoolean() && !port.openingTips());
        }
        for (int index = 0; index < 42; index++) {
            Cell cell = index < cells.size() ? cells.get(index) : null;
            for (String kind : List.of("current", "adjacent")) {
                String id = "day" + index + "_" + kind;
                text(id, cell == null ? empty() : cell.day());
                view.enabled(id, cell != null && cell.currentMonth() == kind.equals("current"));
            }
            view.gameElement("status" + index, cell == null || cell.icon() == null ? null : UiGameElement.sprite(cell.icon()));
            view.enabled("day" + index, cell != null && cell.actionable() && !port.openingTips() && connection.getAsBoolean());
            view.tooltip("day" + index, empty());
        }
        Cell selection = cells.stream().filter(cell -> cell.date() == selectedDate).findFirst()
                .orElseGet(() -> cells.stream().filter(cell -> cell.date() == port.today()).findFirst()
                        .orElseGet(() -> cells.stream().filter(Cell::currentMonth).findFirst().orElse(cells.isEmpty() ? null : cells.get(0))));
        if (selection == null) {
            selectedDate = 0;
            renderDetails(null);
        } else select(selection);
    }

    private void select(Cell cell) {
        if (selectedDate != cell.date() && view.state() != null) {
            view.state().rowPosition(0);
            view.state().detailScroll(0);
        }
        selectedDate = cell.date();
        if (view.state() != null) view.state().selectedId(Integer.toString(selectedDate));
        renderDetails(cell);
    }

    private void renderDetails(Cell cell) {
        text("detail_date", SakuraComponent.get().literal(cell == null ? "" : String.format(Locale.ROOT, "%02d / %02d", cell.date() / 100 % 100, cell.date() % 100)));
        text("detail_status", cell == null ? empty() : port.status(cell.date()));
        List<RewardLine> rewards = cell == null || !port.detailsVisible(cell.date()) ? List.of() : List.copyOf(port.rewards(cell.date()));
        text("empty", cell != null && !port.detailsVisible(cell.date()) ? tr("aui_details_hidden") : rewards.isEmpty() ? tr("aui_no_rewards") : empty());
        view.enabled("empty", rewards.isEmpty());
        view.text("detail_count", SakuraComponent.get().literal(Integer.toString(rewards.size())));
        int date = selectedDate;
        view.rows("fields", new UiRowLayout() {
            public int rowHeight() {
                return 32;
            }

            public int size() {
                return rewards.size();
            }

            public String key(int index) {
                return date + ":reward:" + index;
            }

            public String rowClass(int index) {
                return "signin-reward";
            }

            public void bind(UiView row, int index) {
                RewardLine reward = rewards.get(index);
                row.text("label", reward.name());
                row.tooltip("label", reward.name());
                row.text("detail", reward.probability());
                row.tooltip("detail", reward.probability());
                row.gameElement("icon", reward.icon());
                row.tooltip("icon", reward.name());
            }
        }, 40);
    }

    @Override
    public boolean key(int key, int scan, int modifiers, boolean pressed) {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean()) return false;
        boolean handled = port.key(key, scan, pressed);
        if (handled) refresh();
        return handled;
    }

    @Override
    public void tick() {
        if (view == null || view.nativeView() || closed) return;
        if (connection.getAsBoolean() && (port.revision() != revision || port.detailRevision() != detailRevision)) {
            if (port.detailRevision() != detailRevision) revision = Long.MIN_VALUE;
            port.refresh();
            refresh();
        }
    }

    public boolean requestClose() {
        if (view == null || closed) return false;
        view.requestClose();
        return true;
    }

    @Override
    public void detach(UiDetachReason reason) {
        generation++;
        view = null;
        revision = Long.MIN_VALUE;
        pressedTheme = -1;
        for (AutoCloseable binding : bindings) {
            try {
                binding.close();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
        bindings.clear();
    }

    @Override
    public void close(UiCloseReason reason) {
        detach(UiDetachReason.DISCONNECT);
        closed = true;
        cells = List.of();
    }
}
