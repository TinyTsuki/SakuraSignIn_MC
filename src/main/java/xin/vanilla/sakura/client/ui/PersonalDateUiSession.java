package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.gui.search.ConfigSearchQuery;
import xin.vanilla.banira.client.gui.widget.ButtonWidget.PresetStyle;
import xin.vanilla.sakura.SakuraComponent;

import java.util.*;
import java.util.function.BooleanSupplier;

public final class PersonalDateUiSession implements UiPageSession {
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    @lombok.AllArgsConstructor
    public static class Entry {
        String key;
        String title;
        String calendar;
        String calendarId;
        String date;
        String section;
        boolean header;

        public Entry(String key, String title, String calendar, String date, String section, boolean header) {
            this(key, title, calendar, "", date, section, header);
        }
    }

    public interface Port {
        List<Entry> entries();

        void cycle(String key);

        void edit(String key);

        void save();

        default boolean dirty() {
            return false;
        }
    }

    private final Port port;
    private final BooleanSupplier connection;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private UiView view;
    private String query = "";
    private String section = "";
    private long generation, rowsRevision;
    private boolean closed;
    private final Map<String, Boolean> expanded = new HashMap<>();

    public boolean expanded(String section) {
        return expanded.getOrDefault(section, true);
    }

    public void expanded(String section, boolean value) {
        expanded.put(section, value);
    }

    public PersonalDateUiSession(Port port, BooleanSupplier connection) {
        this.port = Objects.requireNonNull(port);
        this.connection = Objects.requireNonNull(connection);
    }

    public String query() {
        return query;
    }

    public void query(String value) {
        query = Objects.requireNonNull(value);
    }

    public boolean acceptsChildResult() {
        return !closed && connection.getAsBoolean();
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
        long token = generation;
        next.text("title", SakuraComponent.get().transClient("word", "personal_date_config"));
        next.gameElement("brand", SakuraUiPages.moduleIcon());
        next.text("module_name", SakuraComponent.get().transClient("key", "categories"));
        next.text("origin", SakuraComponent.get().transClient("word", "aui_player_configuration"));
        next.text("category_title", SakuraComponent.get().transClient("word", "aui_categories"));
        next.text("breadcrumb", SakuraComponent.get().transClient("word", "personal_date_config"));
        next.text("save", BaniraComponent.get().transClientAuto("config_editor_save"));
        next.gameElement("close", UiGameElement.preset(PresetStyle.CLOSE));
        next.tooltip("close", BaniraComponent.get().transClientAuto("config_editor_close"));
        next.control("search", new UiControlSpec(UiControlSpec.Kind.TEXT, 32767, "", false));
        next.draft("search", query);
        next.text("search_hint", BaniraComponent.get().transClientAuto("config_editor_search_hint"));
        next.tooltip("search", BaniraComponent.get().transClientAuto("config_editor_search_hint"));
        next.gameElement("clear_search", UiGameElement.preset(PresetStyle.CLOSE));
        next.tooltip("clear_search", BaniraComponent.get().transClientAuto("config_editor_search_hint"));
        next.text("cancel", BaniraComponent.get().transClientAuto("config_editor_close"));
        bindings.add(next.onAction("clear_search", () -> {
            if (live(next, token)) {
                query("");
                next.draft("search", "");
                resetScroll();
                refresh();
            }
        }));
        bindings.add(next.onDraft("search", value -> {
            if (live(next, token)) {
                query(value);
                if (next.state() != null) next.state().rowPosition(0);
                refresh();
            }
        }));
        bindings.add(next.onAction("save", () -> {
            if (live(next, token)) {
                port.save();
                refresh();
            }
        }));
        bindings.add(next.onAction("close", () -> {
            if (view == next && generation == token) next.requestClose();
        }));
        bindings.add(next.onAction("cancel", () -> {
            if (view == next && generation == token) next.requestClose();
        }));
        refresh();
    }

    private void refresh() {
        if (view == null || view.nativeView() || closed) return;
        UiView owner = view;
        long token = generation, rowToken = ++rowsRevision;
        ConfigSearchQuery search = ConfigSearchQuery.of(query);
        List<Entry> all = port.entries();
        List<Entry> headers = all.stream().filter(Entry::header).collect(java.util.stream.Collectors.toList());
        if (!section.isEmpty() && headers.stream().noneMatch(header -> header.section().equals(section))) section = "";
        owner.text("category_count", SakuraComponent.get().literal(String.valueOf(headers.size())));
        owner.text("status", SakuraComponent.get().transClient("word", port.dirty() ? "aui_unsaved" : "aui_no_changes"));
        owner.enabled("save", connection.getAsBoolean());
        owner.rows("categories", new UiRowLayout() {
            public int rowHeight() {
                return 20;
            }

            public String scrollerId() {
                return "category_content";
            }

            public boolean tracksPageScroll() {
                return false;
            }

            public boolean textTools() {
                return true;
            }

            public int size() {
                return headers.size() + 1;
            }

            public String key(int index) {
                return index == 0 ? "all" : headers.get(index - 1).section();
            }

            public String rowClass(int index) {
                return "config-navigation" + (section.equals(index == 0 ? "" : headers.get(index - 1).section()) ? " config-selected" : "");
            }

            public void bind(UiView row, int index) {
                String selected = index == 0 ? "" : headers.get(index - 1).section();
                row.text("open", index == 0 ? SakuraComponent.get().transClient("word", "aui_all_dates") : SakuraComponent.get().literal(headers.get(index - 1).title()));
                row.tooltip("open", index == 0 ? SakuraComponent.get().transClient("word", "aui_all_dates") : SakuraComponent.get().literal(headers.get(index - 1).title() + "\n" + selected));
                row.onAction("open", () -> {
                    if (live(owner, token) && rowsRevision == rowToken) {
                        section = selected;
                        resetScroll();
                        refresh();
                    }
                });
            }
        }, 32);
        Set<String> matches = new HashSet<>(), headerMatches = new HashSet<>();
        for (Entry entry : all)
            if (search.matches(entry.key(), entry.title(), entry.calendar(), entry.calendarId(), entry.date())) {
                matches.add(entry.section());
                if (entry.header()) headerMatches.add(entry.section());
            }
        List<Entry> entries = all.stream().filter(row -> section.isEmpty() || section.equals(row.section())).filter(row -> row.header() ? matches.contains(row.section())
                : (search.isEmpty() ? expanded(row.section()) : search.matches(row.key(), row.title(), row.calendar(), row.calendarId(), row.date())
                || headerMatches.contains(row.section()))).collect(java.util.stream.Collectors.toList());
        owner.text("empty", entries.isEmpty() ? SakuraComponent.get().transClient("word", "personal_date_no_presets") : SakuraComponent.get().literal(""));
        owner.enabled("empty", entries.isEmpty());
        long total = all.stream().filter(row -> !row.header()).count(), shown = entries.stream().filter(row -> !row.header()).count();
        owner.text("entry_count", SakuraComponent.get().literal(shown + " / " + total));
        owner.rows("fields", new UiRowLayout() {
            public int rowHeight() {
                return 28;
            }

            public boolean textTools() {
                return true;
            }

            public String rowClass(int index) {
                return entries.get(index).header() ? "config-navigation date-group" : "date-slot";
            }

            public int size() {
                return entries.size();
            }

            public String key(int index) {
                return entries.get(index).key();
            }

            public void bind(UiView row, int index) {
                Entry entry = entries.get(index);
                if (entry.header()) {
                    row.text("open", SakuraComponent.get().literal((expanded(entry.section()) ? "- " : "+ ") + entry.title()));
                    row.tooltip("open", SakuraComponent.get().literal(entry.title()));
                    row.onAction("open", () -> {
                        if (live(owner, token) && rowsRevision == rowToken) {
                            expanded(entry.section(), !expanded(entry.section()));
                            refresh();
                        }
                    });
                    return;
                }
                String full = entry.title() + "\n" + entry.calendar() + "  " + entry.date();
                row.text("label", SakuraComponent.get().literal(entry.title()));
                row.text("detail", SakuraComponent.get().literal(entry.key()));
                row.text("open", SakuraComponent.get().literal(entry.calendar()));
                row.text("apply", SakuraComponent.get().literal(entry.date()));
                for (String id : com.google.common.collect.ImmutableList.of("label", "open", "apply"))
                    row.tooltip(id, SakuraComponent.get().literal(full));
                row.onAction("open", () -> {
                    if (live(owner, token) && rowsRevision == rowToken) {
                        port.cycle(entry.key());
                        refresh();
                    }
                });
                row.onAction("apply", () -> {
                    if (live(owner, token) && rowsRevision == rowToken) port.edit(entry.key());
                });
            }
        }, 120);
    }

    private void resetScroll() {
        if (view.state() != null) {
            view.state().rowPosition(0);
            view.state().scrollOffset(0);
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
        rowsRevision++;
        view = null;
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
    }
}
