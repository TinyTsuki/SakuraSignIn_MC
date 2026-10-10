package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.TextComponent;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.*;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.notification.NotificationDetailViewport;
import xin.vanilla.banira.client.gui.widget.*;
import xin.vanilla.banira.client.enums.EnumOrientation;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.SakuraClientPreferences;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.lottery.LotteryLayout;
import xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryDrawRequestPacket;
import xin.vanilla.sakura.network.packet.LotteryQuoteRequestPacket;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.lottery.LotteryRewardService;

import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class LotteryScreen extends BaniraScreen {
    private final ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
    private final LotteryPaymentViewModel payment = new LotteryPaymentViewModel(
            SakuraClientState.getLotteryRequests(), () -> System.nanoTime() / 1_000_000,
            request -> SakuraNetwork.sendToServer(new LotteryQuoteRequestPacket(request)),
            request -> SakuraNetwork.sendToServer(new LotteryDrawRequestPacket(request)));
    private final NotificationDetailViewport infoViewport = new NotificationDetailViewport();
    private final NotificationDetailViewport descriptionViewport = new NotificationDetailViewport();
    private LotteryLayout layout;
    private DropdownSelectWidget poolSelect, countSelect, animationSelect;
    private ButtonWidget singleDrawButton, batchDrawButton;
    private String selectedPoolId = "";
    private int previewScrollRows;
    private ScrollbarWidget previewScrollbar, infoScrollbar, descriptionScrollbar;
    private long displayedGeneration = -1;

    public LotteryScreen(Screen parent) {
        super(SakuraComponent.get().transClient("word", "lottery_screen"));
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    protected void initWidgets() {
        layout = LotteryLayout.calculate(width, height);
        if (selectedPoolId.isEmpty()) selectedPoolId = SakuraClientPreferences.lastLotteryPoolId();
        validateSelection();
        poolSelect = dropdown(layout.pool());
        refreshPoolOptions();
        poolSelect.onSelectionChanged(values -> {
            selectedPoolId = values.isEmpty() ? "" : values.get(0);
            SakuraClientPreferences.lastLotteryPoolId(selectedPoolId);
            previewScrollRows = 0; infoViewport.reset(); descriptionViewport.reset();
            configureCountOptions();
        });

        singleDrawButton = new ButtonWidget(this);
        singleDrawButton.bounds(bounds(layout.single()));
        singleDrawButton.text(SakuraComponent.get().transClient("word", "lottery_single_draw"));
        singleDrawButton.onClick(button -> payment.click(1));
        singleDrawButton.enabled(false);
        addWidget(singleDrawButton);

        countSelect = dropdown(layout.count());
        countSelect.onSelectionChanged(values -> selectPayment());
        batchDrawButton = new ButtonWidget(this);
        batchDrawButton.bounds(bounds(layout.batch()));
        batchDrawButton.text(SakuraComponent.get().transClient("word", "lottery_batch_draw"));
        batchDrawButton.onClick(button -> payment.click(batchCount()));
        batchDrawButton.enabled(false);
        addWidget(batchDrawButton);

        animationSelect = dropdown(layout.animation());
        animationSelect.optionsEnum(LotteryAnimationStyle.class);
        animationSelect.selectedValues(Collections.singletonList(ClientConfig.get().display().lotteryAnimationStyle().name()));
        animationSelect.onSelectionChanged(values -> {
            if (values.isEmpty()) return;
            LotteryAnimationStyle style = LotteryAnimationStyle.valueOf(values.get(0));
            if (style != ClientConfig.get().display().lotteryAnimationStyle()) {
                ClientConfig.get().display().lotteryAnimationStyle(style);
                ClientConfig.save();
            }
        });

        ButtonWidget closeButton = new ButtonWidget(this);
        closeButton.presetStyleClose().radius(3).padding(1);
        closeButton.bounds(bounds(layout.close()));
        closeButton.onClick(button -> requestClose(CloseReason.BUTTON));
        addWidget(closeButton);
        previewScrollbar = scrollbar(value -> previewScrollRows = (int) Math.round(value));
        infoScrollbar = scrollbar(infoViewport::scrollTo);
        descriptionScrollbar = scrollbar(descriptionViewport::scrollTo);
        configureCountOptions();
        displayedGeneration = SakuraClientState.getLotteryRequests().getGeneration();
    }

    private DropdownSelectWidget dropdown(LotteryLayout.Rect area) {
        DropdownSelectWidget widget = new DropdownSelectWidget(this);
        widget.inputMode(DropdownInputMode.SELECTION_ONLY);
        widget.bounds(bounds(area));
        addWidget(widget);
        return widget;
    }

    private ScrollbarWidget scrollbar(java.util.function.Consumer<Double> changed) {
        ScrollbarWidget widget = new ScrollbarWidget(this);
        widget.orientation(EnumOrientation.VERTICAL).minValue(0).scrollStep(1).scrollOnFocus(false).onValueChanged(changed);
        widget.visible(false);
        addWidget(widget);
        return widget;
    }

    @Override
    public void tick() {
        super.tick();
        long generation = SakuraClientState.getLotteryRequests().getGeneration();
        if (displayedGeneration != generation) {
            displayedGeneration = generation;
            validateSelection(); refreshPoolOptions(); configureCountOptions();
        }
        payment.tick();
        singleDrawButton.enabled(payment.canDraw(1));
        batchDrawButton.enabled(payment.canDraw(batchCount()));
        poolSelect.enabled(!payment.isDrawing());
        countSelect.enabled(!payment.isDrawing());
    }

    private void validateSelection() {
        if (pools().stream().noneMatch(pool -> pool.getId().equals(selectedPoolId))) {
            selectedPoolId = pools().isEmpty() ? "" : pools().get(0).getId();
            SakuraClientPreferences.lastLotteryPoolId(selectedPoolId);
            infoViewport.reset(); descriptionViewport.reset(); previewScrollRows = 0;
        }
    }

    private void refreshPoolOptions() {
        poolSelect.optionEntries(pools().stream().map(pool -> new DropdownOption(
                pool.getId(), pool.getDisplayName(), net.minecraft.world.item.ItemStack.EMPTY, null,
                SakuraComponent.get().transClient("format", "lottery_pool_tooltip_sss",
                        policyName(pool), pool.getMaxDraws(), pool.getCooldownSeconds())))
                .collect(Collectors.toList()));
        poolSelect.selectedValues(selectedPoolId.isEmpty() ? Collections.emptyList() : Collections.singletonList(selectedPoolId));
    }

    @Override
    protected void onRender(PoseStack stack, float partialTicks) {
        LotteryLayout.Rect panel = layout.panel();
        shape(stack, panel.x(), panel.y(), panel.width(), panel.height(), getEffectiveTheme().panelBg(), 8, 0);
        font.draw(stack, font.plainSubstrByWidth(getTitle().getString(), layout.title().width()),
                layout.title().x(), layout.title().y(), getEffectiveTheme().textPrimary());
        LotteryPool pool = selectedPool();
        if (pool == null) {
            renderParagraph(stack, layout.info(), infoViewport, infoScrollbar,
                    SakuraComponent.get().translateClient("word", "lottery_no_pools"), getEffectiveTheme().textSecondary());
            previewScrollbar.visible(false); descriptionScrollbar.visible(false);
        } else {
            String details = SakuraComponent.get().translateClient("format", "lottery_pool_details_sss",
                    policyName(pool), pool.getMaxDraws(), pool.getCooldownSeconds());
            String fee = payment.summary();
            String cost = fee == null ? SakuraComponent.get().translateClient("word", "lottery_quote_pending")
                    : SakuraComponent.get().translateClient("format", "lottery_cost_per_draw_s",
                    fee.isEmpty() ? SakuraComponent.get().translateClient("word", "lottery_free") : fee);
            LotteryQuote quote = payment.quote();
            String status = payment.isDrawing() ? SakuraComponent.get().translateClient("word", "lottery_drawing")
                    : quote != null && !quote.isReady() && !payment.isPending()
                    ? SakuraComponent.get().translateClient("word", "lottery_payment_unavailable") : "";
            renderParagraph(stack, layout.info(), infoViewport, infoScrollbar,
                    details + "\n" + cost + (status.isEmpty() ? "" : "\n" + status), getEffectiveTheme().textSecondary());
            renderParagraph(stack, layout.description(), descriptionViewport, descriptionScrollbar,
                    pool.getDescription(), getEffectiveTheme().textPrimary());
            renderPreview(stack, pool);
            if (!dropdownOpen() && layout.pool().contains(inputState.mouseX(), inputState.mouseY())
                    && font.width(pool.getDisplayName()) > layout.pool().width() - 30) tooltip(stack, pool.getDisplayName());
        }
        renderWidgets(stack, partialTicks);
    }

    private void renderParagraph(PoseStack stack, LotteryLayout.Rect area,
                                 NotificationDetailViewport viewport, ScrollbarWidget scrollbar, String text, int color) {
        List<FormattedCharSequence> lines = font.split(new TextComponent(text), area.width() - 9);
        viewport.layout(area.x(), area.y(), area.width() - 9, area.height(), font.lineHeight, lines.size());
        AbstractGuiUtils.pushScissor(area.x(), area.y(), area.width() - 9, area.height());
        try {
            for (int i = viewport.firstLine(); i < viewport.lastLineExclusive(); i++)
                font.draw(stack, lines.get(i), area.x(), viewport.lineY(i), color);
        } finally { AbstractGuiUtils.popScissor(); }
        scrollbar.bounds(new ScreenCoordinate(area.right() - 5, area.y(), 5, area.height()));
        scrollbar.maxValue(viewport.maxScrollRows()).visibleSize(viewport.visibleRows()).visible(viewport.maxScrollRows() > 0);
        scrollbar.setValue(viewport.scrollRows());
    }

    private void renderPreview(PoseStack stack, LotteryPool pool) {
        LotteryLayout.Rect area = layout.preview();
        LotteryPreviewMode mode = pool.getPreviewMode();
        List<Reward> rewards = pool.getRewards();
        if (mode == LotteryPreviewMode.NONE || rewards.isEmpty()) {
            previewScrollbar.visible(false);
            String text = SakuraComponent.get().translateClient("word",
                    mode == LotteryPreviewMode.NONE ? "lottery_rewards_hidden" : "lottery_pool_empty");
            AbstractGuiUtils.pushScissor(area.x(), area.y(), area.width(), area.height());
            try {
                List<FormattedCharSequence> lines = font.split(new TextComponent(text), area.width());
                for (int i = 0; i < lines.size() && i * font.lineHeight < area.height(); i++)
                    font.draw(stack, lines.get(i), area.x(), area.y() + i * font.lineHeight, getEffectiveTheme().textSecondary());
            } finally { AbstractGuiUtils.popScissor(); }
            return;
        }
        int columns = Math.max(1, (area.width() - 10) / 34);
        int visibleRows = Math.max(1, (area.height() - 10) / 34);
        int maxRows = Math.max(0, (rewards.size() + columns - 1) / columns - visibleRows);
        previewScrollRows = Math.max(0, Math.min(previewScrollRows, maxRows));
        previewScrollbar.bounds(new ScreenCoordinate(area.right() - 5, area.y(), 5, area.height()));
        previewScrollbar.maxValue(maxRows).visibleSize(visibleRows).visible(maxRows > 0);
        previewScrollbar.setValue(previewScrollRows);
        int start = previewScrollRows * columns, end = Math.min(rewards.size(), start + columns * visibleRows);
        AbstractGuiUtils.pushScissor(area.x(), area.y(), area.width() - 7, area.height());
        try {
            for (int i = start; i < end; i++) {
                int local = i - start, x = area.x() + 4 + local % columns * 34, y = area.y() + 5 + local / columns * 34;
                Reward reward = rewards.get(i);
                if (mode.showsItems()) RewardRenderer.renderCustomReward(stack, itemRenderer, font,
                        SakuraClientState.getThemeTexture(), SakuraClientState.getThemeTextureCoordinate(), reward, x, y, true, true);
                else {
                    shape(stack, x, y, 16, 16, getEffectiveTheme().buttonBgHover(), 3, 1);
                    font.draw(stack, "?", x + 5, y + 4, getEffectiveTheme().textPrimary());
                }
                deferRewardTooltip(stack, pool, reward, mode, x, y);
            }
        } finally { AbstractGuiUtils.popScissor(); }
    }

    @Override
    protected void onMouseScrolled(MouseScrolledHandleArgs event) {
        if (dropdownOpen()) return;
        if (infoViewport.contains(event.mouseX(), event.mouseY())) {
            infoViewport.scrollBy(-event.delta()); event.consumed(true);
        } else if (descriptionViewport.contains(event.mouseX(), event.mouseY())) {
            descriptionViewport.scrollBy(-event.delta()); event.consumed(true);
        } else if (layout.preview().contains(event.mouseX(), event.mouseY()) && previewScrollbar.visible()) {
            previewScrollRows -= (int) Math.signum(event.delta()); event.consumed(true);
        }
    }

    private boolean dropdownOpen() {
        return poolSelect.dropdownOpen() || countSelect.dropdownOpen() || animationSelect.dropdownOpen();
    }

    private void deferRewardTooltip(PoseStack stack, LotteryPool pool, Reward reward,
                                    LotteryPreviewMode mode, int x, int y) {
        double mx = inputState.mouseX(), my = inputState.mouseY();
        if (dropdownOpen() || !layout.preview().contains(mx, my) || mx < x || mx >= x + 18 || my < y || my >= y + 18) return;
        List<String> lines = new ArrayList<>();
        if (mode.showsItems()) lines.add(xin.vanilla.sakura.api.reward.client.SakuraRewardClient.displayName(
                reward, Minecraft.getInstance().options.languageCode, true).toString());
        if (mode.showsProbabilities()) {
            BigDecimal total = pool.getRewards().stream().map(Reward::getProbability)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal percent = total.signum() <= 0 ? BigDecimal.ZERO : reward.getProbability().multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP).stripTrailingZeros();
            lines.add(SakuraComponent.get().translateClient("format", "lottery_probability_s", percent.toPlainString()));
        }
        if (!lines.isEmpty()) tooltip(stack, String.join("\n", lines));
    }

    private void tooltip(PoseStack stack, String text) {
        int mx = (int) inputState.mouseX(), my = (int) inputState.mouseY();
        addDeferredTooltipRender(s -> TooltipWidget.drawPopupMessage(s,
                FontDrawArgs.ofPopo(Text.literal(text).stack(s)).x(mx).y(my), getEffectiveTheme(), season()));
    }

    private void configureCountOptions() {
        if (countSelect == null) return;
        List<String> values = drawCounts(selectedPool()).stream().filter(value -> !"1".equals(value)).collect(Collectors.toList());
        if (values.isEmpty()) values.add("1");
        String previous = countSelect.getSelectedValues().isEmpty() ? "" : countSelect.getSelectedValues().get(0);
        countSelect.optionEntries(values.stream().map(value -> new DropdownOption(value,
                "all".equals(value) ? SakuraComponent.get().translateClient("word", "lottery_draw_all")
                        : SakuraComponent.get().translateClient("format", "lottery_draw_count_s", value),
                net.minecraft.world.item.ItemStack.EMPTY, null, null)).collect(Collectors.toList()));
        countSelect.selectedValues(Collections.singletonList(values.contains(previous) ? previous : values.get(0)));
        selectPayment();
    }

    private int batchCount() {
        String count = countSelect.getSelectedValues().isEmpty() ? "1" : countSelect.getSelectedValues().get(0);
        return "all".equals(count) ? -1 : Integer.parseInt(count);
    }
    private void selectPayment() { payment.select(selectedPoolId, batchCount()); }

    static List<String> drawCounts(LotteryPool pool) {
        List<String> values = new ArrayList<>(); values.add("1");
        if (pool == null) return values;
        int max = pool.getLimitPolicy() == LotteryLimitPolicy.UNLIMITED ? LotteryRewardService.MAX_BATCH_DRAWS
                : pool.getLimitPolicy() == LotteryLimitPolicy.COOLDOWN ? 1 : Math.min(pool.getMaxDraws(), LotteryRewardService.MAX_BATCH_DRAWS);
        for (int count = 5; count <= max; count += 5) values.add(String.valueOf(count));
        if (max > 1 && max % 5 != 0) values.add(String.valueOf(max));
        if (max > 1 && pool.getLimitPolicy() != LotteryLimitPolicy.UNLIMITED && pool.getLimitPolicy() != LotteryLimitPolicy.COOLDOWN) values.add("all");
        return values;
    }
    private LotteryPool selectedPool() { return pools().stream().filter(pool -> pool.getId().equals(selectedPoolId)).findFirst().orElse(null); }
    private static List<LotteryPool> pools() { return RewardConfigManager.getRewardConfig().getLotteryPools(); }
    private static String policyName(LotteryPool pool) { return SakuraComponent.get().translateClient("word", "lottery_policy_" + pool.getLimitPolicy().name().toLowerCase(Locale.ROOT)); }

    @Override protected void onRemoved() { payment.cancelIntent(); super.onRemoved(); }
    @Override public void onClose() { payment.cancelIntent(); super.onClose(); }
    @Override protected ScreenCoordinate closeableWindowBounds() { return bounds(layout.panel()); }
    private static ScreenCoordinate bounds(LotteryLayout.Rect rect) { return new ScreenCoordinate(rect.x(), rect.y(), rect.width(), rect.height()); }
    private void shape(PoseStack stack, int x, int y, int width, int height, int color, int radius, int border) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(stack).type(ShapeDrawArgs.ShapeType.RECT).color(color)
                .rect(new ShapeDrawArgs.RectParams().x(x).y(y).width(width).height(height).radius(radius)
                        .cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE).border(border)));
    }
}
