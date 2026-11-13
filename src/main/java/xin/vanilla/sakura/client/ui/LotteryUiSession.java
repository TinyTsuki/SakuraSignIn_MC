package xin.vanilla.sakura.client.ui;

import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.lottery.LotteryArtwork;
import xin.vanilla.sakura.client.gui.lottery.LotteryFeePresentation;
import xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.reward.Reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class LotteryUiSession implements UiPageSession {
    public interface Port {
        void prepare();

        List<LotteryPool> pools();

        String poolId();

        void pool(String id);

        default int batch() {
            return 0;
        }

        default void batch(int value) {
        }

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
    private int confirmationCount;
    private String confirmationFee;

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
        long token = binding;
        next.text("single_label", tr("lottery_single_draw"));
        next.text("screen_title", tr("lottery_screen"));
        next.text("pool_label", tr("lottery_pool_selector"));
        next.text("preview_title", tr("lottery_candidates"));
        next.text("details", tr("lottery_preview_details"));
        next.text("details_close", tr("confirm"));
        next.text("confirm_title", tr("lottery_confirm_draw"));
        next.text("confirm_cancel", tr("cancel"));
        next.text("confirm_draw", tr("confirm"));
        next.enabled("details_panel", false);
        next.enabled("confirm_panel", false);
        next.gameElement("brand", SakuraUiPages.moduleIcon());
        next.gameElement("close_icon", icon("close"));
        next.tooltip("close", xin.vanilla.banira.BaniraComponent.get().transClientAuto("config_editor_close"));
        bindings.add(next.onAction("single", () -> {
            if (live(next, token) && selectedPool != null && selectedPool.isSingleDraw()) {
                confirm(1);
            }
        }));
        bindings.add(next.onValue("batch_count", value -> {
            if (!live(next, token) || selectedPool == null || port.payment().isDrawing()) return;
            for (int count : selectedPool.getDrawCounts()) {
                if (count > 1 && String.valueOf(count).equals(value.asText())) {
                    port.batch(count);
                    updateBatchLabel();
                    status();
                    break;
                }
            }
        }));
        bindings.add(next.onAction("batch", () -> {
            if (live(next, token) && selectedPool != null && selectedPool.getBatchSize() > 1) {
                confirm(batchCount());
            }
        }));
        bindings.add(next.onAction("details", () -> {
            if (live(next, token) && selectedPool != null && confirmationCount == 0)
                next.enabled("details_panel", true);
        }));
        bindings.add(next.onAction("details_close", () -> {
            if (live(next, token)) next.enabled("details_panel", false);
        }));
        bindings.add(next.onAction("confirm_cancel", () -> {
            if (live(next, token)) dismissConfirmation();
        }));
        bindings.add(next.onAction("confirm_draw", () -> {
            if (!live(next, token) || confirmationCount == 0) return;
            int count = confirmationCount;
            String fee = confirmationFee;
            dismissConfirmation();
            port.payment().clickConfirmed(count, fee);
            status();
        }));
        bindings.add(next.onAction("close", () -> {
            if (live(next, token)) next.requestClose();
        }));
        refresh();
    }

    private void refresh() {
        if (view == null || view.nativeView() || closed || !connection.getAsBoolean()) return;
        dismissConfirmation();
        port.prepare();
        generation = port.generation();
        List<LotteryPool> pools = port.pools();
        selectedPool = pools.stream().filter(pool -> pool.getId().equals(port.poolId())).findFirst().orElse(null);
        LotteryPool pool = selectedPool;
        var title = pool == null ? tr("lottery_screen") : literal(pool.getDisplayName());
        view.text("title", title);
        view.tooltip("title", title);
        view.gameElement("poster", pool == null ? null : LotteryArtwork.resolve(pool).element());
        view.enabled("single_slot", pool != null && pool.isSingleDraw());
        view.enabled("batch_slot", pool != null && pool.getBatchSize() > 1);
        List<Integer> batches = pool == null ? List.of() : pool.getDrawCounts().stream().filter(count -> count > 1).toList();
        view.enabled("batch_picker", batches.size() > 1);
        if (batches.size() > 1) {
            var options = batches.stream().map(count -> new xin.vanilla.banira.client.gui.widget.DropdownOption(
                    String.valueOf(count), String.valueOf(count), net.minecraft.world.item.ItemStack.EMPTY, null, null)).toList();
            view.control("batch_count", new UiControlSpec(UiControlSpec.Kind.SELECT, 3, "", false, options,
                    xin.vanilla.banira.client.gui.widget.DropdownInputMode.SELECTION_ONLY));
            view.value("batch_count", UiValue.text(String.valueOf(batchCount())));
        }
        updateBatchLabel();
        bindPools(List.copyOf(pools));
        String policy = pool == null ? "" : quota(pool);
        view.enabled("policy", pool != null);
        view.text("policy", literal(policy));
        String schedule = schedule(pool);
        view.tooltip("policy", literal(schedule.isEmpty() ? policy : policy + "\n" + schedule));
        view.text("description", literal(pool == null ? "" : pool.getDescription()));
        view.tooltip("description", literal(pool == null ? "" : pool.getDescription()));
        view.text("empty", pool == null ? tr("lottery_no_pools") : pool.getPreviewMode() == LotteryPreviewMode.NONE
                ? tr("lottery_rewards_hidden") : pool.getRewards().isEmpty() ? tr("lottery_pool_empty") : literal(""));
        List<Reward> rewards = pool == null || pool.getPreviewMode() == LotteryPreviewMode.NONE ? List.of() : List.copyOf(pool.getRewards());
        view.enabled("details", pool != null);
        for (int i = 0; i < 3; i++) {
            String id = "featured_" + i;
            boolean visible = pool != null && pool.getPreviewMode().showsItems() && i < rewards.size();
            view.enabled(id, visible);
            view.gameElement(id, visible ? RewardRenderer.uiIcon(rewards.get(i)) : null);
            view.tooltip(id, literal(visible ? port.name(rewards.get(i)) : ""));
        }
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

    private void bindPools(List<LotteryPool> pools) {
        UiView owner = view;
        long token = binding, revision = rowsRevision + 1;
        view.rows("pool_rows", new UiRowLayout() {
            @Override
            public int size() {
                return pools.size();
            }

            @Override
            public int rowHeight() {
                return 35;
            }

            @Override
            public String scrollerId() {
                return "pool_scroll";
            }

            @Override
            public boolean tracksPageScroll() {
                return false;
            }

            @Override
            public String key(int index) {
                return pools.get(index).getId();
            }

            @Override
            public String rowClass(int index) {
                return pools.get(index).getId().equals(port.poolId()) ? "pool-card selected" : "pool-card";
            }

            @Override
            public void bind(UiView row, int index) {
                LotteryPool pool = pools.get(index);
                row.gameElement("icon", LotteryArtwork.thumbnail(pool).element());
                row.text("label", literal(pool.getDisplayName()));
                row.text("detail", literal(quota(pool)));
                row.tooltip("label", literal(pool.getDisplayName()));
                row.onAction("label", () -> {
                    if (!live(owner, token) || rowsRevision != revision || port.payment().isDrawing()
                            || port.poolId().equals(pool.getId())) return;
                    port.pool(pool.getId());
                    if (owner.state() != null) {
                        owner.state().rowPosition(0);
                        owner.state().detailScroll(0);
                    }
                    refresh();
                });
            }
        }, 20);
    }

    private int batchCount() {
        if (selectedPool == null) return 0;
        int current = port.batch();
        return current > 1 && selectedPool.allowsCount(current) ? current : selectedPool.getDrawCounts().stream()
                .filter(count -> count > 1).findFirst().orElse(0);
    }

    private void confirm(int count) {
        if (confirmationCount != 0 || selectedPool == null || !selectedPool.allowsCount(count)
                || !port.payment().canOfferDraw(count)) return;
        String fee = port.payment().summary();
        if (fee == null) return;
        confirmationCount = count;
        confirmationFee = fee;
        view.enabled("details_panel", false);
        view.text("confirm_draw_count", SakuraComponent.get().transClient("format", "lottery_draw_count_s", count));
        view.text("confirm_fee", SakuraComponent.get().transClient("format", "lottery_cost_per_draw_s",
                LotteryFeePresentation.of(fee).tooltip()));
        view.enabled("confirm_draw", true);
        view.enabled("confirm_panel", true);
        displayedStatus = null;
        status();
    }

    private void dismissConfirmation() {
        confirmationCount = 0;
        confirmationFee = null;
        view.enabled("confirm_panel", false);
    }

    private void updateBatchLabel() {
        if (batchCount() > 1)
            view.text("batch_label", SakuraComponent.get().transClient("format", "lottery_draw_count_s", batchCount()));
    }

    public static String schedule(LotteryPool pool) {
        if (pool == null) return "";
        var text = SakuraComponent.get();
        if (!pool.getStartDate().isEmpty() && !pool.getEndDate().isEmpty())
            return text.translateClient("format", "lottery_window_ss", pool.getStartDate(), pool.getEndDate());
        if (!pool.getStartDate().isEmpty())
            return text.translateClient("format", "lottery_from_s", pool.getStartDate());
        if (!pool.getEndDate().isEmpty()) return text.translateClient("format", "lottery_until_s", pool.getEndDate());
        return "";
    }

    public static String countdown(LotteryPaymentViewModel payment) {
        long minutes = payment.windowMinutes();
        if (minutes <= 0) return "";
        return countdownLabel(payment) + "\n" + countdownTime(minutes);
    }

    private static String countdownLabel(LotteryPaymentViewModel payment) {
        return tr(payment.opensNext() ? "lottery_start_date" : "lottery_time_remaining").toString();
    }

    private static String countdownTime(long minutes) {
        return SakuraComponent.get().translateClient("format", "lottery_countdown_sss",
                minutes / 1440, (minutes / 60) % 24, minutes % 60);
    }

    private static UiGameElement icon(String name) {
        return UiGameElement.sprite(xin.vanilla.banira.client.data.Texture.of(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("apricityui", "apricity/sakura_sign_in/icons/" + name + ".png"), 16, 16));
    }

    private void status() {
        if (view == null || view.nativeView()) return;
        LotteryPaymentViewModel payment = port.payment();
        LotteryPool pool = selectedPool;
        Billing billing = billing(payment, pool, port.generation(), true);
        long minutes = payment.windowMinutes();
        String countdownLabel = minutes > 0 ? countdownLabel(payment) : "";
        String countdownTime = minutes > 0 ? countdownTime(minutes) : "";
        boolean single = pool != null && pool.isSingleDraw() && payment.canOfferDraw(1);
        boolean batch = pool != null && pool.getBatchSize() > 1 && payment.canOfferDraw(batchCount());
        String stamp = List.of(billing, single, batch, payment.isDrawing(), countdownLabel, countdownTime).toString();
        if (stamp.equals(displayedStatus)) return;
        displayedStatus = stamp;
        view.enabled("time", minutes > 0);
        view.enabled("confirm_draw", confirmationCount != 0 && payment.canOfferDraw(confirmationCount));
        view.text("time_label", literal(countdownLabel));
        view.text("time_value", literal(countdownTime));
        view.text("info", literal(billing.status()));
        view.tooltip("info", literal(billing.status()));
        view.enabled("info", !billing.status().isEmpty());
        var fee = LotteryFeePresentation.of(pool == null ? null : payment.summary());
        view.text("fee", literal(fee.text()));
        view.tooltip("fee", literal(fee.tooltip()));
        view.gameElement("fee_icon", fee.icon());
        view.enabled("fee_icon", fee.icon() != null);
        view.tooltip("fee_icon", literal(fee.tooltip()));
        view.text("available", literal(billing.available()));
        view.enabled("available", !billing.available().isEmpty());
        view.tooltip("available", literal(billing.available()));
        view.enabled("single", single);
        view.enabled("batch", batch);
    }

    public static String quota(LotteryPool pool) {
        var text = SakuraComponent.get();
        String policy = text.translateClient("word", "lottery_policy_" + pool.getLimitPolicy().name().toLowerCase(Locale.ROOT));
        if (pool.getLimitPolicy() != LotteryLimitPolicy.UNLIMITED && pool.getLimitPolicy() != LotteryLimitPolicy.COOLDOWN)
            policy = text.translateClient("format", "lottery_policy_limit_ss", policy, pool.getMaxDraws());
        if (pool.getCooldownSeconds() > 0)
            policy += " · " + text.translateClient("format", "lottery_interval_s", pool.getCooldownSeconds());
        return policy;
    }

    public record Billing(String cost, String available, String status) {
    }

    public static Billing billing(LotteryPaymentViewModel payment, LotteryPool pool, long generation) {
        return billing(payment, pool, generation, false);
    }

    private static Billing billing(LotteryPaymentViewModel payment, LotteryPool pool, long generation, boolean compact) {
        String fee = payment.summary();
        String cost = pool == null || fee == null ? "-" : LotteryFeePresentation.of(fee).tooltip();
        LotteryQuote quote = payment.quote();
        boolean waiting = payment.isPending() && !payment.isRefreshing();
        boolean ready = !waiting && !payment.isDrawing() && quote != null && quote.isReady()
                && quote.getGeneration() == generation && pool != null && quote.getRequest().getPoolId().equals(pool.getId());
        String state = pool == null ? "lottery_no_pools" : payment.isDrawing() ? "lottery_drawing"
                : waiting || quote == null ? "lottery_quote_pending"
                : ready ? "" : switch (quote.getStatus()) {
            case FORBIDDEN -> "lottery_permission_denied";
            case STALE_GENERATION -> "lottery_configuration_changed";
            case POOL_NOT_FOUND -> "lottery_pool_not_found";
            case POOL_CLOSED -> "lottery_pool_closed";
            default -> "lottery_payment_unavailable";
        };
        String available = ready ? String.valueOf(quote.getAvailableCount()) : "-";
        return new Billing(compact ? cost : tr("lottery_cost_label") + ": " + cost,
                !ready ? "" : compact ? SakuraComponent.get().translateClient("format", "lottery_remaining_s", available) : tr("lottery_available_count") + ": " + available,
                state.isEmpty() ? "" : tr(state).toString());
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
        confirmationCount = 0;
        confirmationFee = null;
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
