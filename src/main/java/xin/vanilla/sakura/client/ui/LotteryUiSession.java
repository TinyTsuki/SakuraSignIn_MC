package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.gui.widget.*;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.Reward;

import java.math.*;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class LotteryUiSession implements UiPageSession {
    public interface Port {
        void prepare();
        List<LotteryPool> pools();
        String poolId();
        void pool(String id);
        List<String> counts();
        String count();
        void count(String value);
        LotteryAnimationStyle animation();
        void animation(LotteryAnimationStyle value);
        long generation();
        LotteryPaymentViewModel payment();
        String name(Reward reward);
    }
    private final Port port;
    private final BooleanSupplier connection;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private UiView view;
    private long binding, generation;
    private boolean closed;
    private String displayedStatus;
    private LotteryPool selectedPool;
    private long rowsRevision;

    public LotteryUiSession(Port port, BooleanSupplier connection) {
        this.port = Objects.requireNonNull(port); this.connection = Objects.requireNonNull(connection);
    }
    private boolean live(UiView owner, long token) { return !closed && view == owner && binding == token && connection.getAsBoolean(); }
    @Override public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = Objects.requireNonNull(next);
        if (next.nativeView()) return;
        long token = binding;
        next.text("title", tr("lottery_screen"));
        next.text("single", tr("lottery_single_draw")); next.text("batch", tr("lottery_batch_draw"));
        next.gameElement("close", UiGameElement.preset(ButtonWidget.PresetStyle.CLOSE));
        next.tooltip("close", xin.vanilla.banira.BaniraComponent.get().transClientAuto("config_editor_close"));
        next.tooltip("pool", tr("lottery_pool_title")); next.tooltip("count", tr("lottery_max_draws"));
        bindings.add(next.onValue("pool", value -> {
            if (!live(next, token) || port.payment().isDrawing()) return;
            String id = value.asText();
            if (port.pools().stream().noneMatch(pool -> pool.getId().equals(id))) return;
            port.pool(id); if (next.state() != null) { next.state().rowPosition(0); next.state().detailScroll(0); }
            refresh();
        }));
        bindings.add(next.onValue("count", value -> {
            if (live(next, token) && !port.payment().isDrawing() && port.counts().contains(value.asText())) { port.count(value.asText()); refresh(); }
        }));
        bindings.add(next.onValue("animation", value -> {
            if (!live(next, token)) return;
            for (LotteryAnimationStyle style : LotteryAnimationStyle.values()) if (style.name().equals(value.asText())) {
                port.animation(style); next.value("animation", UiValue.text(style.name())); next.tooltip("animation", style.enumDescription()); break;
            }
        }));
        bindings.add(next.onAction("single", () -> { if (live(next, token)) { port.payment().click(1); status(); } }));
        bindings.add(next.onAction("batch", () -> { if (live(next, token)) { port.payment().click(batchCount()); status(); } }));
        bindings.add(next.onAction("close", () -> { if (live(next, token)) next.requestClose(); }));
        refresh();
    }
    private void refresh() {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean()) return;
        port.prepare(); generation = port.generation();
        List<LotteryPool> pools = port.pools();
        selectedPool = pools.stream().filter(pool -> pool.getId().equals(port.poolId())).findFirst().orElse(null);
        select("pool", pools.stream().map(pool -> option(pool.getId(), pool.getDisplayName())).toList(), port.poolId());
        select("count", port.counts().stream().map(value -> option(value, "all".equals(value)
                ? tr("lottery_draw_all").toString() : SakuraComponent.get().translateClient("format", "lottery_draw_count_s", value))).toList(), port.count());
        select("animation", Arrays.stream(LotteryAnimationStyle.values()).map(style -> option(style.name(), style.enumDescription().toString())).toList(), port.animation().name());
        view.tooltip("animation", port.animation().enumDescription());
        LotteryPool pool = selectedPool;
        view.text("description", literal(pool == null ? "" : pool.getDescription()));
        view.tooltip("description", literal(pool == null ? "" : pool.getDescription()));
        view.text("empty", pool == null ? tr("lottery_no_pools") : pool.getPreviewMode() == LotteryPreviewMode.NONE
                ? tr("lottery_rewards_hidden") : pool.getRewards().isEmpty() ? tr("lottery_pool_empty") : literal(""));
        List<Reward> rewards = pool == null || pool.getPreviewMode() == LotteryPreviewMode.NONE ? List.of() : List.copyOf(pool.getRewards());
        BigDecimal total = rewards.stream().map(Reward::getProbability).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        UiView owner = view; long token = binding, rowsToken = ++rowsRevision;
        view.rows("fields", new UiRowProvider() {
            public int size() { return rewards.size(); }
            public String key(int index) { return pool.getId() + ":" + index; }
            public void bind(UiView row, int index) {
                if (!live(owner, token) || rowsRevision != rowsToken) return;
                Reward reward = rewards.get(index);
                boolean items = pool.getPreviewMode().showsItems();
                String name = items ? port.name(reward) : "?";
                String probability = "";
                if (pool.getPreviewMode().showsProbabilities()) {
                    BigDecimal weight = reward.getProbability() == null ? BigDecimal.ZERO : reward.getProbability();
                    BigDecimal percent = total.signum() <= 0 ? BigDecimal.ZERO : weight.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP).stripTrailingZeros();
                    probability = SakuraComponent.get().translateClient("format", "lottery_probability_s", percent.toPlainString());
                }
                row.text("label", literal(name)); row.text("detail", literal(probability));
                row.tooltip("label", literal(name + (probability.isEmpty() ? "" : "\n" + probability)));
                row.gameElement("icon", items ? RewardRenderer.uiIcon(reward) : null);
            }
        }, 120);
        displayedStatus = null; status();
    }
    private void select(String id, List<DropdownOption> options, String value) {
        view.control(id, new UiControlSpec(UiControlSpec.Kind.SELECT, 32767, "", false, options, DropdownInputMode.SELECTION_ONLY));
        view.value(id, UiValue.text(value));
    }
    private static DropdownOption option(String value, String label) { return new DropdownOption(value, label, net.minecraft.world.item.ItemStack.EMPTY, null, null); }
    private int batchCount() { return "all".equals(port.count()) ? -1 : Integer.parseInt(port.count()); }
    private void status() {
        if (view == null || view.nativeView()) return;
        LotteryPaymentViewModel payment = port.payment(); LotteryPool pool = selectedPool;
        String details = pool == null ? "" : SakuraComponent.get().translateClient("format", "lottery_pool_details_sss",
                SakuraComponent.get().translateClient("word", "lottery_policy_" + pool.getLimitPolicy().name().toLowerCase(Locale.ROOT)), pool.getMaxDraws(), pool.getCooldownSeconds());
        String fee = payment.summary();
        String cost = fee == null ? tr("lottery_quote_pending").toString() : SakuraComponent.get().translateClient("format", "lottery_cost_per_draw_s", fee.isEmpty() ? tr("lottery_free").toString() : fee);
        LotteryQuote quote = payment.quote();
        String state = payment.isDrawing() ? tr("lottery_drawing").toString() : quote != null && !quote.isReady() && !payment.isPending() ? tr("lottery_payment_unavailable").toString() : "";
        String text = details + "\n" + cost + (state.isEmpty() ? "" : "\n" + state);
        boolean single = pool != null && payment.canDraw(1), batch = pool != null && payment.canDraw(batchCount());
        String stamp = text + single + batch + payment.isDrawing();
        if (stamp.equals(displayedStatus)) return;
        displayedStatus = stamp;
        view.text("info", literal(text)); view.tooltip("info", literal(text));
        view.enabled("single", single); view.enabled("batch", batch);
        view.enabled("pool", !payment.isDrawing()); view.enabled("count", !payment.isDrawing());
    }
    @Override public void tick() {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean()) return;
        if (generation != port.generation()) refresh();
        port.payment().tick(); status();
    }
    public boolean requestClose() { if (view == null || closed) return false; view.requestClose(); return true; }
    @Override public void detach(UiDetachReason reason) {
        binding++; rowsRevision++; view = null; selectedPool = null;
        for (AutoCloseable resource : bindings) try { resource.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        bindings.clear();
    }
    @Override public void close(UiCloseReason reason) {
        if (closed) return;
        detach(UiDetachReason.DISCONNECT); closed = true; port.payment().cancelIntent();
    }
    private static xin.vanilla.banira.common.data.Component tr(String key) { return SakuraComponent.get().transClient("word", key); }
    private static xin.vanilla.banira.common.data.Component literal(String value) { return SakuraComponent.get().literal(value); }
}
