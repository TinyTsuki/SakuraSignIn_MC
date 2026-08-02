package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.ItemRenderer;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.banira.client.gui.widget.DropdownSelectWidget;
import xin.vanilla.banira.client.gui.widget.DropdownInputMode;
import xin.vanilla.banira.client.gui.widget.ScrollbarWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.enums.EnumOrientation;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.SakuraClientPreferences;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.LotteryDrawRequestPacket;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.lottery.LotteryRewardService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 玩家选择奖池、连抽次数并按服务端许可预览奖励。 */
public final class LotteryScreen extends BaniraScreen {
    private static final int PANEL_TOP = 24;
    private static final int CLOSE_SIZE = 10;
    private static final int CLOSE_PAD = 6;
    private final Screen parent;
    private final ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
    private DropdownSelectWidget poolSelect;
    private DropdownSelectWidget countSelect;
    private ButtonWidget singleDrawButton;
    private ButtonWidget batchDrawButton;
    private String selectedPoolId = "";
    private ScreenCoordinate previewArea;
    private int previewScrollRows;
    private ScrollbarWidget previewScrollbar;

    public LotteryScreen(Screen parent) {
        super(SakuraComponent.get().transClient("word", "lottery_screen"));
        this.parent = parent;
        previousScreen(parent);
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    protected void initWidgets() {
        int panelWidth = Math.min(560, width - 40);
        int panelX = (width - panelWidth) / 2;
        List<LotteryPool> pools = pools();
        if (selectedPoolId.isEmpty()) {
            selectedPoolId = SakuraClientPreferences.lastLotteryPoolId();
        }
        if (pools.stream().noneMatch(pool -> pool.getId().equals(selectedPoolId))) {
            selectedPoolId = pools.isEmpty() ? "" : pools.get(0).getId();
            SakuraClientPreferences.lastLotteryPoolId(selectedPoolId);
        }

        poolSelect = new DropdownSelectWidget(this);
        poolSelect.inputMode(DropdownInputMode.SELECTION_ONLY);
        poolSelect.bounds(new ScreenCoordinate(panelX + 20, 54, panelWidth - 40, 22));
        poolSelect.optionEntries(pools.stream().map(pool -> new DropdownOption(
                pool.getId(), pool.getDisplayName(), net.minecraft.item.ItemStack.EMPTY,
                null, SakuraComponent.get().transClient("format", "lottery_pool_tooltip_sss",
                policyName(pool), pool.getMaxDraws(), pool.getCooldownSeconds())))
                .collect(Collectors.toList()));
        poolSelect.selectedValues(selectedPoolId.isEmpty()
                ? Collections.emptyList() : Collections.singletonList(selectedPoolId));
        poolSelect.onSelectionChanged(values -> {
            selectedPoolId = values.isEmpty() ? "" : values.get(0);
            SakuraClientPreferences.lastLotteryPoolId(selectedPoolId);
            previewScrollRows = 0;
            configureCountOptions();
            configurePreviewScrollbar();
        });
        addWidget(poolSelect);

        countSelect = new DropdownSelectWidget(this);
        countSelect.inputMode(DropdownInputMode.SELECTION_ONLY);
        int footerWidth = panelWidth - 40;
        int singleWidth = Math.max(70, footerWidth / 4);
        int batchWidth = Math.max(70, footerWidth / 4);
        int countWidth = footerWidth - singleWidth - batchWidth - 12;
        singleDrawButton = new ButtonWidget(this);
        singleDrawButton.bounds(new ScreenCoordinate(panelX + 20, height - 62,
                singleWidth, 22));
        singleDrawButton.text(SakuraComponent.get().transClient(
                "word", "lottery_single_draw"));
        singleDrawButton.onClick(button -> requestDraw(1));
        addWidget(singleDrawButton);

        countSelect.bounds(new ScreenCoordinate(panelX + 20 + singleWidth + 6,
                height - 62, countWidth, 22));
        addWidget(countSelect);

        batchDrawButton = new ButtonWidget(this);
        batchDrawButton.bounds(new ScreenCoordinate(
                panelX + panelWidth - 20 - batchWidth, height - 62, batchWidth, 22));
        batchDrawButton.text(SakuraComponent.get().transClient(
                "word", "lottery_batch_draw"));
        batchDrawButton.onClick(button -> requestBatchDraw());
        addWidget(batchDrawButton);

        ButtonWidget closeButton = new ButtonWidget(this);
        closeButton.presetStyleClose().radius(CLOSE_SIZE / 3f).padding(1);
        closeButton.bounds(new ScreenCoordinate(panelX + panelWidth - CLOSE_PAD - CLOSE_SIZE,
                PANEL_TOP + CLOSE_PAD, CLOSE_SIZE, CLOSE_SIZE));
        closeButton.onClick(button -> requestClose(CloseReason.BUTTON));
        addWidget(closeButton);
        previewScrollbar = new ScrollbarWidget(this);
        previewScrollbar.orientation(EnumOrientation.VERTICAL).minValue(0).scrollStep(1)
                .onValueChanged(value -> previewScrollRows = (int) Math.round(value));
        addWidget(previewScrollbar);
        configureCountOptions();
        configurePreviewScrollbar();
    }

    @Override
    protected void onRender(MatrixStack stack, float partialTicks) {
        int panelWidth = Math.min(560, width - 40);
        int panelX = (width - panelWidth) / 2;
        shape(stack, panelX, PANEL_TOP, panelWidth, height - 44,
                getEffectiveTheme().panelBg(), 8, 0);
        centered(stack, SakuraComponent.get().translateClient("word", "lottery_screen"),
                34, getEffectiveTheme().textPrimary());

        LotteryPool pool = selectedPool();
        if (pool == null) {
            centered(stack, SakuraComponent.get().translateClient("word", "lottery_no_pools"),
                    height / 2, getEffectiveTheme().textSecondary());
        } else {
            String details = SakuraComponent.get().translateClient("format",
                    "lottery_pool_details_sss", policyName(pool), pool.getMaxDraws(),
                    pool.getCooldownSeconds());
            centered(stack, details, 84, getEffectiveTheme().textSecondary());
            renderPreview(stack, pool, panelX + 20, 104, panelWidth - 40,
                    Math.max(36, height - 184));
        }
        renderWidgets(stack, partialTicks);
    }

    private void renderPreview(MatrixStack stack, LotteryPool pool, int x, int y,
                               int width, int height) {
        shape(stack, x, y, width, height, getEffectiveTheme().buttonBg(), 5, 1);
        previewArea = new ScreenCoordinate(x, y, width, height);
        LotteryPreviewMode mode = pool.getPreviewMode();
        if (mode == LotteryPreviewMode.NONE) {
            centered(stack, "?", y + height / 2 - font.lineHeight / 2,
                    getEffectiveTheme().textPrimary());
            centered(stack, SakuraComponent.get().translateClient(
                    "word", "lottery_rewards_hidden"), y + height / 2 + 12,
                    getEffectiveTheme().textSecondary());
            return;
        }
        List<Reward> rewards = pool.getRewards();
        if (rewards.isEmpty()) {
            centered(stack, SakuraComponent.get().translateClient(
                    "word", "lottery_pool_empty"), y + height / 2,
                    getEffectiveTheme().textSecondary());
            return;
        }
        int columns = Math.max(1, (width - 10) / 34);
        int visibleRows = Math.max(1, (height - 16) / 34);
        int maxRows = Math.max(0, (rewards.size() + columns - 1) / columns - visibleRows);
        previewScrollRows = Math.max(0, Math.min(previewScrollRows, maxRows));
        if (previewScrollbar != null) {
            previewScrollbar.bounds(new ScreenCoordinate(x + width - 7, y + 8, 5, height - 16));
            previewScrollbar.maxValue(maxRows).visibleSize(visibleRows).visible(maxRows > 0);
            if ((int) Math.round(previewScrollbar.value()) != previewScrollRows) {
                previewScrollbar.setValue(previewScrollRows);
            }
        }
        int start = previewScrollRows * columns;
        int end = Math.min(rewards.size(), start + columns * visibleRows);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int itemX = x + 10 + (local % columns) * 34;
            int itemY = y + 10 + (local / columns) * 34;
            Reward reward = rewards.get(i);
            if (mode.showsItems()) {
                RewardRenderer.renderCustomReward(stack, itemRenderer, font,
                        SakuraClientState.getThemeTexture(),
                        SakuraClientState.getThemeTextureCoordinate(), reward,
                        itemX, itemY, true, true);
            } else {
                shape(stack, itemX, itemY, 16, 16, getEffectiveTheme().buttonBgHover(), 3, 1);
                font.draw(stack, "?", itemX + 5, itemY + 4, getEffectiveTheme().textPrimary());
            }
            deferRewardTooltip(stack, pool, reward, mode, itemX, itemY);
        }
    }

    @Override
    protected void onMouseScrolled(MouseScrolledHandleArgs eventArgs) {
        LotteryPool pool = selectedPool();
        if (pool == null || pool.getPreviewMode() == LotteryPreviewMode.NONE || previewArea == null) {
            return;
        }
        double mouseX = eventArgs.mouseX();
        double mouseY = eventArgs.mouseY();
        if (mouseX < previewArea.x() || mouseX >= previewArea.x() + previewArea.width()
                || mouseY < previewArea.y() || mouseY >= previewArea.y() + previewArea.height()) {
            return;
        }
        int columns = Math.max(1, ((int) previewArea.width() - 10) / 34);
        int visibleRows = Math.max(1, ((int) previewArea.height() - 16) / 34);
        int maxRows = Math.max(0,
                (pool.getRewards().size() + columns - 1) / columns - visibleRows);
        previewScrollRows = Math.max(0, Math.min(maxRows,
                previewScrollRows - (int) Math.signum(eventArgs.delta())));
        eventArgs.consumed(true);
    }

    private void configurePreviewScrollbar() {
        if (previewScrollbar == null) return;
        LotteryPool pool = selectedPool();
        int panelWidth = Math.min(560, width - 40);
        int previewWidth = panelWidth - 40;
        int previewHeight = Math.max(36, height - 184);
        int columns = Math.max(1, (previewWidth - 10) / 34);
        int visibleRows = Math.max(1, (previewHeight - 16) / 34);
        int count = pool == null ? 0 : pool.getRewards().size();
        int maxRows = Math.max(0, (count + columns - 1) / columns - visibleRows);
        previewScrollRows = Math.min(previewScrollRows, maxRows);
        previewScrollbar.maxValue(maxRows).visibleSize(visibleRows).visible(maxRows > 0);
        previewScrollbar.setValue(previewScrollRows);
    }

    private void deferRewardTooltip(MatrixStack stack, LotteryPool pool, Reward reward,
                                    LotteryPreviewMode mode, int x, int y) {
        double mouseX = inputState.mouseX();
        double mouseY = inputState.mouseY();
        if (mouseX < x || mouseX >= x + 18 || mouseY < y || mouseY >= y + 18) return;
        List<String> lines = new ArrayList<>();
        if (mode.showsItems()) {
            lines.add(xin.vanilla.sakura.api.reward.client.SakuraRewardClient.displayName(
                    reward, Minecraft.getInstance().options.languageCode, true).toString());
        }
        if (mode.showsProbabilities()) {
            BigDecimal total = pool.getRewards().stream().map(Reward::getProbability)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal percent = total.signum() <= 0 ? BigDecimal.ZERO
                    : reward.getProbability().multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP).stripTrailingZeros();
            lines.add(SakuraComponent.get().translateClient("format",
                    "lottery_probability_s", percent.toPlainString()));
        }
        if (lines.isEmpty()) return;
        Text tooltip = Text.literal(String.join("\n", lines));
        addDeferredTooltipRender(s -> TooltipWidget.drawPopupMessage(s,
                FontDrawArgs.ofPopo(tooltip.stack(s)).x((int) mouseX).y((int) mouseY),
                getEffectiveTheme(), season()));
    }

    private void configureCountOptions() {
        if (countSelect == null) {
            return;
        }
        LotteryPool pool = selectedPool();
        List<String> values = drawCounts(pool);
        List<String> batchValues = values.stream().filter(value -> !"1".equals(value))
                .collect(Collectors.toList());
        if (batchValues.isEmpty()) {
            batchValues.add("1");
        }
        countSelect.optionEntries(batchValues.stream().map(value -> new DropdownOption(
                value, "all".equals(value)
                ? SakuraComponent.get().translateClient("word", "lottery_draw_all")
                : SakuraComponent.get().translateClient("format", "lottery_draw_count_s", value),
                net.minecraft.item.ItemStack.EMPTY, null, null))
                .collect(Collectors.toList()));
        countSelect.selectedValues(Collections.singletonList(batchValues.get(0)));
        if (singleDrawButton != null) {
            singleDrawButton.enabled(pool != null);
        }
        if (batchDrawButton != null) {
            batchDrawButton.enabled(pool != null && !"1".equals(batchValues.get(0)));
        }
    }

    static List<String> drawCounts(LotteryPool pool) {
        List<String> values = new ArrayList<>();
        values.add("1");
        if (pool == null) {
            return values;
        }
        int max = pool.getLimitPolicy() == LotteryLimitPolicy.UNLIMITED
                || pool.getLimitPolicy() == LotteryLimitPolicy.COOLDOWN
                ? LotteryRewardService.MAX_BATCH_DRAWS
                : Math.min(pool.getMaxDraws(), LotteryRewardService.MAX_BATCH_DRAWS);
        for (int count = 5; count <= max; count += 5) {
            values.add(String.valueOf(count));
        }
        if (max > 1 && max % 5 != 0) {
            values.add(String.valueOf(max));
        }
        if (max > 1 && pool.getLimitPolicy() != LotteryLimitPolicy.UNLIMITED
                && pool.getLimitPolicy() != LotteryLimitPolicy.COOLDOWN) {
            values.add("all");
        }
        return values;
    }

    private void requestBatchDraw() {
        LotteryPool pool = selectedPool();
        if (pool == null || countSelect.getSelectedValues().isEmpty()) {
            return;
        }
        String count = countSelect.getSelectedValues().get(0);
        requestDraw("all".equals(count) ? -1 : Integer.parseInt(count));
    }

    private void requestDraw(int count) {
        LotteryPool pool = selectedPool();
        if (pool != null) {
            SakuraNetwork.sendToServer(new LotteryDrawRequestPacket(pool.getId(), count));
        }
    }

    private LotteryPool selectedPool() {
        return pools().stream().filter(pool -> pool.getId().equals(selectedPoolId))
                .findFirst().orElse(null);
    }

    private static List<LotteryPool> pools() {
        return RewardConfigManager.getRewardConfig().getLotteryPools();
    }

    private static String policyName(LotteryPool pool) {
        return SakuraComponent.get().translateClient("word",
                "lottery_policy_" + pool.getLimitPolicy().name().toLowerCase());
    }

    @Override
    protected ScreenCoordinate closeableWindowBounds() {
        int panelWidth = Math.min(560, width - 40);
        return new ScreenCoordinate((width - panelWidth) / 2, PANEL_TOP, panelWidth, height - 44);
    }

    private void shape(MatrixStack stack, int x, int y, int width, int height,
                       int color, int radius, int border) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs().stack(stack)
                .type(ShapeDrawArgs.ShapeType.RECT).color(color)
                .rect(new ShapeDrawArgs.RectParams().x(x).y(y).width(width).height(height)
                        .radius(radius).cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE)
                        .border(border)));
    }

    private void centered(MatrixStack stack, String text, int y, int color) {
        font.draw(stack, text, width / 2.0F - font.width(text) / 2.0F, y, color);
    }
}
