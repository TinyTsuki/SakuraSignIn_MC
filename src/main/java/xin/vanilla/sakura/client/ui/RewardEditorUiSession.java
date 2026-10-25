package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.gui.search.ConfigSearchQuery;
import xin.vanilla.banira.client.gui.widget.ButtonWidget.PresetStyle;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.client.gui.RewardKeyboardNavigator.Direction;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.client.data.Texture;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.BooleanSupplier;

public final class RewardEditorUiSession implements UiPageSession {
    public enum Action {
        ADD("add", "ui_reward_add", PresetStyle.PLUS), COPY("copy", "copy", null),
        CUT("cut", "cut", null), PASTE("paste", "paste", null),
        DELETE("delete", "delete", PresetStyle.CLOSE), UNDO("undo", "ui_reward_undo", PresetStyle.ARROW_LEFT),
        REDO("redo", "ui_reward_redo", PresetStyle.ARROW_RIGHT), SORT("sort", "reward_rule_sort", PresetStyle.ARROW_DOWN),
        MERGE("merge", "merge_same_rewards", null), UPLOAD("upload", "upload_reward_config", PresetStyle.ARROW_UP),
        DOWNLOAD("download", "download_reward_config", PresetStyle.ARROW_DOWN), FOLDER("folder", "open_config_folder", null),
        HELP("help", "help_button", null), MOVE("move", "ui_reward_move", PresetStyle.MOVE),
        CLEAR("clear", "clear", PresetStyle.MINUS), SIDEBAR("sidebar", "reward_rule_type", PresetStyle.SETTINGS);
        public final String id, label;
        public final PresetStyle icon;
        Action(String id, String label, PresetStyle icon) { this.id = id; this.label = label; this.icon = icon; }
    }
    public enum EntryAction { SELECT, TOGGLE, GROUP_SELECT, CONTEXT, EDIT, PROBABILITY, DELETE }
    public record Entry(String id, String label, String detail, String tooltip, UiGameElement icon,
                        boolean group, boolean collapsed, boolean selected, boolean editable) { }
    public record Fact(Component label, Component value) { }
    public record Detail(String id, String title, String subtitle, String description, UiGameElement icon,
                         List<Fact> facts, boolean editable, boolean probability) {
        public Detail { facts = List.copyOf(facts); }
    }
    public interface Port {
        long revision();
        void refresh();
        List<Entry> entries();
        int rule();
        void rule(int rule);
        boolean redacted();
        boolean enabled(Action action);
        boolean sidebarOpen();
        void command(Action action);
        void entry(String id, EntryAction action);
        void suspend();
        void closed(UiCloseReason reason);
        boolean key(int key, int scan, int modifiers, boolean pressed);
        default void tick() { }
        default Detail detail() { return null; }
        default int ruleCount(int rule) { return -1; }
        default Component status() { return tr("aui_no_changes"); }
        default Component probability(String id) { return SakuraComponent.get().literal("%"); }
    }
    private final Port port;
    private final BooleanSupplier connection;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private final Map<UiView, List<AutoCloseable>> rowBindings = new LinkedHashMap<>();
    private final Map<UiView, Entry> rowEntries = new IdentityHashMap<>();
    private static final Map<String, UiGameElement> ICONS = new HashMap<>();
    private UiView view;
    private String query = "";
    private String revealId;
    private List<String> navigationIds = List.of();
    private long generation, rowRevision, revision = Long.MIN_VALUE;
    private boolean closed;
    private boolean moreOpen, detailOpen, wide;
    private Detail selectedDetail;
    private int chromeState = -1;
    private static final String[] RULE_KEYS = {"reward_base", "reward_continuous", "reward_cycle", "reward_year",
            "reward_month", "reward_week", "reward_time", "reward_cumulative", "reward_random", "reward_cdk",
            "reward_personal_date", "reward_lottery"};

    public RewardEditorUiSession(Port port, BooleanSupplier connection) {
        this.port = Objects.requireNonNull(port); this.connection = Objects.requireNonNull(connection);
    }
    public String query() { return query; }
    public void query(String value) { query = Objects.requireNonNull(value); }
    public int boundRows() { return rowBindings.size(); }
    public List<String> navigationIds() { return navigationIds; }
    public void reveal(String id) { revealId = Objects.requireNonNull(id); }
    public static Direction listDirection(Direction direction) {
        return direction == Direction.UP ? Direction.LEFT : direction == Direction.DOWN ? Direction.RIGHT : direction;
    }
    private boolean live(UiView owner, long token) { return !closed && view == owner && generation == token && connection.getAsBoolean(); }
    private boolean detailBlocks() { return detailOpen && !wide; }
    @Override public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = Objects.requireNonNull(next);
        if (next.nativeView()) return;
        long token = generation;
        next.gameElement("brand", SakuraUiPages.moduleIcon());
        next.text("title", tr("reward_option_title"));
        next.control("search", new UiControlSpec(UiControlSpec.Kind.TEXT, 32767, "", false));
        next.draft("search", query);
        Component searchHint = BaniraComponent.get().transClientAuto("config_editor_search_hint");
        next.text("search_hint", searchHint); next.tooltip("search", searchHint);
        bindings.add(next.onAction("clear_search", () -> {
            if (live(next, token)) { query = ""; next.draft("search", ""); if (next.state() != null) next.state().rowPosition(0); refresh(); }
        }));
        bindings.add(next.onDraft("search", text -> { if (live(next, token)) { query(text); if (next.state() != null) next.state().rowPosition(0); refresh(); } }));
        for (Action action : Action.values()) {
            next.gameElement(action.id + "_icon", icon(action.id));
            next.text(action.id + "_label", tr(action.label));
            next.tooltip(action.id, tr(action.label));
            bindings.add(next.onAction(action.id, () -> {
                boolean outside = action == Action.UPLOAD || action == Action.DOWNLOAD || action == Action.FOLDER || action == Action.HELP;
                if (live(next, token) && (!detailBlocks() || outside) && port.enabled(action)) { moreOpen = false; clearFocus(next); port.command(action); if (live(next, token)) refresh(); }
            }));
        }
        for (ERewardRule rule : ERewardRule.values()) {
            String id = "rule" + rule.getCode();
            next.text(id, ruleName(rule.getCode())); next.tooltip(id, tr("reward_rule_description_" + rule.getCode()));
            bindings.add(next.onAction(id, () -> {
                if (!live(next, token) || detailBlocks()) return;
                clearFocus(next);
                moreOpen = false; detailOpen = false;
                port.rule(rule.getCode()); if (next.state() != null) next.state().rowPosition(0); refresh();
            }));
        }
        next.gameElement("close", UiGameElement.preset(PresetStyle.CLOSE));
        next.gameElement("close_icon", UiGameElement.preset(PresetStyle.CLOSE));
        for (String id : List.of("more", "details")) next.gameElement(id + "_icon", icon(id));
        next.tooltip("more", tr("aui_more")); next.tooltip("details", tr("aui_reward_details"));
        next.text("detail_heading", tr("aui_reward_details"));
        next.text("detail_edit", tr("edit")); next.text("detail_probability", tr("edit_probability"));
        next.text("detail_add", tr("ui_reward_add"));
        next.gameElement("close_details", UiGameElement.preset(PresetStyle.CLOSE));
        bindings.add(next.onAction("more", () -> { if (live(next, token)) { clearFocus(next); detailOpen = false; moreOpen = !moreOpen; chrome(); } }));
        bindings.add(next.onAction("details", () -> { if (live(next, token)) { clearFocus(next); moreOpen = false; detailOpen = !detailOpen; chrome(); } }));
        bindings.add(next.onPointer("workspace", pointer -> {
            if (!live(next, token) || !moreOpen || pointer.kind() != UiPointer.Kind.DOWN || pointer.button() != 0 || !pointer.finite()) return;
            var bounds = next.bounds("more_panel");
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0
                    && (pointer.x() < bounds.x() || pointer.x() >= bounds.x() + bounds.width()
                    || pointer.y() < bounds.y() || pointer.y() >= bounds.y() + bounds.height())) {
                moreOpen = false; clearFocus(next); chrome();
            }
        }));
        for (String id : List.of("close_details", "inspector_shade"))
            bindings.add(next.onAction(id, () -> { if (live(next, token)) { detailOpen = false; chrome(); } }));
        bindings.add(next.onAction("detail_edit", () -> detailAction(next, token, EntryAction.EDIT)));
        bindings.add(next.onAction("detail_probability", () -> detailAction(next, token, EntryAction.PROBABILITY)));
        bindings.add(next.onAction("detail_add", () -> {
            if (live(next, token) && port.enabled(Action.ADD)) { port.command(Action.ADD); if (live(next, token)) refresh(); }
        }));
        next.tooltip("close", BaniraComponent.get().transClientAuto("config_editor_close"));
        bindings.add(next.onAction("close", () -> { if (view == next && generation == token) next.requestClose(); }));
        refresh();
    }
    private static xin.vanilla.banira.common.data.Component tr(String key) { return SakuraComponent.get().transClient("word", key); }
    private static Component ruleName(int code) { return code >= 1 && code <= RULE_KEYS.length ? tr(RULE_KEYS[code - 1]) : tr("reward_rule_type"); }
    private static UiGameElement icon(String name) {
        return ICONS.computeIfAbsent(name, key -> UiGameElement.sprite(Texture.of(new ResourceLocation("apricityui",
                "apricity/sakura_sign_in/icons/reward-" + key + ".png"), 32, 32)));
    }
    private void chrome() {
        if (view == null) return;
        var bounds = view.bounds("workspace");
        wide = bounds != null && bounds.width() >= 430;
        boolean hint = query.isEmpty() && (view.state() == null || !"search".equals(view.state().focusedField()));
        int state = (wide ? 1 : 0) | (detailOpen ? 2 : 0) | (moreOpen ? 4 : 0) | (hint ? 8 : 0);
        if (state == chromeState) return;
        chromeState = state;
        view.enabled("inspector_dock", wide);
        view.enabled("inspector", wide || detailOpen);
        view.enabled("inspector_shade", !wide && detailOpen);
        view.enabled("close_details", !wide);
        view.enabled("more_panel", moreOpen);
        view.enabled("search_hint", hint);
        view.gameElement("detail_icon", (wide || detailOpen) && selectedDetail != null ? selectedDetail.icon() : null);
        view.gameElement("close_details", !wide && detailOpen ? UiGameElement.preset(PresetStyle.CLOSE) : null);
        boolean blocked = !wide && detailOpen;
        boolean covered = blocked || moreOpen;
        for (Action action : Action.values()) {
            boolean menu = switch (action) { case CUT, MOVE, SORT, MERGE, CLEAR, SIDEBAR -> true; default -> false; };
            boolean visible = menu ? moreOpen && !blocked : !covered || action == Action.UPLOAD
                    || action == Action.DOWNLOAD || action == Action.FOLDER || action == Action.HELP;
            view.gameElement(action.id + "_icon", visible ? icon(action.id) : null);
        }
        view.gameElement("more_icon", covered ? null : icon("more"));
        view.gameElement("details_icon", covered ? null : icon("details"));
        rowEntries.forEach((row, entry) -> rowSprites(row, entry));
    }
    private void rowSprites(UiView row, Entry entry) {
        boolean visible = !moreOpen && (wide || !detailOpen);
        row.gameElement("icon", visible ? entry.group() ? icon(entry.collapsed() ? "chevron_right" : "chevron_down") : entry.icon() : null);
        row.gameElement("apply", visible && entry.editable() ? icon("edit") : null);
        row.gameElement("delete", visible && (!entry.group() || entry.editable()) ? icon("delete") : null);
    }
    private void detailAction(UiView owner, long token, EntryAction action) {
        if (!live(owner, token) || selectedDetail == null || port.redacted() || revision != port.revision()) return;
        if (action == EntryAction.EDIT ? !selectedDetail.editable() : !selectedDetail.probability()) return;
        clearFocus(owner); port.entry(selectedDetail.id(), action); if (live(owner, token)) refresh();
    }
    private void inspector(List<Entry> entries) {
        Detail detail = port.redacted() ? null : port.detail();
        if (detail == null && !port.redacted()) {
            Entry selected = entries.stream().filter(Entry::selected).findFirst().orElse(null);
            if (selected != null) detail = new Detail(selected.id(), selected.label(), selected.detail(), selected.tooltip(),
                    selected.icon(), List.of(), selected.editable(), !selected.group());
        }
        selectedDetail = detail;
        chromeState = -1;
        view.text("detail_title", detail == null ? ruleName(port.rule()) : SakuraComponent.get().literal(detail.title()));
        view.text("detail_subtitle", SakuraComponent.get().literal(detail == null ? "" : detail.subtitle()));
        view.text("detail_description", port.redacted() ? tr("reward_rule_permission_denied_page")
                : detail == null ? port.rule() == 0 ? tr("reward_rule_type") : tr("reward_rule_description_" + port.rule()) : SakuraComponent.get().literal(detail.description()));
        for (int index = 0; index < 12; index++) {
            Fact fact = detail != null && index < detail.facts().size() ? detail.facts().get(index) : null;
            view.enabled("detail_fact" + index, fact != null);
            view.text("detail_fact_label" + index, fact == null ? SakuraComponent.get().literal("") : fact.label());
            view.text("detail_fact_value" + index, fact == null ? SakuraComponent.get().literal("") : fact.value());
            view.tooltip("detail_fact_value" + index, fact == null ? SakuraComponent.get().literal("") : fact.value());
        }
        view.enabled("detail_edit", detail != null && detail.editable());
        view.enabled("detail_probability", detail != null && detail.probability());
        view.enabled("detail_add", port.enabled(Action.ADD));
    }
    private void refresh() {
        if (view == null || view.nativeView() || closed) return;
        clearRows();
        port.refresh(); revision = port.revision();
        UiView owner = view;
        long token = generation, rowsToken = ++rowRevision;
        for (Action action : Action.values()) owner.enabled(action.id, port.enabled(action));
        for (ERewardRule rule : ERewardRule.values()) {
            int count = port.ruleCount(rule.getCode());
            owner.text("rule" + rule.getCode(), SakuraComponent.get().literal((port.rule() == rule.getCode() ? "> " : "")
                    + ruleName(rule.getCode()) + (count >= 0 ? "  " + count : "")));
            owner.enabled("rule" + rule.getCode(), port.sidebarOpen());
        }
        owner.enabled("rules", port.sidebarOpen());
        ConfigSearchQuery search = ConfigSearchQuery.of(query);
        List<Entry> all = port.redacted() ? List.of() : port.entries();
        List<Entry> entries = all.stream().filter(entry -> search.matches(entry.id(), entry.label(), entry.detail(), entry.tooltip())).toList();
        owner.text("section", ruleName(port.rule())); owner.text("status", port.status());
        owner.text("count", SakuraComponent.get().literal(entries.stream().filter(entry -> !entry.group()).count() + " / "
                + all.stream().filter(entry -> !entry.group()).count()));
        inspector(all); chrome();
        navigationIds = entries.stream().filter(entry -> !entry.group()).map(Entry::id).toList();
        if (revealId != null && owner.state() != null) {
            for (int index = 0; index < entries.size(); index++) if (entries.get(index).id().equals(revealId)) {
                owner.state().rowPosition(index); break;
            }
            revealId = null;
        }
        owner.text("empty", port.redacted() ? tr("reward_rule_permission_denied_page")
                : port.rule() == 0 ? tr("reward_rule_type") : entries.isEmpty() ? tr("aui_no_rewards") : SakuraComponent.get().literal(""));
        owner.rows("fields", new UiRowLayout() {
            public int rowHeight() { return 28; }
            public boolean textTools() { return true; }
            public String rowClass(int index) { Entry entry = entries.get(index); return "row reward-entry"
                    + (entry.group() ? " reward-group" : "") + (entry.selected() ? " reward-selected" : ""); }
            public int size() { return entries.size(); }
            public String key(int index) { return port.rule() + ":" + entries.get(index).id(); }
            public void bind(UiView row, int index) {
                if (!live(owner, token) || rowRevision != rowsToken) return;
                List<AutoCloseable> old = rowBindings.remove(row); release(old);
                if (rowBindings.size() >= 120) { UiView first = rowBindings.keySet().iterator().next(); release(rowBindings.remove(first)); rowEntries.remove(first); }
                List<AutoCloseable> owned = new ArrayList<>(); rowBindings.put(row, owned);
                Entry entry = entries.get(index);
                rowEntries.put(row, entry);
                row.text("open", SakuraComponent.get().literal(entry.label()));
                row.text("label", SakuraComponent.get().literal(entry.detail()));
                row.value("input", UiValue.bool(entry.selected())); row.tooltip("input", tr("aui_select"));
                owned.add(row.onValue("input", value -> {
                    if (value.kind() == UiValue.Kind.BOOLEAN && value.asBoolean() != entry.selected()
                            && live(owner, token) && !detailBlocks() && rowRevision == rowsToken && rowBindings.get(row) == owned && !port.redacted()) {
                        clearFocus(owner); port.entry(entry.id(), EntryAction.TOGGLE); if (live(owner, token)) refresh();
                    }
                }));
                row.tooltip("open", SakuraComponent.get().literal(entry.tooltip()));
                bindEntry(row, owned, "open", entry, EntryAction.SELECT, owner, token, rowsToken);
                owned.add(row.onLongAction("open", () -> { if (live(owner, token) && !detailBlocks() && rowRevision == rowsToken && rowBindings.get(row) == owned) {
                    clearFocus(owner); port.entry(entry.id(), EntryAction.GROUP_SELECT); refresh();
                } }));
                boolean[] rightPressed = {false};
                owned.add(row.onPointer("open", pointer -> {
                    if (pointer.kind() == UiPointer.Kind.DOWN && pointer.button() == 1) {
                        rightPressed[0] = live(owner, token) && rowRevision == rowsToken && rowBindings.get(row) == owned && inside(row, pointer);
                    } else if (pointer.kind() == UiPointer.Kind.UP && rightPressed[0]) {
                        rightPressed[0] = false;
                        if (pointer.button() == 1 && live(owner, token) && !detailBlocks() && rowRevision == rowsToken && rowBindings.get(row) == owned && inside(row, pointer) && !port.redacted()) {
                            clearFocus(owner); port.entry(entry.id(), EntryAction.CONTEXT); if (live(owner, token)) refresh();
                        }
                    }
                }));
                row.gameElement("apply", icon("edit")); row.tooltip("apply", tr("edit"));
                row.enabled("apply", entry.editable());
                bindEntry(row, owned, "apply", entry, EntryAction.EDIT, owner, token, rowsToken);
                row.text("reset", port.probability(entry.id())); row.tooltip("reset", tr("edit_probability")); row.enabled("reset", !entry.group());
                bindEntry(row, owned, "reset", entry, EntryAction.PROBABILITY, owner, token, rowsToken);
                row.gameElement("delete", icon("delete")); row.tooltip("delete", tr("delete"));
                row.enabled("delete", !entry.group() || entry.editable());
                bindEntry(row, owned, "delete", entry, EntryAction.DELETE, owner, token, rowsToken);
                rowSprites(row, entry);
            }
        }, 120);
    }
    private void bindEntry(UiView row, List<AutoCloseable> owned, String id, Entry entry, EntryAction action, UiView owner, long token, long rowsToken) {
        owned.add(row.onAction(id, () -> {
            if (!live(owner, token) || detailBlocks() || rowRevision != rowsToken || rowBindings.get(row) != owned || port.redacted()) return;
            if ((action == EntryAction.EDIT && !entry.editable()) || (action == EntryAction.PROBABILITY && entry.group())
                    || (action == EntryAction.DELETE && entry.group() && !entry.editable())) return;
            clearFocus(owner); port.entry(entry.id(), action); if (live(owner, token)) refresh();
        }));
    }
    private static void clearFocus(UiView view) { if (view.state() != null) view.state().selection("", 0, 0); }
    private static boolean inside(UiView row, UiPointer pointer) {
        var bounds = row.bounds("open");
        return pointer.finite() && bounds != null && Double.isFinite(bounds.x()) && Double.isFinite(bounds.y())
                && Double.isFinite(bounds.width()) && Double.isFinite(bounds.height()) && bounds.width() > 0 && bounds.height() > 0
                && pointer.x() >= bounds.x() && pointer.x() < bounds.x() + bounds.width()
                && pointer.y() >= bounds.y() && pointer.y() < bounds.y() + bounds.height();
    }
    @Override public void tick() {
        if (view != null && !view.nativeView() && connection.getAsBoolean()) {
            port.tick(); if (revision != port.revision()) refresh(); else chrome();
        }
    }
    @Override public boolean key(int key, int scan, int modifiers, boolean pressed) {
        if (view == null || closed || !connection.getAsBoolean()) return false;
        if (pressed && key == 256 && (moreOpen || detailBlocks())) {
            moreOpen = false; detailOpen = false; clearFocus(view); chrome(); return true;
        }
        if (detailBlocks()) return true;
        if (view.state() != null && !view.state().focusedField().isEmpty()) return false;
        boolean handled = port.key(key, scan, modifiers, pressed); if (handled) refresh(); return handled;
    }
    public boolean requestClose() { if (view == null || closed) return false; view.requestClose(); return true; }
    private static void release(List<AutoCloseable> resources) {
        if (resources != null) for (AutoCloseable resource : resources) try { resource.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private void clearRows() { for (List<AutoCloseable> owned : rowBindings.values()) release(owned); rowBindings.clear(); rowEntries.clear(); }
    @Override public void detach(UiDetachReason reason) {
        generation++; rowRevision++; view = null;
        navigationIds = List.of();
        selectedDetail = null; moreOpen = false; detailOpen = false; chromeState = -1;
        clearRows(); release(bindings); bindings.clear(); port.suspend();
    }
    @Override public void close(UiCloseReason reason) {
        if (closed) return;
        detach(UiDetachReason.DISCONNECT); closed = true; port.closed(reason);
    }
}
