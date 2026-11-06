package xin.vanilla.sakura.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.enums.EnumOrientation;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.notification.NotificationDetailViewport;
import xin.vanilla.banira.client.gui.widget.*;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientPreferences;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.lottery.LotteryLayout;
import xin.vanilla.sakura.client.gui.lottery.LotteryArtwork;
import xin.vanilla.sakura.client.gui.lottery.LotteryPaymentViewModel;
import xin.vanilla.sakura.client.ui.LotteryUiSession;
import xin.vanilla.sakura.client.ui.SakuraUiPages;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.*;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryDrawRequestPacket;
import xin.vanilla.sakura.network.packet.LotteryQuoteRequestPacket;
import xin.vanilla.sakura.reward.Reward;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class LotteryScreen extends BaniraScreen implements UiPageSource {
    private LotteryUiSession lotteryUiSession;
    private final Object uiConnection = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getConnection();
    private UiPageState lotteryPageState;
    private Double pendingDescriptionScroll;
    private final LotteryPaymentViewModel payment = new LotteryPaymentViewModel(
            SakuraClientState.getLotteryRequests(), () -> System.nanoTime() / 1_000_000,
            request -> SakuraNetwork.sendToServer(new LotteryQuoteRequestPacket(request)),
            request -> SakuraNetwork.sendToServer(new LotteryDrawRequestPacket(request)));
    private final NotificationDetailViewport infoViewport = new NotificationDetailViewport();
    private final NotificationDetailViewport descriptionViewport = new NotificationDetailViewport();
    private LotteryLayout layout;
    private final List<ButtonWidget> poolButtons = new ArrayList<>();
    private List<LotteryPool> railPools = List.of();
    private List<LotteryArtwork> railArt = List.of();
    private int poolOffset;
    private int selectedBatch;
    private DropdownSelectWidget batchPicker;
    private LotteryArtwork poster;
    private ScrollbarWidget poolScrollbar;
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

    private boolean uiConnected() {
        return uiConnection == Minecraft.getInstance().getConnection();
    }

    @Override
    public String uiSurfaceId() {
        return SakuraSignIn.MODID + ":lottery";
    }

    @Override
    public UiPageSpec uiPage() {
        return SakuraUiPages.page("lottery", this, pageSession());
    }

    @Override
    public UiPageSession pageSession() {
        if (lotteryUiSession == null) lotteryUiSession = new LotteryUiSession(new LotteryUiSession.Port() {
            public void prepare() {
                prepareSelection();
            }

            public List<LotteryPool> pools() {
                return LotteryScreen.pools();
            }

            public String poolId() {
                return selectedPoolId;
            }

            public void pool(String id) {
                selectPool(id);
            }

            public int batch() {
                return batchCount();
            }

            public void batch(int value) {
                selectedBatch = value;
            }

            public long generation() {
                return SakuraClientState.getLotteryRequests().getGeneration();
            }

            public LotteryPaymentViewModel payment() {
                return payment;
            }

            public String name(Reward reward) {
                return xin.vanilla.sakura.api.reward.client.SakuraRewardClient.displayName(reward, Minecraft.getInstance().options.languageCode, true).toString();
            }
        }, this::uiConnected);
        return lotteryUiSession;
    }

    private void prepareSelection() {
        if (selectedPoolId.isEmpty()) selectedPoolId = SakuraClientPreferences.lastLotteryPoolId();
        validateSelection();
        selectPayment();
    }

    private void selectPool(String id) {
        if (payment.isDrawing() || selectedPoolId.equals(id)) return;
        selectedPoolId = id;
        SakuraClientPreferences.lastLotteryPoolId(id);
        previewScrollRows = 0;
        infoViewport.reset();
        descriptionViewport.reset();
        pendingDescriptionScroll = null;
        prepareSelection();
        if (batchPicker != null) configureBatches();
    }

    @Override
    public void capturePageState(UiPageState state) {
        lotteryPageState = state;
        if (state != null && layout != null && font != null) {
            state.rowPosition(previewScrollRows * Math.max(1, (layout.preview().width() - 10) / 34));
            state.detailScroll(descriptionViewport.scrollRows() * font.lineHeight);
        }
    }

    @Override
    public void preparePageState(UiPageState state) {
        lotteryPageState = state;
    }

    @Override
    protected void initWidgets() {
        layout = LotteryLayout.calculate(width, height);
        prepareSelection();
        if (lotteryPageState != null) {
            previewScrollRows = (int) lotteryPageState.rowPosition() / Math.max(1, (layout.preview().width() - 10) / 34);
            pendingDescriptionScroll = lotteryPageState.detailScroll();
        }
        poolButtons.clear();
        for (int i = 0; i < layout.poolRows(); i++) {
            int index = i;
            ButtonWidget button = new ButtonWidget(this);
            button.bounds(bounds(layout.poolRow(i)));
            button.onClick(ignored -> {
                int actual = poolOffset + index;
                if (actual < railPools.size()) selectPool(railPools.get(actual).getId());
            });
            addWidget(button);
            poolButtons.add(button);
        }
        poolScrollbar = scrollbar(value -> setPoolOffset((int) Math.round(value)));
        LotteryLayout.Rect rail = layout.pool();
        poolScrollbar.bounds(new ScreenCoordinate(rail.right() - 5, rail.y(), 5, rail.height()));
        refreshPoolOptions();

        batchPicker = new DropdownSelectWidget(this);
        batchPicker.inputMode(DropdownInputMode.SELECTION_ONLY);
        batchPicker.onSelectionChanged(values -> {
            if (!values.isEmpty()) selectedBatch = Integer.parseInt(values.get(0));
        });
        addWidget(batchPicker);

        singleDrawButton = new ButtonWidget(this);
        singleDrawButton.bounds(bounds(layout.single()));
        singleDrawButton.text(SakuraComponent.get().transClient("word", "lottery_single_draw"));
        singleDrawButton.onClick(button -> payment.click(1));
        singleDrawButton.enabled(false);
        addWidget(singleDrawButton);

        batchDrawButton = new ButtonWidget(this);
        batchDrawButton.bounds(bounds(layout.batch()));
        batchDrawButton.text(SakuraComponent.get().transClient("word", "lottery_batch_draw"));
        batchDrawButton.onClick(button -> payment.click(batchCount()));
        batchDrawButton.enabled(false);
        addWidget(batchDrawButton);

        ButtonWidget closeButton = new ButtonWidget(this);
        closeButton.presetStyleClose().radius(3).padding(1);
        closeButton.bounds(bounds(layout.close()));
        closeButton.onClick(button -> requestClose(CloseReason.BUTTON));
        addWidget(closeButton);
        previewScrollbar = scrollbar(value -> previewScrollRows = (int) Math.round(value));
        infoScrollbar = scrollbar(infoViewport::scrollTo);
        descriptionScrollbar = scrollbar(descriptionViewport::scrollTo);
        configureBatches();
        updateDrawButtons();
        displayedGeneration = SakuraClientState.getLotteryRequests().getGeneration();
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
            validateSelection();
            refreshPoolOptions();
            selectPayment();
            configureBatches();
        }
        payment.tick();
        updateDrawButtons();
    }

    private void updateDrawButtons() {
        LotteryPool pool = selectedPool();
        boolean single = pool != null && pool.isSingleDraw();
        int batch = batchCount();
        batchPicker.enabled(!payment.isDrawing());
        singleDrawButton.visible(single);
        singleDrawButton.enabled(single && payment.canDraw(1));
        batchDrawButton.visible(batch > 1);
        batchDrawButton.enabled(batch > 1 && payment.canDraw(batch));
        batchDrawButton.text(SakuraComponent.get().transClient("format", "lottery_draw_count_s", batch));
        for (int i = 0; i < poolButtons.size(); i++) {
            ButtonWidget button = poolButtons.get(i);
            int actual = poolOffset + i;
            button.enabled(actual < railPools.size() && !payment.isDrawing());
        }
    }

    private void validateSelection() {
        if (pools().stream().noneMatch(pool -> pool.getId().equals(selectedPoolId))) {
            selectedPoolId = pools().isEmpty() ? "" : pools().get(0).getId();
            SakuraClientPreferences.lastLotteryPoolId(selectedPoolId);
            infoViewport.reset();
            descriptionViewport.reset();
            previewScrollRows = 0;
        }
    }

    private void refreshPoolOptions() {
        railPools = List.copyOf(pools());
        railArt = railPools.stream().map(LotteryArtwork::thumbnail).toList();
        int selected = 0;
        for (int i = 0; i < railPools.size(); i++)
            if (railPools.get(i).getId().equals(selectedPoolId)) selected = i;
        setPoolOffset(Math.min(selected, Math.max(0, railPools.size() - poolButtons.size())));
    }

    private void setPoolOffset(int offset) {
        int maximum = Math.max(0, railPools.size() - poolButtons.size());
        poolOffset = Math.max(0, Math.min(offset, maximum));
        poolScrollbar.maxValue(maximum).visibleSize(poolButtons.size()).visible(maximum > 0);
        if ((int) Math.round(poolScrollbar.value()) != poolOffset) poolScrollbar.setValue(poolOffset);
        for (int i = 0; i < poolButtons.size(); i++) poolButtons.get(i).visible(poolOffset + i < railPools.size());
    }

    private void drawPoolRail(GuiGraphics stack, double mouseX, double mouseY) {
        for (int i = 0; i < poolButtons.size() && poolOffset + i < railPools.size(); i++) {
            LotteryLayout.Rect row = layout.poolRow(i);
            int index = poolOffset + i;
            LotteryPool pool = railPools.get(index);
            if (pool.getId().equals(selectedPoolId))
                shape(stack, row.x() + 2, row.y() + 2, row.width() - 4, row.height() - 4, getEffectiveTheme().buttonBgHover(), 4, 0);
            railArt.get(index).render(stack, row.x() + 5, row.y() + 3, 29, row.height() - 6, 17);
            String title = pool.getDisplayName();
            int room = row.width() - 41;
            stack.drawString(font, font.plainSubstrByWidth(title, room), row.x() + 37, row.y() + 10, getEffectiveTheme().textPrimary(), false);
            if (row.contains(mouseX, mouseY)) tooltip(stack, title);
        }
    }

    @Override
    protected void onRender(GuiGraphics stack, int mouseX, int mouseY, float partialTicks) {
        LotteryLayout.Rect panel = layout.panel();
        shape(stack, panel.x(), panel.y(), panel.width(), panel.height(), getEffectiveTheme().panelBg(), 8, 0);
        stack.drawString(font, font.plainSubstrByWidth(getTitle().getString(), layout.title().width()),
                layout.title().x(), layout.title().y(), getEffectiveTheme().textPrimary(), false);
        LotteryPool pool = selectedPool();
        if (pool == null) {
            renderParagraph(stack, layout.info(), infoViewport, infoScrollbar,
                    SakuraComponent.get().translateClient("word", "lottery_no_pools"), getEffectiveTheme().textSecondary());
            previewScrollbar.visible(false);
            descriptionScrollbar.visible(false);
        } else {
            LotteryLayout.Rect heading = layout.heading(), poster = layout.poster();
            shape(stack, poster.x(), poster.y(), poster.width(), poster.height(), getEffectiveTheme().buttonBgHover(), 5, 0);
            if (this.poster != null) this.poster.render(stack, poster.x() + 4, poster.y() + 4,
                    poster.width() - 8, poster.height() - 8, 82);
            stack.drawString(font, font.plainSubstrByWidth(pool.getDisplayName(), heading.width()),
                    heading.x(), heading.y(), getEffectiveTheme().textPrimary(), false);
            if (heading.contains(mouseX, mouseY)) tooltip(stack, pool.getDisplayName());
            String details = SakuraComponent.get().translateClient("format", "lottery_pool_details_sss",
                    policyName(pool), pool.getMaxDraws(), pool.getCooldownSeconds());
            String fee = payment.summary();
            String cost = fee == null ? SakuraComponent.get().translateClient("word", "lottery_quote_pending")
                    : SakuraComponent.get().translateClient("format", "lottery_cost_per_draw_s",
                    fee.isEmpty() ? SakuraComponent.get().translateClient("word", "lottery_free") : fee);
            LotteryQuote quote = payment.quote();
            String status = payment.isDrawing() ? SakuraComponent.get().translateClient("word", "lottery_drawing")
                    : quote != null && !quote.isReady() && !payment.isPending()
                    ? SakuraComponent.get().translateClient("word", quote.getStatus() == LotteryQuote.Status.POOL_CLOSED
                    ? "lottery_pool_closed" : "lottery_payment_unavailable") : "";
            String schedule = LotteryUiSession.schedule(pool);
            if (!schedule.isEmpty()) details += "\n" + schedule;
            renderParagraph(stack, layout.info(), infoViewport, infoScrollbar,
                    details + "\n" + cost + (status.isEmpty() ? "" : "\n" + status), getEffectiveTheme().textSecondary());
            renderParagraph(stack, layout.description(), descriptionViewport, descriptionScrollbar,
                    pool.getDescription(), getEffectiveTheme().textPrimary());
            renderPreview(stack, pool);
        }
        renderWidgets(stack, partialTicks);
        drawPoolRail(stack, mouseX, mouseY);
    }

    private void renderParagraph(GuiGraphics stack, LotteryLayout.Rect area,
                                 NotificationDetailViewport viewport, ScrollbarWidget scrollbar, String text, int color) {
        List<FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(text), area.width() - 9);
        viewport.layout(area.x(), area.y(), area.width() - 9, area.height(), font.lineHeight, lines.size());
        if (viewport == descriptionViewport && pendingDescriptionScroll != null) {
            viewport.scrollTo(pendingDescriptionScroll / font.lineHeight);
            pendingDescriptionScroll = null;
        }
        AbstractGuiUtils.pushScissor(area.x(), area.y(), area.width() - 9, area.height());
        try {
            for (int i = viewport.firstLine(); i < viewport.lastLineExclusive(); i++)
                stack.drawString(font, lines.get(i), area.x(), viewport.lineY(i), color, false);
        } finally {
            AbstractGuiUtils.popScissor();
        }
        scrollbar.bounds(new ScreenCoordinate(area.right() - 5, area.y(), 5, area.height()));
        scrollbar.maxValue(viewport.maxScrollRows()).visibleSize(viewport.visibleRows()).visible(viewport.maxScrollRows() > 0);
        scrollbar.setValue(viewport.scrollRows());
    }

    private void renderPreview(GuiGraphics stack, LotteryPool pool) {
        LotteryLayout.Rect area = layout.preview();
        LotteryPreviewMode mode = pool.getPreviewMode();
        List<Reward> rewards = pool.getRewards();
        if (mode == LotteryPreviewMode.NONE || rewards.isEmpty()) {
            previewScrollbar.visible(false);
            String text = SakuraComponent.get().translateClient("word",
                    mode == LotteryPreviewMode.NONE ? "lottery_rewards_hidden" : "lottery_pool_empty");
            AbstractGuiUtils.pushScissor(area.x(), area.y(), area.width(), area.height());
            try {
                List<FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(text), area.width());
                for (int i = 0; i < lines.size() && i * font.lineHeight < area.height(); i++)
                    stack.drawString(font, lines.get(i), area.x(), area.y() + i * font.lineHeight, getEffectiveTheme().textSecondary(), false);
            } finally {
                AbstractGuiUtils.popScissor();
            }
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
                if (mode.showsItems()) RewardRenderer.renderCustomReward(stack, font,
                        SakuraClientState.getThemeTexture(), SakuraClientState.getThemeTextureCoordinate(), reward, x, y, true, true);
                else {
                    shape(stack, x, y, 16, 16, getEffectiveTheme().buttonBgHover(), 3, 1);
                    stack.drawString(font, "?", x + 5, y + 4, getEffectiveTheme().textPrimary(), false);
                }
                deferRewardTooltip(stack, pool, reward, mode, x, y);
            }
        } finally {
            AbstractGuiUtils.popScissor();
        }
    }

    @Override
    protected void onMouseScrolled(MouseScrolledHandleArgs event) {
        if (batchPicker.dropdownOpen()) return;
        if (layout.pool().contains(event.mouseX(), event.mouseY())) {
            setPoolOffset(poolOffset - (int) Math.signum(event.delta()));
            event.consumed(true);
            return;
        }
        if (infoViewport.contains(event.mouseX(), event.mouseY())) {
            infoViewport.scrollBy(-event.delta());
            event.consumed(true);
        } else if (descriptionViewport.contains(event.mouseX(), event.mouseY())) {
            descriptionViewport.scrollBy(-event.delta());
            event.consumed(true);
        } else if (layout.preview().contains(event.mouseX(), event.mouseY()) && previewScrollbar.visible()) {
            previewScrollRows -= (int) Math.signum(event.delta());
            event.consumed(true);
        }
    }

    private void deferRewardTooltip(GuiGraphics stack, LotteryPool pool, Reward reward,
                                    LotteryPreviewMode mode, int x, int y) {
        double mx = inputState.mouseX(), my = inputState.mouseY();
        if (batchPicker.dropdownOpen() || !layout.preview().contains(mx, my) || mx < x || mx >= x + 18 || my < y || my >= y + 18)
            return;
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

    private void tooltip(GuiGraphics stack, String text) {
        int mx = (int) inputState.mouseX(), my = (int) inputState.mouseY();
        addDeferredTooltipRender(s -> TooltipWidget.drawPopupMessage(s.pose(),
                FontDrawArgs.ofPopo(Text.literal(text).stack(s.pose())).x(mx).y(my), getEffectiveTheme(), season()));
    }

    private void configureBatches() {
        LotteryPool pool = selectedPool();
        poster = pool == null ? null : LotteryArtwork.resolve(pool);
        List<Integer> choices = pool == null ? List.of() : pool.getDrawCounts().stream().filter(count -> count > 1).toList();
        if (!choices.contains(selectedBatch)) selectedBatch = choices.isEmpty() ? 0 : choices.get(0);
        batchPicker.visible(choices.size() > 1);
        LotteryLayout.Rect area = layout.batch();
        int pickerWidth = choices.size() > 1 ? 30 : 0;
        if (pickerWidth > 0) {
            batchPicker.bounds(new ScreenCoordinate(area.x(), area.y(), pickerWidth, area.height()));
            batchPicker.optionEntries(choices.stream().map(count -> new DropdownOption(
                    String.valueOf(count), String.valueOf(count), net.minecraft.world.item.ItemStack.EMPTY, null, null)).toList());
            batchPicker.selectedValues(List.of(String.valueOf(selectedBatch)));
        }
        int spacing = pickerWidth > 0 ? pickerWidth + 3 : 0;
        batchDrawButton.bounds(new ScreenCoordinate(area.x() + spacing, area.y(), area.width() - spacing, area.height()));
    }

    private int batchCount() {
        LotteryPool pool = selectedPool();
        if (pool == null) return 0;
        return selectedBatch > 1 && pool.allowsCount(selectedBatch) ? selectedBatch
                : pool.getDrawCounts().stream().filter(count -> count > 1).findFirst().orElse(0);
    }

    private void selectPayment() {
        LotteryPool pool = selectedPool();
        if (pool == null) payment.select("", List.of());
        else payment.select(selectedPoolId, pool.getDrawCounts().stream().sorted().toList());
    }

    static List<String> drawCounts(LotteryPool pool) {
        return pool == null ? List.of() : pool.getDrawCounts().stream().map(String::valueOf).toList();
    }

    private LotteryPool selectedPool() {
        return pools().stream().filter(pool -> pool.getId().equals(selectedPoolId)).findFirst().orElse(null);
    }

    private static List<LotteryPool> pools() {
        return RewardConfigManager.getRewardConfig().getLotteryPools();
    }

    private static String policyName(LotteryPool pool) {
        return SakuraComponent.get().translateClient("word", "lottery_policy_" + pool.getLimitPolicy().name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void onRemoved() {
        BaniraUi.screenRemoved(this);
        super.onRemoved();
    }

    @Override
    public void onClose() {
        if (BaniraUi.closePage(this, UiCloseReason.USER_CLOSE)) return;
        if (lotteryUiSession != null && lotteryUiSession.requestClose()) return;
        payment.cancelIntent();
        super.onClose();
    }

    @Override
    protected ScreenCoordinate closeableWindowBounds() {
        return bounds(layout.panel());
    }

    private static ScreenCoordinate bounds(LotteryLayout.Rect rect) {
        return new ScreenCoordinate(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private void shape(GuiGraphics stack, int x, int y, int width, int height, int color, int radius, int border) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(stack.pose()).type(ShapeDrawArgs.ShapeType.RECT).color(color)
                .rect(new ShapeDrawArgs.RectParams().x(x).y(y).width(width).height(height).radius(radius)
                        .cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE).border(border)));
    }
}
