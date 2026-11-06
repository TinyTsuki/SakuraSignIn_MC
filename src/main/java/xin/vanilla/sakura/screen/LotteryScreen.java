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
    private static final int STAGE_FILL = 0xff3e2c3c;
    private static final int STAGE_EDGE = 0xff826073;
    private static final int STAGE_TEXT = 0xfffff3f5;
    private static final int STAGE_MUTED = 0xffebcdd7;
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
            state.rowPosition(previewScrollRows * layout.rewardColumns());
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
            previewScrollRows = (int) lotteryPageState.rowPosition() / layout.rewardColumns();
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
            boolean active = pool.getId().equals(selectedPoolId);
            boolean hovered = row.contains(mouseX, mouseY) && !batchPicker.dropdownOpen();
            int border = active ? 2 : 1;
            shape(stack, row.x(), row.y(), row.width(), row.height(), active ? 0xffffb7d2 : hovered ? STAGE_EDGE : STAGE_FILL, 4, 0);
            shape(stack, row.x() + border, row.y() + border, row.width() - 2 * border, row.height() - 2 * border,
                    active ? 0xff69445b : STAGE_FILL, 3, 0);
            railArt.get(index).render(stack, row.x() + 4, row.y() + 2, row.width() - 8, row.height() - 15, 24);
            String title = pool.getDisplayName();
            String label = font.plainSubstrByWidth(title, row.width() - 8);
            stack.drawCenteredString(font, label, row.x() + row.width() / 2, row.bottom() - 11, STAGE_TEXT);
            if (hovered) tooltip(stack, title);
        }
    }

    @Override
    protected void onRender(GuiGraphics stack, int mouseX, int mouseY, float partialTicks) {
        LotteryLayout.Rect panel = layout.panel();
        shape(stack, panel.x(), panel.y(), panel.width(), panel.height(), getEffectiveTheme().panelBg(), 8, 0);
        LotteryLayout.Rect stage = layout.stage(), footer = layout.footer();
        shape(stack, stage.x(), stage.y(), stage.width(), stage.height(), STAGE_EDGE, 5, 0);
        shape(stack, stage.x() + 1, stage.y() + 1, stage.width() - 2, stage.height() - 2, STAGE_FILL, 4, 0);
        stack.fill(footer.x(), footer.y(), footer.right(), footer.y() + 1, STAGE_EDGE);
        LotteryPool pool = selectedPool();
        renderPoolHeading(stack, pool);
        if (pool == null) {
            renderParagraph(stack, layout.info(), infoViewport, infoScrollbar,
                    SakuraComponent.get().translateClient("word", "lottery_no_pools"), STAGE_MUTED);
            previewScrollbar.visible(false);
            descriptionScrollbar.visible(false);
        } else {
            LotteryLayout.Rect poster = layout.poster();
            if (this.poster != null) this.poster.render(stack, poster.x(), poster.y(),
                    poster.width(), poster.height(), Math.min(poster.width(), poster.height()) * 3 / 4);
            renderParagraph(stack, layout.info(), infoViewport, infoScrollbar, LotteryUiSession.countdown(payment), STAGE_MUTED);
            renderParagraph(stack, layout.description(), descriptionViewport, descriptionScrollbar,
                    pool.getDescription(), STAGE_TEXT);
            LotteryLayout.Rect caption = layout.previewHeading();
            String previewTitle = SakuraComponent.get().translateClient("word", "lottery_candidates");
            stack.drawString(font, font.plainSubstrByWidth(previewTitle, caption.width()), caption.x(), caption.y(), STAGE_MUTED, false);
            renderPreview(stack, pool);
            renderBilling(stack, pool, mouseX, mouseY);
        }
        renderWidgets(stack, partialTicks);
        drawPoolRail(stack, mouseX, mouseY);
    }

    private void renderPoolHeading(GuiGraphics stack, LotteryPool pool) {
        var title = pool == null ? getTitle() : net.minecraft.network.chat.Component.literal(pool.getDisplayName());
        LotteryLayout.Rect area = layout.title();
        float scale = 1.4F;
        List<FormattedCharSequence> lines = font.split(title, (int) (area.width() / scale));
        while (lines.size() * font.lineHeight * scale > area.height() && scale > .5F) {
            scale -= .1F;
            lines = font.split(title, (int) (area.width() / scale));
        }
        int titleHeight = (int) Math.ceil(lines.size() * font.lineHeight * scale);
        stack.pose().pushPose();
        try {
            stack.pose().translate(area.x(), area.y(), 0);
            stack.pose().scale(scale, scale, 1);
            for (int i = 0; i < lines.size(); i++)
                stack.drawString(font, lines.get(i), 0, i * font.lineHeight, getEffectiveTheme().textPrimary(), false);
        } finally {
            stack.pose().popPose();
        }
        if (pool != null) {
            LotteryLayout.Rect policy = layout.policy();
            stack.drawString(font, font.plainSubstrByWidth(LotteryUiSession.quota(pool), policy.width()),
                    policy.x(), Math.min(policy.y(), area.y() + titleHeight + 1), getEffectiveTheme().textSecondary(), false);
        }
    }

    private void renderBilling(GuiGraphics stack, LotteryPool pool, int mouseX, int mouseY) {
        var billing = LotteryUiSession.billing(payment, pool, displayedGeneration);
        LotteryLayout.Rect area = layout.billing();
        stack.drawString(font, font.plainSubstrByWidth(billing.cost(), area.width()), area.x(), area.y(), getEffectiveTheme().textPrimary(), false);
        stack.drawString(font, font.plainSubstrByWidth(billing.available(), area.width()), area.x(), area.y() + 9, getEffectiveTheme().textSecondary(), false);
        if (!billing.status().isEmpty())
            stack.drawString(font, font.plainSubstrByWidth(billing.status(), area.width()), area.x(), area.y() + 18, getEffectiveTheme().textSecondary(), false);
        if (area.contains(mouseX, mouseY) && !batchPicker.dropdownOpen())
            tooltip(stack, billing.cost() + "\n" + billing.available() + (billing.status().isEmpty() ? "" : "\n" + billing.status()));
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
        if (area.height() < font.lineHeight) {
            previewScrollbar.visible(false);
            return;
        }
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
                    stack.drawString(font, lines.get(i), area.x(), area.y() + i * font.lineHeight, STAGE_MUTED, false);
            } finally {
                AbstractGuiUtils.popScissor();
            }
            return;
        }
        int stride = LotteryLayout.REWARD_STRIDE;
        int columns = layout.rewardColumns();
        int visibleRows = Math.max(1, (area.height() + 3) / stride);
        int maxRows = Math.max(0, (rewards.size() + columns - 1) / columns - visibleRows);
        previewScrollRows = Math.max(0, Math.min(previewScrollRows, maxRows));
        previewScrollbar.bounds(new ScreenCoordinate(area.right() - 5, area.y(), 5, area.height()));
        previewScrollbar.maxValue(maxRows).visibleSize(visibleRows).visible(maxRows > 0);
        previewScrollbar.setValue(previewScrollRows);
        int start = previewScrollRows * columns, end = Math.min(rewards.size(), start + columns * visibleRows);
        AbstractGuiUtils.pushScissor(area.x(), area.y(), area.width() - 7, area.height());
        try {
            for (int i = start; i < end; i++) {
                int local = i - start, x = area.x() + 3 + local % columns * stride, y = area.y() + 3 + local / columns * stride;
                Reward reward = rewards.get(i);
                shape(stack, x - 2, y - 2, 22, 22, STAGE_EDGE, 3, 0);
                shape(stack, x - 1, y - 1, 20, 20, STAGE_FILL, 2, 0);
                if (mode.showsItems()) RewardRenderer.renderCustomReward(stack, font,
                        SakuraClientState.getThemeTexture(), SakuraClientState.getThemeTextureCoordinate(), reward, x, y, true, true);
                else {
                    stack.drawString(font, "?", x + 5, y + 4, STAGE_TEXT, false);
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
