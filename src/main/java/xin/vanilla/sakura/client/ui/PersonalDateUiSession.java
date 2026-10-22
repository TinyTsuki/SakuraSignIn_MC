package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.gui.search.ConfigSearchQuery;
import xin.vanilla.banira.client.gui.widget.ButtonWidget.PresetStyle;
import xin.vanilla.sakura.SakuraComponent;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class PersonalDateUiSession implements UiPageSession {
    public record Entry(String key, String title, String calendar, String calendarId, String date, String section, boolean header) {
        public Entry(String key, String title, String calendar, String date, String section, boolean header) {
            this(key, title, calendar, "", date, section, header);
        }
    }
    public interface Port {
        List<Entry> entries();
        void cycle(String key);
        void edit(String key);
        void save();
        void commonConfig();
    }
    private final Port port;
    private final BooleanSupplier connection;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private UiView view;
    private String query = "";
    private long generation, rowsRevision;
    private boolean closed;
    private final Map<String, Boolean> expanded = new HashMap<>();
    public boolean expanded(String section) { return expanded.getOrDefault(section, true); }
    public void expanded(String section, boolean value) { expanded.put(section, value); }
    public PersonalDateUiSession(Port port, BooleanSupplier connection) {
        this.port = Objects.requireNonNull(port); this.connection = Objects.requireNonNull(connection);
    }
    public String query() { return query; }
    public void query(String value) { query = Objects.requireNonNull(value); }
    public boolean acceptsChildResult() { return !closed && connection.getAsBoolean(); }
    private boolean live(UiView owner, long token) { return !closed && view == owner && generation == token && connection.getAsBoolean(); }
    @Override public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = Objects.requireNonNull(next);
        if (next.nativeView()) return;
        long token = generation;
        next.text("title", SakuraComponent.get().transClient("word", "personal_date_config"));
        next.text("save", BaniraComponent.get().transClientAuto("config_editor_save"));
        next.text("common", SakuraComponent.get().transClient("word", "banira_player_config"));
        next.gameElement("close", UiGameElement.preset(PresetStyle.CLOSE));
        next.tooltip("close", BaniraComponent.get().transClientAuto("config_editor_close"));
        next.control("search", new UiControlSpec(UiControlSpec.Kind.TEXT, 32767, "", false));
        next.draft("search", query);
        next.tooltip("search", BaniraComponent.get().transClientAuto("config_search_hint"));
        bindings.add(next.onDraft("search", value -> { if (live(next, token)) { query(value); if (next.state() != null) next.state().rowPosition(0); refresh(); } }));
        bindings.add(next.onAction("save", () -> { if (live(next, token)) port.save(); }));
        bindings.add(next.onAction("common", () -> { if (live(next, token)) port.commonConfig(); }));
        bindings.add(next.onAction("close", () -> { if (view == next && generation == token) next.requestClose(); }));
        refresh();
    }
    private void refresh() {
        if (view == null || view.nativeView() || closed) return;
        UiView owner = view;
        long token = generation, rowToken = ++rowsRevision;
        ConfigSearchQuery search = ConfigSearchQuery.of(query);
        List<Entry> all = port.entries();
        Set<String> matches = new HashSet<>(), headerMatches = new HashSet<>();
        for (Entry entry : all) if (search.matches(entry.key(), entry.title(), entry.calendar(), entry.calendarId(), entry.date())) {
            matches.add(entry.section()); if (entry.header()) headerMatches.add(entry.section());
        }
        List<Entry> entries = all.stream().filter(row -> row.header() ? matches.contains(row.section())
                : (search.isEmpty() ? expanded(row.section()) : search.matches(row.key(), row.title(), row.calendar(), row.calendarId(), row.date())
                || headerMatches.contains(row.section()))).toList();
        owner.text("empty", entries.isEmpty() ? SakuraComponent.get().transClient("word", "personal_date_no_presets") : SakuraComponent.get().literal(""));
        owner.rows("fields", new UiRowProvider() {
            public int size() { return entries.size(); }
            public String key(int index) { return entries.get(index).key(); }
            public void bind(UiView row, int index) {
                Entry entry = entries.get(index);
                if (entry.header()) {
                    row.text("open", SakuraComponent.get().literal((expanded(entry.section()) ? "- " : "+ ") + entry.title()));
                    row.tooltip("open", SakuraComponent.get().literal(entry.title()));
                    row.onAction("open", () -> {
                        if (live(owner, token) && rowsRevision == rowToken) { expanded(entry.section(), !expanded(entry.section())); refresh(); }
                    });
                    return;
                }
                String full = entry.title() + "\n" + entry.calendar() + "  " + entry.date();
                row.text("label", SakuraComponent.get().literal(entry.title()));
                row.text("open", SakuraComponent.get().literal(entry.calendar()));
                row.text("apply", SakuraComponent.get().literal(entry.date()));
                for (String id : List.of("label", "open", "apply")) row.tooltip(id, SakuraComponent.get().literal(full));
                row.onAction("open", () -> { if (live(owner, token) && rowsRevision == rowToken) { port.cycle(entry.key()); refresh(); } });
                row.onAction("apply", () -> { if (live(owner, token) && rowsRevision == rowToken) port.edit(entry.key()); });
            }
        }, 120);
    }
    public boolean requestClose() { if (view == null || closed) return false; view.requestClose(); return true; }
    @Override public void detach(UiDetachReason reason) {
        generation++; rowsRevision++; view = null;
        for (AutoCloseable binding : bindings) {
            try { binding.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        }
        bindings.clear();
    }
    @Override public void close(UiCloseReason reason) { detach(UiDetachReason.DISCONNECT); closed = true; }
}
