package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.gui.widget.DropdownInputMode;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.Reward;

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
        this.port = Objects.requireNonNull(port);
        this.connection = Objects.requireNonNull(connection);
    }

    private boolean live(UiView owner, long token) {
        return !closed && view == owner && binding == token && connection.getAsBoolean();
    }

    @Override
    public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = Objects.requireNonNull(next);
        if (next.nativeView()) return;
        LotteryUiRewards.enter(next);
        long token = binding;
        next.text("title", tr("lottery_screen"));
        next.text("single_label", tr("lottery_single_draw"));
        next.text("batch_label", tr("lottery_batch_draw"));
        next.text("pool_label", tr("lottery_pool_selector"));
        next.text("fee_label", tr("lottery_cost_label"));
        next.text("available_label", tr("lottery_available_count"));
        next.text("count_label", tr("lottery_batch_count"));
        next.text("preview_title", tr("lottery_candidates"));
        next.text("animation_label", tr("lottery_animation"));
        next.gameElement("brand", SakuraUiPages.moduleIcon());
        next.gameElement("close_icon", icon("close"));
        next.tooltip("close", xin.vanilla.banira.BaniraComponent.get().transClientAuto("config_editor_close"));
        next.tooltip("pool", tr("lottery_pool_title"));
        next.tooltip("count", tr("lottery_max_draws"));
        bindings.add(next.onValue("pool", value -> {
            if (!live(next, token) || port.payment().isDrawing()) return;
            String id = value.asText();
            if (port.pools().stream().noneMatch(pool -> pool.getId().equals(id))) return;
            port.pool(id);
            if (next.state() != null) {
                next.state().rowPosition(0);
                next.state().detailScroll(0);
            }
            refresh();
        }));
        bindings.add(next.onValue("count", value -> {
            if (live(next, token) && !port.payment().isDrawing() && port.counts().contains(value.asText())) {
                port.count(value.asText());
                refresh();
            }
        }));
        bindings.add(next.onValue("animation", value -> {
            if (!live(next, token)) return;
            for (LotteryAnimationStyle style : LotteryAnimationStyle.values())
                if (style.name().equals(value.asText())) {
                    port.animation(style);
                    animation();
                    break;
                }
        }));
        for (LotteryAnimationStyle style : LotteryAnimationStyle.values()) {
            String id = "animation_" + style.name().toLowerCase(Locale.ROOT);
            next.text(id, tr("lottery_animation_button_" + style.name().toLowerCase(Locale.ROOT)));
            next.tooltip(id, style.enumDescription());
            bindings.add(next.onAction(id, () -> {
                if (live(next, token)) {
                    port.animation(style);
                    animation();
                }
            }));
        }
        bindings.add(next.onAction("single", () -> {
            if (live(next, token)) {
                port.payment().click(1);
                status();
            }
        }));
        bindings.add(next.onAction("batch", () -> {
            if (live(next, token)) {
                port.payment().click(batchCount());
                status();
            }
        }));
        bindings.add(next.onAction("close", () -> {
            if (live(next, token)) next.requestClose();
        }));
        refresh();
    }

    private void refresh() {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean()) return;
        port.prepare();
        generation = port.generation();
        List<LotteryPool> pools = port.pools();
        selectedPool = pools.stream().filter(pool -> pool.getId().equals(port.poolId())).findFirst().orElse(null);
        select("pool", pools.stream().map(pool -> option(pool.getId(), pool.getDisplayName())).toList(), port.poolId());
        select("count", port.counts().stream().map(value -> option(value, "all".equals(value)
                ? tr("lottery_draw_all_short").toString() : value)).toList(), port.count());
        select("animation", Arrays.stream(LotteryAnimationStyle.values()).map(style -> option(style.name(), style.enumDescription().toString())).toList(), port.animation().name());
        animation();
        LotteryPool pool = selectedPool;
        view.tooltip("pool", pool == null ? tr("lottery_pool_title") : literal(pool.getDisplayName()));
        view.tooltip("count", literal("all".equals(port.count()) ? tr("lottery_draw_all").toString()
                : SakuraComponent.get().translateClient("format", "lottery_draw_count_s", port.count())));
        String policy = pool == null ? "" : SakuraComponent.get().translateClient("word", "lottery_policy_" + pool.getLimitPolicy().name().toLowerCase(Locale.ROOT));
        if (pool != null && pool.getLimitPolicy() != LotteryLimitPolicy.UNLIMITED && pool.getLimitPolicy() != LotteryLimitPolicy.COOLDOWN)
            policy = SakuraComponent.get().translateClient("format", "lottery_policy_limit_ss", policy, pool.getMaxDraws());
        if (pool != null && pool.getCooldownSeconds() > 0)
            policy += " · " + SakuraComponent.get().translateClient("format", "lottery_interval_s", pool.getCooldownSeconds());
        view.text("policy", literal(policy));
        view.tooltip("policy", literal(policy));
        view.text("description", literal(pool == null ? "" : pool.getDescription()));
        view.tooltip("description", literal(pool == null ? "" : pool.getDescription()));
        view.text("empty", pool == null ? tr("lottery_no_pools") : pool.getPreviewMode() == LotteryPreviewMode.NONE
                ? tr("lottery_rewards_hidden") : pool.getRewards().isEmpty() ? tr("lottery_pool_empty") : literal(""));
        List<Reward> rewards = pool == null || pool.getPreviewMode() == LotteryPreviewMode.NONE ? List.of() : List.copyOf(pool.getRewards());
        view.text("preview_count", literal(pool == null || pool.getPreviewMode() == LotteryPreviewMode.NONE ? "" : String.valueOf(rewards.size())));
        UiView owner = view;
        long token = binding, rowsToken = ++rowsRevision;
        LotteryUiRewards.bind(view, rewards, pool == null ? "empty" : pool.getId(),
                () -> live(owner, token) && rowsRevision == rowsToken, port::name,
                pool != null && pool.getPreviewMode().showsItems(), pool != null && pool.getPreviewMode().showsProbabilities(),
                index -> RewardRenderer.uiIcon(rewards.get(index)));
        displayedStatus = null;
        status();
    }

    private void select(String id, List<DropdownOption> options, String value) {
        view.control(id, new UiControlSpec(UiControlSpec.Kind.SELECT, 32767, "", false, options, DropdownInputMode.SELECTION_ONLY));
        view.value(id, UiValue.text(value));
    }

    private static DropdownOption option(String value, String label) {
        return new DropdownOption(value, label, net.minecraft.world.item.ItemStack.EMPTY, null, null);
    }

    private int batchCount() {
        return "all".equals(port.count()) ? -1 : Integer.parseInt(port.count());
    }

    private void animation() {
        view.value("animation", UiValue.text(port.animation().name()));
        view.tooltip("animation", port.animation().enumDescription());
        for (LotteryAnimationStyle style : LotteryAnimationStyle.values())
            view.enabled("animation_" + style.name().toLowerCase(Locale.ROOT), style != port.animation());
    }

    private static UiGameElement icon(String name) {
        return UiGameElement.sprite(xin.vanilla.banira.client.data.Texture.of(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("apricityui", "apricity/sakura_sign_in/icons/" + name + ".png"), 16, 16));
    }

    private void status() {
        if (view == null || view.nativeView()) return;
        LotteryPaymentViewModel payment = port.payment();
        LotteryPool pool = selectedPool;
        String fee = payment.summary();
        String cost = pool == null || fee == null ? "\u2014" : fee.isEmpty() ? tr("lottery_free").toString() : fee;
        LotteryQuote quote = payment.quote();
        boolean ready = !payment.isWaitingForQuote() && !payment.isDrawing() && quote != null && quote.isReady()
                && quote.getGeneration() == port.generation() && pool != null && quote.getRequest().getPoolId().equals(pool.getId());
        String state = pool == null ? "lottery_no_pools" : payment.isDrawing() ? "lottery_drawing"
                : payment.isWaitingForQuote() || quote == null ? "lottery_quote_pending"
                : ready ? "" : switch (quote.getStatus()) {
            case FORBIDDEN -> "lottery_permission_denied";
            case STALE_GENERATION -> "lottery_configuration_changed";
            case POOL_NOT_FOUND -> "lottery_pool_not_found";
            default -> "lottery_payment_unavailable";
        };
        String text = state.isEmpty() ? "" : tr(state).toString();
        String available = ready ? String.valueOf(quote.getAvailableCount()) : "\u2014";
        boolean single = pool != null && payment.canOfferDraw(1), batch = pool != null && payment.canOfferDraw(batchCount());
        String stamp = List.of(text, cost, available, single, batch, payment.isDrawing()).toString();
        if (stamp.equals(displayedStatus)) return;
        displayedStatus = stamp;
        view.text("info", literal(text));
        view.tooltip("info", literal(text));
        view.enabled("info", !text.isEmpty());
        view.text("fee", literal(cost));
        view.tooltip("fee", literal(cost));
        view.text("available", literal(available));
        view.enabled("single", single);
        view.enabled("batch", batch);
        view.enabled("pool", !payment.isDrawing());
        view.enabled("count", !payment.isDrawing());
    }

    @Override
    public void tick() {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean()) return;
        if (generation != port.generation()) refresh();
        port.payment().tick();
        status();
    }

    public boolean requestClose() {
        if (view == null || closed) return false;
        view.requestClose();
        return true;
    }

    @Override
    public void detach(UiDetachReason reason) {
        LotteryUiRewards.leave(view);
        binding++;
        rowsRevision++;
        view = null;
        selectedPool = null;
        for (AutoCloseable resource : bindings)
            try {
                resource.close();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        bindings.clear();
    }

    @Override
    public void close(UiCloseReason reason) {
        if (closed) return;
        detach(UiDetachReason.DISCONNECT);
        closed = true;
        port.payment().cancelIntent();
    }

    private static xin.vanilla.banira.common.data.Component tr(String key) {
        return SakuraComponent.get().transClient("word", key);
    }

    private static xin.vanilla.banira.common.data.Component literal(String value) {
        return SakuraComponent.get().literal(value);
    }
}
