package xin.vanilla.sakura.screen;

import xin.vanilla.sakura.data.time.SakuraClock;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePresetValidator;

import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.client.gui.component.TextList;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.gui.ConfirmDialogScreen;
import xin.vanilla.banira.client.gui.ReadOnlyTextScreen;
import xin.vanilla.banira.api.client.BaniraInput;
import xin.vanilla.banira.client.util.SystemUtils;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
import xin.vanilla.banira.client.gui.widget.LabelWidget;
import xin.vanilla.banira.client.gui.widget.PopupOption;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.enums.EnumEllipsisPosition;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.NumberUtils;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.SakuraLang;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.RewardListEntryWidget;
import xin.vanilla.sakura.client.gui.RewardRuleTooltipFormatter;
import xin.vanilla.sakura.client.gui.RewardSelectionIds;
import xin.vanilla.sakura.client.gui.RewardKeyboardNavigator;
import xin.vanilla.sakura.client.gui.RewardOperationWidget;
import xin.vanilla.sakura.client.gui.RewardSelectionModel;
import xin.vanilla.sakura.client.gui.RewardSemanticMatcher;
import xin.vanilla.sakura.client.gui.RewardRenderer;
import xin.vanilla.sakura.client.gui.RewardProbabilityInput;
import xin.vanilla.sakura.client.reward.RewardEditorCoordinator;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.client.SakuraRewardClient;
import xin.vanilla.sakura.config.*;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.collection.StringList;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.DownloadRewardOptionNotice;
import xin.vanilla.sakura.network.packet.PersonalDatePresetSyncPacket;
import xin.vanilla.sakura.network.packet.LotteryPoolSyncPacket;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.notification.SakuraClientNotifications;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardManager;
import xin.vanilla.sakura.client.data.RewardClipboardManager;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.banira.common.util.CollectionUtils;
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.banira.client.data.GLFWKey;
import xin.vanilla.sakura.util.GLFWKeyHelper;
import xin.vanilla.sakura.util.SakuraUtils;
import xin.vanilla.banira.common.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
public class RewardOptionScreen extends BaniraScreen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String REWARD_TYPE_OPTION_PREFIX = "reward-type:";
    private static final ERewardRule[] REWARD_RULES = {
            ERewardRule.BASE_REWARD, ERewardRule.CONTINUOUS_REWARD,
            ERewardRule.CYCLE_REWARD, ERewardRule.YEAR_REWARD,
            ERewardRule.MONTH_REWARD, ERewardRule.WEEK_REWARD,
            ERewardRule.DATE_TIME_REWARD, ERewardRule.CUMULATIVE_REWARD,
            ERewardRule.RANDOM_REWARD, ERewardRule.CDK_REWARD,
            ERewardRule.PERSONAL_DATE_REWARD, ERewardRule.LOTTERY_REWARD
    };

    private final EditCommandHandler editHandler = new EditCommandHandler(this);

    private Text tips;

    /**
     * 左侧边栏标题高度
     */
    private int leftBarTitleHeight;
    /**
     * 左侧边栏宽度
     */
    private int leftBarWidth;
    private double ruleListOffset;
    private double ruleListContentHeight;
    private double ruleListViewportHeight;
    /**
     * 右侧边栏宽度
     */
    private final int rightBarWidth = 20;

    // region 奖励列表相关参数
    // 物品图标的大小
    private final int itemIconSize = 16;
    private final int itemRightMargin = 4;
    private final int itemBottomMargin = 8;
    private final int rewardWheelStep = itemIconSize + itemBottomMargin;
    private final int groupContentIndent = 8;
    private final int groupContentPadding = 4;
    private final int groupGap = 4;
    private final int groupHeaderVerticalPadding = 2;
    // 屏幕边缘间距
    private final int leftMargin = 4;
    private final int rightMargin = 4;
    private final int topMargin = 4;
    private final int bottomMargin = 4;
    /**
     * 每行可放物品的数量
     */
    private int lineItemCount;
    // 矩阵栈
    private PoseStack ms;
    private double rewardLayoutY;
    private double rewardContentHeight;
    // Y坐标偏移
    private double yOffset, yOffsetOld, yOffsetResetTime;
    // endregion 奖励列表相关参数

    /**
     * 当前选中的操作按钮
     */
    private int currOpButton;
    /**
     * 当前选中的奖励按钮
     */
    private String currRewardButton;
    private final RewardSelectionModel rewardSelection = new RewardSelectionModel();
    private final Set<String> collapsedRewardGroups = new HashSet<>();
    private final Map<String, RewardGroupLayout> rewardGroupLayouts = new LinkedHashMap<>();
    private final Map<String, String> rewardGroupTitles = new LinkedHashMap<>();
    private String draggingRewardId;
    private String dragTargetGroupKey;
    private double dragMouseX;
    private double dragMouseY;
    private int heldNavigationKey = -1;
    private long navigationHeldSince;
    private long nextNavigationRepeatAt;
    /**
     * 弹出菜单会在回调前清空，因此由界面保存本次菜单的业务上下文。
     */
    private String popupContextId;
    /**
     * 操作按钮集合
     */
    private final Map<Integer, RewardOperationWidget> OP_BUTTONS = new LinkedHashMap<>();

    /**
     * 奖励列表按钮集合
     */
    private final Map<String, RewardListEntryWidget> REWARD_BUTTONS = new LinkedHashMap<>();

    /**
     * 操作按钮类型
     */
    @Getter
    enum OperationButtonType implements IEnumDescribable {
        REWARD_PANEL(-1),
        OPEN(1),
        CLOSE(2),
        BASE_REWARD(201),
        CONTINUOUS_REWARD(202),
        CYCLE_REWARD(203),
        YEAR_REWARD(204),
        MONTH_REWARD(205),
        WEEK_REWARD(206),
        DATE_TIME_REWARD(207),
        CUMULATIVE_REWARD(208),
        RANDOM_REWARD(209),
        CDK_REWARD(210),
        PERSONAL_DATE_REWARD(211),
        LOTTERY_REWARD(212),
        OFFSET_Y(301),
        HELP(302),
        DOWNLOAD(303),
        UPLOAD(304),
        FOLDER(305),
        SORT(306),
        MERGE(307);

        final int code;

        OperationButtonType(int code) {
            this.code = code;
        }

        @Override
        public Component enumDescription() {
            return SakuraComponent.get().literal(name());
        }

        static OperationButtonType valueOf(int code) {
            return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
        }
    }

    /**
     * 绘制背景纹理
     */
    private void renderBackgroundTexture(PoseStack matrixStack) {
        // 启用混合模式以支持透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 绑定背景纹理
        RenderSystem.setShaderTexture(0, SakuraClientState.getThemeTexture());

        // 获取屏幕宽高
        int screenWidth = super.width;
        int screenHeight = super.height;

        // 获取纹理指定区域的坐标和大小
        float u0 = (float) SakuraClientState.getThemeTextureCoordinate().getOptionBgUV().getU0();
        float v0 = (float) SakuraClientState.getThemeTextureCoordinate().getOptionBgUV().getV0();
        float regionWidth = (float) SakuraClientState.getThemeTextureCoordinate().getOptionBgUV().getUWidth();
        float regionHeight = (float) SakuraClientState.getThemeTextureCoordinate().getOptionBgUV().getVHeight();
        if (regionWidth == 0) regionWidth = screenWidth;
        if (regionHeight == 0) regionHeight = screenHeight;
        int textureTotalWidth = SakuraClientState.getThemeTextureCoordinate().getTotalWidth();
        int textureTotalHeight = SakuraClientState.getThemeTextureCoordinate().getTotalHeight();

        int tileWidth = Math.max(1, (int) regionWidth);
        int tileHeight = Math.max(1, (int) regionHeight);
        for (int x = 0; x < screenWidth; x += tileWidth) {
            for (int y = 0; y < screenHeight; y += tileHeight) {
                int drawWidth = Math.min(tileWidth, screenWidth - x);
                int drawHeight = Math.min(tileHeight, screenHeight - y);
                AbstractGuiUtils.blit(matrixStack, SakuraClientState.getThemeTexture(),
                        x, y, drawWidth, drawHeight,
                        u0, v0, drawWidth, drawHeight,
                        textureTotalWidth, textureTotalHeight);
            }
        }

        // 禁用混合模式
        RenderSystem.disableBlend();
    }

    /**
     * 添加奖励标题按钮渲染方法
     *
     * @param title      标题
     * @param key        奖励的键
     * @param titleIndex 标题的索引
     */
    private void addRewardTitleButton(String title, String key, int titleIndex) {
        if (!rewardGroupLayouts.isEmpty()) {
            rewardLayoutY += groupGap;
        }
        int headerHeight = groupHeaderHeight();
        RewardGroupLayout layout = new RewardGroupLayout(key, rewardLayoutY,
                rewardLayoutY + headerHeight);
        rewardGroupLayouts.put(key, layout);
        rewardGroupTitles.put(key, title);
        RewardListEntryWidget entry = new RewardListEntryWidget(this, titleIndex, context -> {
            RewardListEntryWidget widget = context.getEntry();
            boolean collapsed = isRewardGroupCollapsed(key);
            int x = (int) widget.realX();
            int y = (int) widget.realY();
            int width = (int) widget.realWidth();
            int height = (int) widget.realHeight();
            boolean hovered = widget.isMouseInside(inputState.mouseX(), inputState.mouseY());
            int headerColor = hovered
                    ? getEffectiveTheme().buttonBgHover()
                    : getEffectiveTheme().panelBg();
            int panelHeight = (int) Math.max(height, layout.bottomY() - layout.topY());
            AbstractGuiUtils.fill(context.getStack(), x, y, width, height, headerColor);
            String titleId = rewardGroupTitleId(key);
            int borderColor = rewardSelection.isSelected(titleId)
                    ? groupSelectionColor()
                    : key.equals(dragTargetGroupKey)
                    ? getEffectiveTheme().accentFocused()
                    : groupBorderColor();
            drawRewardGroupBorder(context.getStack(), x, y, width, panelHeight, borderColor);
            context.getGraphics().drawString(super.font, collapsed ? "\u25B6" : "\u25BC",
                    x + 4, (int) (y + (height - super.font.lineHeight) / 2.0F),
                    getEffectiveTheme().buttonText(), false);
            drawLimitedText(context.getStack(), title,
                    x + 16, y + (height - super.font.lineHeight) / 2,
                    width - 20, getEffectiveTheme().buttonText(), false);
        }).setBaseX(leftBarWidth)
                .setDrawSelectionOutline(false)
                .setTooltip(Text.literal(RewardRuleTooltipFormatter.describe(
                        currentRewardRule(), key, title)))
                .setTooltipRequiresShift(true);
        entry.bounds()
                .x(leftMargin)
                .y(rewardLayoutY)
                .width(super.width - leftBarWidth - leftMargin - rightMargin - rightBarWidth)
                .height(headerHeight);
        registerRewardEntry(rewardGroupTitleId(key), entry);
        rewardLayoutY += headerHeight;
    }

    /**
     * 添加奖励图标按钮渲染方法
     *
     * @param rewardMap 奖励列表
     * @param key       奖励列表的key
     */
    private void addRewardButton(Map<String, RewardList> rewardMap, String key) {
        RewardGroupLayout layout = rewardGroupLayouts.get(key);
        if (isRewardGroupCollapsed(key)) {
            layout.bottomY(rewardLayoutY);
            return;
        }
        RewardList rewards = rewardMap.get(key);
        if (rewards == null || rewards.isEmpty()) {
            layout.bottomY(rewardLayoutY);
            return;
        }
        rewardLayoutY += groupContentPadding;
        for (int j = 0; j < rewards.size(); j++) {
            RewardListEntryWidget entry = new RewardListEntryWidget(this, j, context -> {
                RewardListEntryWidget widget = context.getEntry();
                Reward reward = rewardMap.get(key).get(widget.getOperation());
                RewardRenderer.renderCustomReward(context.getGraphics(), super.font,
                        SakuraClientState.getThemeTexture(), SakuraClientState.getThemeTextureCoordinate(),
                        reward, (int) widget.realX(), (int) widget.realY(), true);
            }).setBaseX(leftBarWidth)
                    .setTooltip(rewardItemTooltip(rewardMap.get(key).get(j)));
            entry.bounds()
                    .x(leftMargin + groupContentIndent
                            + (j % lineItemCount) * (itemIconSize + itemRightMargin))
                    .y(rewardLayoutY + (itemIconSize + itemBottomMargin)
                            * Math.floor((double) j / lineItemCount))
                    .width(itemIconSize)
                    .height(itemIconSize);
            registerRewardEntry(String.format("%s,%s", key, j), entry);
        }
        int rowCount = (rewards.size() + lineItemCount - 1) / lineItemCount;
        rewardLayoutY += rowCount * (itemIconSize + itemBottomMargin);
        layout.bottomY(rewardLayoutY);
    }

    /** 奖励悬浮提示只描述奖励本身，操作说明集中在首屏帮助。 */
    private Text rewardItemTooltip(Reward reward) {
        return Text.from(SakuraRewardClient.displayName(
                reward, SakuraLang.getClientLanguage(), true).clone());
    }

    private boolean isRewardGroupCollapsed(String key) {
        return collapsedRewardGroups.contains(currOpButton + ":" + key);
    }

    private int groupSelectionColor() {
        return 0xFFFFD54F;
    }

    private int groupBorderColor() {
        return (getEffectiveTheme().buttonBorderHover() & 0x00FFFFFF) | 0xE0000000;
    }

    private int groupHeaderHeight() {
        return font.lineHeight + groupHeaderVerticalPadding * 2;
    }

    private void drawRewardGroupBorder(PoseStack stack, int x, int y,
                                       int width, int height, int color) {
        BaseShapeWidget.drawShape(new ShapeDrawArgs()
                .stack(stack)
                .type(ShapeDrawArgs.ShapeType.RECT)
                .color(color)
                .rect(new ShapeDrawArgs.RectParams()
                        .x(x).y(y).width(width).height(height)
                        .radius(0).border(1)));
    }

    private void drawLimitedText(PoseStack stack, String content, int x, int y,
                                 int maxWidth, int color, boolean shadow) {
        LabelWidget.drawLimitedText(FontDrawArgs.of(Text.literal(content)
                        .stack(stack).font(font).color(color).shadow(shadow))
                .x(x).y(y).maxWidth(maxWidth).maxLine(1).wrap(false)
                .position(EnumEllipsisPosition.END).inScreen(false)
                .padding(0).margin(0));
    }

    private int multilineTextWidth(Text text) {
        return Arrays.stream(text.content().split("\\n", -1))
                .mapToInt(font::width).max().orElse(0);
    }

    private int multilineTextHeight(Text text) {
        return text.content().split("\\n", -1).length * font.lineHeight;
    }

    private void drawWelcomeTips(PoseStack stack) {
        int availableWidth = Math.max(1,
                width - leftBarWidth - rightBarWidth - 16);
        int textWidth = Math.min(multilineTextWidth(tips), availableWidth);
        int textHeight = multilineTextHeight(tips);
        int x = leftBarWidth
                + (width - leftBarWidth - rightBarWidth - textWidth) / 2;
        int y = (height - textHeight) / 2;
        LabelWidget.drawLimitedText(FontDrawArgs.of(tips.clone()
                        .stack(stack).font(font))
                .x(x).y(y).maxWidth(textWidth).maxLine(0).wrap(false)
                .position(EnumEllipsisPosition.END).inScreen(false)
                .padding(0).margin(0));
    }

    private void toggleRewardGroup(String key) {
        String groupId = currOpButton + ":" + key;
        if (!collapsedRewardGroups.remove(groupId)) {
            collapsedRewardGroups.add(groupId);
        }
    }

    private String rewardGroupTitleId(String key) {
        return "标题," + key;
    }

    private String rewardGroupKey(String rewardId) {
        int separator = rewardId == null ? -1 : rewardId.lastIndexOf(',');
        return separator > 0 ? rewardId.substring(0, separator) : null;
    }

    private List<String> rewardIdsForGroup(String groupKey) {
        ERewardRule rule = currentRewardRule();
        return rule == null ? new ArrayList<>() : RewardSelectionIds.rewardIdsForGroup(
                RewardConfigManager.getRewardMap(rule), groupKey);
    }

    private void toggleRewardGroupSelection(String groupKey) {
        String groupId = rewardGroupTitleId(groupKey);
        rewardSelection.toggleGroup(groupId, rewardIdsForGroup(groupKey));
        currRewardButton = rewardSelection.primary();
    }

    private void selectGroupRewards(String groupKey) {
        rewardSelection.selectOnly(rewardIdsForGroup(groupKey));
        currRewardButton = rewardSelection.primary();
    }

    private void selectSemanticRewards(String rewardId) {
        ERewardRule rule = currentRewardRule();
        if (rule == null) {
            return;
        }
        Reward selected = rewardForId(rule, rewardId);
        if (selected == null) {
            return;
        }
        List<String> matches = new ArrayList<>();
        Map<String, RewardList> rewardMap = RewardConfigManager.getRewardMap(rule);
        for (String id : RewardSelectionIds.rewardIds(rewardMap)) {
            if (RewardSemanticMatcher.matches(selected, rewardForId(rule, id))) {
                matches.add(id);
            }
        }
        rewardSelection.selectOnly(matches);
        currRewardButton = rewardSelection.primary();
    }

    private ERewardRule currentRewardRule() {
        OperationButtonType button = OperationButtonType.valueOf(currOpButton);
        return button == null ? null : ERewardRule.valueOf(button.toString());
    }

    private Reward rewardForId(ERewardRule rule, String rewardId) {
        String groupKey = rewardGroupKey(rewardId);
        int separator = rewardId == null ? -1 : rewardId.lastIndexOf(',');
        int index = separator < 0 ? -1
                : NumberUtils.toInt(rewardId.substring(separator + 1), -1);
        RewardList rewards = groupKey == null ? null
                : RewardConfigManager.getRewardMap(rule).get(groupKey);
        return rewards == null || index < 0 || index >= rewards.size()
                ? null : rewards.get(index);
    }

    private String rewardGroupDisplayName(String key) {
        return rewardGroupTitles.getOrDefault(key, key);
    }

    private void registerRewardEntry(String key, RewardListEntryWidget entry) {
        entry.setDragHandler(event -> scrollRewardPanel(event.dragY()));
        if (!key.startsWith("标题,")) {
            entry.setLongPressHandler(event -> beginRewardDrag(key))
                    .setLongPressDragHandler(event -> updateRewardDrag(
                            event.mouseX(), event.mouseY()))
                    .setLongPressReleaseHandler(event -> finishRewardDrag(
                            event.mouseX(), event.mouseY()));
        }
        entry.setReleaseHandler(event -> {
            AtomicBoolean updateLayout = new AtomicBoolean(false);
            AtomicBoolean handled = new AtomicBoolean(false);
            handleRewardOption(event, key, entry, updateLayout, handled);
            if (updateLayout.get()) {
                updateLayout();
            }
        });
        REWARD_BUTTONS.put(key, entry);
        addWidget(entry);
    }

    private void beginRewardDrag(String rewardId) {
        if (!rewardSelection.isSelected(rewardId)) {
            rewardSelection.selectOnly(rewardId);
            currRewardButton = rewardId;
        }
        draggingRewardId = rewardId;
        updateRewardDrag(inputState.mouseX(), inputState.mouseY());
    }

    private void updateRewardDrag(double mouseX, double mouseY) {
        if (draggingRewardId == null) {
            return;
        }
        dragMouseX = mouseX;
        dragMouseY = mouseY;
        dragTargetGroupKey = findRewardGroupAt(mouseX, mouseY);
    }

    private void finishRewardDrag(double mouseX, double mouseY) {
        if (draggingRewardId == null) {
            return;
        }
        updateRewardDrag(mouseX, mouseY);
        String targetKey = dragTargetGroupKey;
        draggingRewardId = null;
        dragTargetGroupKey = null;
        if (targetKey != null) {
            moveSelectedRewardsTo(targetKey);
        }
    }

    private String findRewardGroupAt(double mouseX, double mouseY) {
        double x = leftBarWidth + leftMargin;
        double width = this.width - leftBarWidth - leftMargin - rightMargin - rightBarWidth;
        if (mouseX < x || mouseX >= x + width) {
            return null;
        }
        for (RewardGroupLayout layout : rewardGroupLayouts.values()) {
            double top = yOffset + layout.topY();
            double bottom = yOffset + layout.bottomY();
            if (mouseY >= top && mouseY < bottom) {
                return layout.key();
            }
        }
        return null;
    }

    /**
     * 将当前选择中的奖励按原顺序追加到目标组，删除源项时按索引倒序处理。
     */
    private void moveSelectedRewardsTo(String targetKey) {
        OperationButtonType button = OperationButtonType.valueOf(currOpButton);
        if (button == null) {
            return;
        }
        ERewardRule rule = ERewardRule.valueOf(button.toString());
        Map<String, RewardList> rewardMap = RewardConfigManager.getRewardMap(rule);
        if (!rewardMap.containsKey(targetKey)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null
                || !player.hasPermissions(CommonConfig.get().permission().permissionEditReward())) {
            SakuraClientNotifications.warning(SakuraComponent.get().transClient(
                    "word", "no_permission_to_edit_reward"), SakuraNotificationTypes.REWARD);
            return;
        }

        List<DraggedReward> moving = new ArrayList<>();
        for (String id : selectedRewardIds()) {
            int separator = id.lastIndexOf(',');
            if (separator <= 0) {
                continue;
            }
            String sourceKey = id.substring(0, separator);
            int sourceIndex = NumberUtils.toInt(id.substring(separator + 1), -1);
            RewardList source = rewardMap.get(sourceKey);
            if (targetKey.equals(sourceKey) || source == null
                    || sourceIndex < 0 || sourceIndex >= source.size()) {
                continue;
            }
            moving.add(new DraggedReward(sourceKey, sourceIndex, source.get(sourceIndex)));
        }
        if (moving.isEmpty()) {
            return;
        }

        Map<String, List<Integer>> removals = new LinkedHashMap<>();
        for (DraggedReward reward : moving) {
            removals.computeIfAbsent(reward.sourceKey(), ignored -> new ArrayList<>())
                    .add(reward.sourceIndex());
        }
        removals.forEach((sourceKey, indexes) -> {
            indexes.sort(Collections.reverseOrder());
            for (int sourceIndex : indexes) {
                RewardConfigManager.deleteReward(rule, sourceKey, sourceIndex);
            }
        });
        for (DraggedReward reward : moving) {
            RewardConfigManager.addReward(rule, targetKey, reward.reward());
        }
        RewardConfigManager.saveRewardOption();
        rewardSelection.clear();
        updateLayout();
    }

    private StringInputScreen getRuleKeyInputScreen(Screen callbackScreen, ERewardRule rule, String[] key) {
        return new StringInputScreen(callbackScreen, Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()), Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_something"), ruleInputRegex(rule), "", input -> {
            StringList result = new StringList();
            if (CollectionUtils.isNotNullOrEmpty(input)) {
                String normalized = normalizeRuleInput(rule, input.get(0));
                if (RewardConfigManager.validateKeyName(rule, normalized)) {
                    key[0] = normalized;
                } else {
                    result.add(SakuraComponent.get().transClient("format", "reward_rule_s_error", input.get(0)).toString());
                }
            }
            return result;
        });
    }

    private StringInputScreen getCdkRuleKeyInputScreen(Screen callbackScreen, ERewardRule rule, String[] key) {
        return new StringInputScreen(callbackScreen
                , new TextList(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_reward_rule_key_" + rule.getCode())
                , Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_valid_until")
                , Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_num"))
                , new TextList(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_something"))
                , new StringList("\\w*", "", "\\d*")
                , new StringList("", DateUtils.toString(DateUtils.addMonth(SakuraClock.clientNow(), 1)), "1")
                , input -> {
            StringList result = new StringList("", "", "");
            if (CollectionUtils.isNotNullOrEmpty(input)) {
                if (!RewardConfigManager.validateKeyName(rule, input.get(0))) {
                    result.set(0, SakuraComponent.get().transClient("format", "reward_rule_s_error", input.get(0)).toString());
                }
                if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                    if (DateUtils.format(input.get(1)) == null) {
                        result.set(1, SakuraComponent.get().transClient("format", "valid_until_s_error", input.get(1)).toString());
                    }
                }
                if (StringUtils.isNotNullOrEmpty(input.get(2))) {
                    if (NumberUtils.toInt(input.get(2)) == 0) {
                        result.set(2, SakuraComponent.get().transClient("format", "num_s_error", input.get(2)).toString());
                    }
                }
                if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                    key[0] = String.format("%s|%s|-1|%d", input.get(0), input.get(1), NumberUtils.toInt(input.get(2), 1));
                }
            }
            return result;
        });
    }

    /**
     * 更新奖励列表渲染方法集合
     */
    private void updateRewardList() {
        RewardConfigManager.setRewardOptionDataChanged(false);
        REWARD_BUTTONS.values().forEach(this::removeWidget);
        REWARD_BUTTONS.clear();
        rewardGroupLayouts.clear();
        rewardGroupTitles.clear();
        rewardLayoutY = topMargin;
        if (OperationButtonType.valueOf(currOpButton) == null) {
            rewardContentHeight = super.height;
            return;
        }
        RewardConfig rewardConfig = RewardConfigManager.getRewardConfig();
        int titleIndex = -1;
        switch (OperationButtonType.valueOf(currOpButton)) {
            case BASE_REWARD: {
                this.addRewardTitleButton(SakuraComponent.get().transClient(
                        "word", "base_reward").toString(), "base", titleIndex);
                this.addRewardButton(Collections.singletonMap(
                        "base", rewardConfig.getBaseRewards()), "base");
            }
            break;
            case CONTINUOUS_REWARD: {
                for (String key : rewardConfig.getContinuousRewards().keySet()) {
                    this.addRewardTitleButton(SakuraComponent.get().transClient(
                            "format", "day_s", key).toString(), key, titleIndex);
                    this.addRewardButton(rewardConfig.getContinuousRewards(), key);
                    titleIndex--;
                }
            }
            break;
            case CYCLE_REWARD: {
                for (String key : rewardConfig.getCycleRewards().keySet()) {
                    this.addRewardTitleButton(SakuraComponent.get().transClient(
                            "format", "day_s", key).toString(), key, titleIndex);
                    this.addRewardButton(rewardConfig.getCycleRewards(), key);
                    titleIndex--;
                }
            }
            break;
            case YEAR_REWARD: {
                for (String key : rewardConfig.getYearRewards().keySet()) {
                    this.addRewardTitleButton(SakuraComponent.get().transClient(
                            "format", "year_day_s", key).toString(), key, titleIndex);
                    this.addRewardButton(rewardConfig.getYearRewards(), key);
                    titleIndex--;
                }
            }
            break;
            case MONTH_REWARD: {
                for (String key : rewardConfig.getMonthRewards().keySet()) {
                    this.addRewardTitleButton(SakuraComponent.get().transClient(
                            "format", "month_day_s", key).toString(), key, titleIndex);
                    this.addRewardButton(rewardConfig.getMonthRewards(), key);
                    titleIndex--;
                }
            }
            break;
            case WEEK_REWARD: {
                for (String key : rewardConfig.getWeekRewards().keySet()) {
                    this.addRewardTitleButton(SakuraComponent.get().transClient(
                            "word", "week_" + key).toString(), key, titleIndex);
                    this.addRewardButton(rewardConfig.getWeekRewards(), key);
                    titleIndex--;
                }
            }
            break;
            case DATE_TIME_REWARD: {
                for (String key : rewardConfig.getDateTimeRewards().keySet()) {
                    this.addRewardTitleButton(key, key, titleIndex);
                    this.addRewardButton(rewardConfig.getDateTimeRewards(), key);
                    titleIndex--;
                }
            }
            break;
            case CUMULATIVE_REWARD: {
                for (String key : rewardConfig.getCumulativeRewards().keySet()) {
                    this.addRewardTitleButton(SakuraComponent.get().transClient(
                            "format", "day_s", key).toString(), key, titleIndex);
                    this.addRewardButton(rewardConfig.getCumulativeRewards(), key);
                    titleIndex--;
                }
            }
            break;
            case RANDOM_REWARD: {
                Map<String, RewardList> randomRewards =
                        RewardConfigManager.getRewardMap(ERewardRule.RANDOM_REWARD);
                for (String key : randomRewards.keySet()) {
                    String probability = RewardConfigManager.getDisplayKey(
                            ERewardRule.RANDOM_REWARD, key);
                    this.addRewardTitleButton(String.format("%s%%",
                            NumberUtils.toFixedEx(new BigDecimal(probability)
                                    .multiply(new BigDecimal(100)), 10)), key, titleIndex);
                    this.addRewardButton(randomRewards, key);
                    titleIndex--;
                }
            }
            break;
            case CDK_REWARD: {
                for (int i = 0; i < rewardConfig.getCdkRewards().size(); i++) {
                    KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> keyValue = rewardConfig.getCdkRewards().get(i);
                    String key = String.format("%s|%s|%d|%d", keyValue.key().key(), keyValue.key().value(), i, keyValue.value().value().get());
                    this.addRewardTitleButton(SakuraComponent.get().transClient(
                            "format", "s_valid_until_s", keyValue.key().key(),
                            keyValue.key().value(), keyValue.value().value()
                    ).toString(), key, titleIndex);
                    this.addRewardButton(Collections.singletonMap(
                            key, keyValue.value().key()), key);
                    titleIndex--;
                }
            }
            break;
            case PERSONAL_DATE_REWARD: {
                Map<String, RewardList> personalRewards = RewardConfigManager.getRewardMap(
                        ERewardRule.PERSONAL_DATE_REWARD);
                for (PersonalDatePreset preset : rewardConfig.getPersonalDatePresets()) {
                    this.addRewardTitleButton(preset.getDisplayName(), preset.getId(), titleIndex);
                    this.addRewardButton(personalRewards, preset.getId());
                    titleIndex--;
                }
            }
            break;
            case LOTTERY_REWARD: {
                Map<String, RewardList> lotteryRewards = RewardConfigManager.getRewardMap(
                        ERewardRule.LOTTERY_REWARD);
                for (LotteryPool pool : rewardConfig.getLotteryPools()) {
                    this.addRewardTitleButton(pool.getDisplayName(), pool.getId(), titleIndex);
                    this.addRewardButton(lotteryRewards, pool.getId());
                    titleIndex--;
                }
            }
            break;
        }
        rewardContentHeight = rewardLayoutY + bottomMargin;
        setYOffset(yOffset);
        ERewardRule rule = currentRewardRule();
        Map<String, RewardList> rewardMap = rule == null
                ? Collections.emptyMap() : RewardConfigManager.getRewardMap(rule);
        rewardSelection.retainAll(RewardSelectionIds.selectionIds(rewardMap, "标题,"));
        currRewardButton = rewardSelection.primary();
    }

    /**
     * 渲染奖励列表
     */
    private void prepareRewardList() {
        if (REWARD_BUTTONS.isEmpty()) return;

        int selectedColor = SakuraClientState.getThemeTextureCoordinate().getTextColorCanRepair();
        for (Map.Entry<String, RewardListEntryWidget> item : REWARD_BUTTONS.entrySet()) {
            item.getValue()
                    .setBaseY(yOffset)
                    .setViewportHeight(super.height)
                    .setSelected(rewardSelection.isSelected(item.getKey()))
                    .setSelectedColor((selectedColor & 0x00FFFFFF) | 0xCC000000);
        }
    }

    private final Consumer<PopupOption> pasteConsumer = option -> {
        String paste = SakuraComponent.get().transClient("word", "paste").toString();
        if ("paste".equals(option.getSelectedId())) {
            option.getRenderList().stream()
                    .filter(item -> paste.equalsIgnoreCase(item.content()))
                    .forEach(item -> item.color(RewardClipboardManager.isClipboardValid() ? 0xFFFFFFFF : 0xFF999999));
        }
    };

    /**
     * 处理操作按钮事件
     *
     * @param event        鼠标事件
     * @param value        操作按钮
     * @param updateLayout 是否更新布局
     * @param flag         是否处理过事件
     */
    private void handleOperation(MouseEvent event, RewardOperationWidget value,
                                 AtomicBoolean updateLayout, AtomicBoolean flag) {
        double mouseX = event.mouseX();
        double mouseY = event.mouseY();
        int button = event.button();
        if (value.getOperation() == OperationButtonType.REWARD_PANEL.getCode()
                && isCurrentRuleRedacted()) {
            flag.set(true);
            return;
        }
        // 展开左侧边栏
        if (value.getOperation() == OperationButtonType.OPEN.getCode()) {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraClientState.setRewardOptionBarOpened(true);
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 关闭左侧边栏
        else if (value.getOperation() == OperationButtonType.CLOSE.getCode()) {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraClientState.setRewardOptionBarOpened(false);
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 左侧边栏奖励规则类型按钮
        else if (value.getOperation() > 200 && value.getOperation() <= 299) {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                this.currOpButton = value.getOperation();
                updateLayout.set(true);
                flag.set(true);
            } else if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                ERewardRule rewardRule = ERewardRule.valueOf(
                        OperationButtonType.valueOf(value.getOperation()).name());
                if (RewardConfigManager.isRuleRedacted(rewardRule)) {
                    flag.set(true);
                    return;
                }
                // 绘制弹出层选项
                this.popupOption.clear()
                        .addOptionWithId("clear",
                                Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.clear").color(0xFFFF0000));
                this.showPopup(mouseX, mouseY, String.format("奖励规则类型按钮:%s", value.getOperation()));
                flag.set(true);
            }
        }
        // 重置偏移量
        else if (value.getOperation() == OperationButtonType.OFFSET_Y.getCode()) {
            this.yOffsetResetTime = System.currentTimeMillis();
            this.yOffsetOld = this.yOffset;
            flag.set(true);
        }
        // 奖励配置列表面板
        else if (value.getOperation() == OperationButtonType.REWARD_PANEL.getCode()) {
            String groupKey = findRewardGroupAt(event.mouseX(), event.mouseY());
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_MIDDLE && groupKey != null) {
                toggleRewardGroupSelection(groupKey);
            } else if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT && groupKey != null) {
                if (!rewardSelection.isSelected(rewardGroupTitleId(groupKey))) {
                    rewardSelection.selectOnly(rewardGroupTitleId(groupKey));
                }
                currRewardButton = rewardGroupTitleId(groupKey);
                showRewardGroupPopup(mouseX, mouseY, groupKey);
            } else if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                if (this.currOpButton > 200 && this.currOpButton <= 299) {
                    this.popupOption.clear()
                            .addOptionWithId("paste", Text.trans(
                                    SakuraSignIn.MODID, "word.sakura_sign_in.paste"));
                    addRewardTypeOptions();
                    this.popupOption.setBeforeRender(pasteConsumer);
                    this.showPopup(mouseX, mouseY, String.format("奖励面板按钮:%s", this.currOpButton));
                    flag.set(true);
                }
            }
        }
        // 帮助按钮
        else if (value.getOperation() == OperationButtonType.HELP.getCode()) {
            List<Component> paragraphs = new ArrayList<>();
            for (int i = 1; i <= ERewardRule.values().length; i++) {
                paragraphs.add(SakuraComponent.get().transClient(
                        "word", "reward_rule_description_" + i));
            }
            Minecraft.getInstance().setScreen(new ReadOnlyTextScreen(
                    new ReadOnlyTextScreen.Args()
                            .parentScreen(this)
                            .season(season())
                            .title(SakuraComponent.get().transClient(
                                    "word", "reward_rule_help"))
                            .paragraphs(paragraphs)
            ));
            flag.set(true);
        }
        // 上传奖励配置
        else if (value.getOperation() == OperationButtonType.UPLOAD.getCode()) {
            // 仅管理员可上传
            if (!Minecraft.getInstance().isLocalServer()) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    if (player.hasPermissions(CommonConfig.get().permission().permissionEditReward())) {
                        SakuraNetwork.sendSplitToServer(RewardConfigManager.toSyncPacket(player));
                        if (!RewardConfigManager.isRuleRedacted(
                                ERewardRule.PERSONAL_DATE_REWARD)) {
                            SakuraNetwork.sendSplitToServer(new PersonalDatePresetSyncPacket(
                                    RewardConfigManager.getRewardConfig()
                                            .getPersonalDatePresets()));
                        }
                        if (!RewardConfigManager.isRuleRedacted(ERewardRule.LOTTERY_REWARD)) {
                            SakuraNetwork.sendSplitToServer(new LotteryPoolSyncPacket(
                                    RewardConfigManager.getRewardConfig().getLotteryPools()));
                        }
                        flag.set(true);
                    }
                }
            } else {
                Component component = SakuraComponent.get().trans("word", "local_server_not_support_this_operation");
                SakuraClientNotifications.warning(component, SakuraNotificationTypes.REWARD);
            }
        }
        // 下载奖励配置
        else if (value.getOperation() == OperationButtonType.DOWNLOAD.getCode()) {
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                if (!Minecraft.getInstance().isLocalServer()) {
                    // 备份签到奖励配置
                    RewardConfigManager.backupRewardOption();
                    // 同步签到奖励配置到客户端
                    SakuraNetwork.sendToServer(new DownloadRewardOptionNotice());
                    flag.set(true);
                } else {
                    Component component = SakuraComponent.get().trans("word", "local_server_not_support_this_operation");
                    SakuraClientNotifications.warning(component, SakuraNotificationTypes.REWARD);
                }
            }
        }
        // 排序
        else if (value.getOperation() == OperationButtonType.SORT.getCode()) {
            RewardConfigManager.sortRewards();
            RewardConfigManager.saveRewardOption();
            updateLayout.set(true);
            flag.set(true);
        }
        // 合并当前规则页各组内仅数量不同的相同奖励
        else if (value.getOperation() == OperationButtonType.MERGE.getCode()) {
            if (mergeCurrentRuleRewards()) {
                updateLayout.set(true);
            }
            flag.set(true);
        }
        // 打开配置文件夹
        else if (value.getOperation() == OperationButtonType.FOLDER.getCode()) {
            SystemUtils.openFileInFolder(
                    RewardConfigManager.getConfigDirectory()
                            .resolve(RewardConfigManager.FILE_NAME));
            flag.set(true);
        }
    }

    private boolean mergeCurrentRuleRewards() {
        ERewardRule rule = currentRewardRule();
        if (rule == null || isCurrentRuleRedacted()) return false;
        Map<String, RewardList> groups = RewardConfigManager.getRewardMap(rule);
        Map<String, RewardList> mergedGroups = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, RewardList> entry : groups.entrySet()) {
            RewardList merged = RewardManager.mergeRewards(entry.getValue());
            mergedGroups.put(entry.getKey(), merged);
            changed |= merged.size() != entry.getValue().size();
        }
        if (!changed) return false;
        RewardConfigManager.addUndoRewardOption(rule);
        RewardConfigManager.clearRedoList();
        RewardConfigManager.setRewardMap(RewardConfigManager.getRewardConfig(), rule, mergedGroups);
        RewardConfigManager.saveRewardOption();
        rewardSelection.clear();
        currRewardButton = null;
        return true;
    }

    /**
     * 处理奖励按钮事件
     *
     * @param event        鼠标事件
     * @param value        奖励按钮
     * @param updateLayout 是否更新布局
     * @param flag         是否处理过事件
     */
    private void handleRewardOption(MouseEvent event, String key,
                                    RewardListEntryWidget value, AtomicBoolean updateLayout,
                                    AtomicBoolean flag) {
        double mouseX = event.mouseX();
        double mouseY = event.mouseY();
        int button = event.button();
        LOGGER.debug("选择了奖励配置:\tButton: {}\tOperation: {}\tKey: {}\tIndex: {}", button, this.currOpButton, key, value.getOperation());

        boolean group = key.startsWith("标题,");
        String groupKey = group ? key.substring("标题,".length()) : rewardGroupKey(key);
        if (button == GLFWKey.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (group) {
                toggleRewardGroupSelection(groupKey);
            } else {
                rewardSelection.cycleMiddleReward(
                        key, rewardGroupTitleId(groupKey), rewardIdsForGroup(groupKey));
                currRewardButton = rewardSelection.primary();
            }
            flag.set(true);
            return;
        }

        if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            if (group) {
                if (inputState.isCtrlPressing()) {
                    rewardSelection.select(key, new ArrayList<>(REWARD_BUTTONS.keySet()),
                            true, false);
                    currRewardButton = rewardSelection.primary();
                } else {
                    toggleRewardGroup(key.substring("标题,".length()));
                    rewardSelection.clear();
                    updateLayout.set(true);
                }
                flag.set(true);
                return;
            } else if (event.clickCount() == 3) {
                selectGroupRewards(groupKey);
                flag.set(true);
                return;
            } else if (event.clickCount() == 2) {
                selectSemanticRewards(key);
                flag.set(true);
                return;
            } else {
                rewardSelection.select(key, selectableRewardIds(),
                        inputState.isCtrlPressing(), inputState.isShiftPressing());
            }
        } else if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT
                && !rewardSelection.isSelected(key)) {
            rewardSelection.selectOnly(key);
        }
        if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
            this.currRewardButton = key;
        } else {
            this.currRewardButton = rewardSelection.primary();
        }

        if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
            if (group) {
                showRewardGroupPopup(mouseX, mouseY, groupKey);
            } else {
                this.popupOption.clear();
                Reward clickedReward = rewardAt(key);
                if (clickedReward != null && SakuraRewardClient.find(clickedReward.getTypeId())
                        .map(registration -> registration.getExtension().getEditor() != null)
                        .orElse(false)) {
                    this.popupOption.addOptionWithId("edit", Text.trans(
                            SakuraSignIn.MODID, "word.sakura_sign_in.edit"));
                }
                if (clickedReward != null) {
                    this.popupOption.addOptionWithId("probability", Text.trans(
                            SakuraSignIn.MODID, "word.sakura_sign_in.edit_probability"));
                }
                this.popupOption.addOptionWithId("copy", Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.copy"))
                        .addOptionWithId("cut", Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.cut"))
                        .addOptionWithId("paste", Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.paste"))
                        .addOptionWithId("delete",
                                Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.delete").color(0xFFFF0000));
                this.showPopup(mouseX, mouseY, String.format("奖励按钮:%s", key));
            }
            this.popupOption.setBeforeRender(pasteConsumer);
            flag.set(true);
        }
    }

    private void renderDraggedReward(GuiGraphics graphics) {
        PoseStack stack = graphics.pose();
        if (draggingRewardId == null) {
            return;
        }
        OperationButtonType button = OperationButtonType.valueOf(currOpButton);
        if (button == null) {
            return;
        }
        List<String> draggingIds = selectedRewardIds();
        int columns = Math.min(5, Math.max(1, draggingIds.size()));
        int spacing = itemIconSize + 2;
        int startX = (int) dragMouseX - columns * spacing / 2;
        int startY = (int) dragMouseY - itemIconSize / 2;
        stack.pushPose();
        stack.translate(0, 0, 500);
        int visualIndex = 0;
        for (String rewardId : selectedRewardIds()) {
            int separator = rewardId.lastIndexOf(',');
            if (separator <= 0) {
                continue;
            }
            String key = rewardId.substring(0, separator);
            int index = NumberUtils.toInt(rewardId.substring(separator + 1), -1);
            RewardList rewards = RewardConfigManager.getRewardMap(
                    ERewardRule.valueOf(button.toString())).get(key);
            if (rewards == null || index < 0 || index >= rewards.size()) {
                continue;
            }
            int x = startX + visualIndex % columns * spacing;
            int y = startY + visualIndex / columns * spacing;
            RewardRenderer.renderCustomReward(graphics, font,
                    SakuraClientState.getThemeTexture(),
                    SakuraClientState.getThemeTextureCoordinate(),
                    rewards.get(index), x, y, true);
            visualIndex++;
        }
        stack.popPose();
    }

    private List<String> selectableRewardIds() {
        List<String> ids = new ArrayList<>();
        for (String id : REWARD_BUTTONS.keySet()) {
            if (!id.startsWith("标题")) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Reward rewardAt(String rewardId) {
        int separator = rewardId == null ? -1 : rewardId.lastIndexOf(',');
        OperationButtonType button = OperationButtonType.valueOf(currOpButton);
        if (separator <= 0 || button == null) {
            return null;
        }
        String key = rewardId.substring(0, separator);
        int index = NumberUtils.toInt(rewardId.substring(separator + 1), -1);
        RewardList rewards = RewardConfigManager.getRewardMap(
                ERewardRule.valueOf(button.toString())).get(key);
        return rewards != null && index >= 0 && index < rewards.size()
                ? rewards.get(index) : null;
    }

    private List<String> selectedRewardIds() {
        List<String> ids = new ArrayList<>();
        for (String id : rewardSelection.selectedIds()) {
            if (!id.startsWith("标题") && id.contains(",")) {
                ids.add(id);
            }
        }
        return ids;
    }

    private List<String> selectedGroupKeys() {
        return RewardSelectionIds.groupKeys(rewardSelection.selectedIds(), "标题,");
    }

    private void showPopup(double mouseX, double mouseY, String contextId) {
        this.popupContextId = contextId;
        this.popupOption.onSelect(this::handlePopupSelection).showAt(mouseX, mouseY, contextId);
    }

    private void showRewardGroupPopup(double mouseX, double mouseY, String groupKey) {
        String groupId = rewardGroupTitleId(groupKey);
        this.popupOption.clear();
        if (!"标题,base".equalsIgnoreCase(groupId)) {
            this.popupOption.addOptionWithId("edit", Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.edit"));
        }
        this.popupOption.addOptionWithId("copy", Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.copy"));
        if (!"标题,base".equalsIgnoreCase(groupId)) {
            this.popupOption.addOptionWithId("cut", Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.cut"));
        }
        this.popupOption.addOptionWithId("paste", Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.paste"));
        addRewardTypeOptions();
        this.popupOption.addOptionWithId("clear",
                Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.clear").color(0xFFFF0000));
        if (!"标题,base".equalsIgnoreCase(groupId)) {
            this.popupOption.addOptionWithId("delete",
                    Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.delete").color(0xFFFF0000));
        }
        this.popupOption.setBeforeRender(pasteConsumer);
        this.showPopup(mouseX, mouseY, String.format("奖励按钮:%s", groupId));
    }

    private void handlePopupSelection(PopupOption.SelectEvent event) {
        if (event.button() != GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        AtomicBoolean handled = new AtomicBoolean(false);
        this.handlePopupOption(event.button(), popupContextId, event.id(), handled);
    }

    private void addRewardTypeOptions() {
        for (SakuraRewardClient.Registration<?> registration : SakuraRewardClient.all()) {
            if (registration.getExtension().getEditor() == null) {
                continue;
            }
            Component label = registration.getExtension().getTypeName() == null
                    ? SakuraComponent.get().literal(registration.getTypeId().toString())
                    : registration.getExtension().getTypeName().get();
            this.popupOption.addOptionWithId(
                    REWARD_TYPE_OPTION_PREFIX + registration.getTypeId(),
                    Text.literal(label.toString()));
        }
    }

    private void handlePopupOption(int button, String popupId, String optionId,
                                   AtomicBoolean flag) {
        LOGGER.debug("选择了弹出选项:\tButton: {}\tId: {}\tOption: {}",
                button, popupId, optionId);
        OperationButtonType buttonType = OperationButtonType.valueOf(currOpButton);
        if (buttonType == null) {
            return;
        }
        ERewardRule rule = ERewardRule.valueOf(buttonType.toString());
        RewardTypeId rewardType = rewardTypeOption(optionId);

        if (popupId.startsWith("奖励规则类型按钮:")) {
            int opCode = NumberUtils.toInt(popupId.replace("奖励规则类型按钮:", ""));
            if ("clear".equals(optionId) && opCode > 200 && opCode <= 299) {
                requestConfirmation("confirm_clear_reward_rule",
                        () -> clearRewardRule(opCode, rule));
                flag.set(true);
            }
            return;
        }

        if (popupId.startsWith("奖励面板按钮:")) {
            if ("paste".equals(optionId)) {
                editHandler.handlePaste();
                flag.set(true);
            } else if (rewardType != null) {
                flag.set(openCreateReward(rule, null, rewardType));
            }
            return;
        }

        if (!popupId.startsWith("奖励按钮:")) {
            return;
        }

        String id = popupId.substring("奖励按钮:".length());
        if (id.startsWith("标题,")) {
            String key = id.substring("标题,".length());
            if ("edit".equals(optionId)) {
                editRewardGroup(rule, key);
            } else if ("copy".equals(optionId)) {
                editHandler.handleCopy();
            } else if ("cut".equals(optionId)) {
                editHandler.handleCut();
            } else if ("paste".equals(optionId)) {
                editHandler.handlePaste();
            } else if ("clear".equals(optionId)) {
                requestGroupConfirmation("confirm_clear_reward_group", key, () -> {
                    RewardConfigManager.addUndoRewardOption(rule);
                    RewardConfigManager.clearRedoList();
                    RewardConfigManager.clearKey(rule, key);
                    RewardConfigManager.saveRewardOption();
                    updateLayout();
                });
            } else if ("delete".equals(optionId)) {
                requestDeleteConfirmation();
            } else if (rewardType != null) {
                openCreateReward(rule, key, rewardType);
            } else {
                return;
            }
            flag.set(true);
            return;
        }

        int separator = id.lastIndexOf(',');
        if (separator <= 0 || separator >= id.length() - 1) {
            LOGGER.error("Invalid reward popup id: {}", id);
            return;
        }
        String key = id.substring(0, separator);
        int index = NumberUtils.toInt(id.substring(separator + 1), -1);
        if (index < 0) {
            return;
        }

        if ("edit".equals(optionId)) {
            editReward(rule, key, index);
        } else if ("probability".equals(optionId)) {
            editRewardProbability(rule, key, index);
        } else if ("copy".equals(optionId)) {
            editHandler.handleCopy();
        } else if ("cut".equals(optionId)) {
            editHandler.handleCut();
        } else if ("paste".equals(optionId)) {
            editHandler.handlePaste();
        } else if ("delete".equals(optionId)) {
            requestDeleteConfirmation();
        } else {
            return;
        }
        flag.set(true);
    }

    private RewardTypeId rewardTypeOption(String optionId) {
        if (optionId == null || !optionId.startsWith(REWARD_TYPE_OPTION_PREFIX)) {
            return null;
        }
        try {
            return RewardTypeId.parse(optionId.substring(REWARD_TYPE_OPTION_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid reward type option: {}", optionId);
            return null;
        }
    }

    private boolean openCreateReward(ERewardRule rule, String groupKey,
                                     RewardTypeId rewardType) {
        Consumer<Reward> save = reward -> {
            RewardConfigManager.addUndoRewardOption(rule);
            RewardConfigManager.clearRedoList();
            RewardConfigManager.addReward(rule, groupKeyForSave(rule, groupKey), reward);
            RewardConfigManager.saveRewardOption();
            updateLayout();
        };
        if (groupKey != null || rule == ERewardRule.BASE_REWARD) {
            return RewardEditorCoordinator.openCreate(this, rewardType, save);
        }

        String[] createdKey = new String[]{""};
        Screen transition = RewardEditorCoordinator.deferred(
                this,
                () -> RewardEditorCoordinator.openCreate(this, rewardType, reward -> {
                    RewardConfigManager.addUndoRewardOption(rule);
                    RewardConfigManager.clearRedoList();
                    RewardConfigManager.addReward(rule, createdKey[0], reward);
                    RewardConfigManager.saveRewardOption();
                    updateLayout();
                }),
                () -> StringUtils.isNotNullOrEmpty(createdKey[0])
        );
        Screen keyScreen = rule == ERewardRule.CDK_REWARD
                ? getCdkRuleKeyInputScreen(transition, rule, createdKey)
                : rule == ERewardRule.PERSONAL_DATE_REWARD
                ? getPersonalDatePresetInputScreen(transition, null, createdKey)
                : rule == ERewardRule.LOTTERY_REWARD
                ? getLotteryPoolInputScreen(transition, null, createdKey)
                : getRuleKeyInputScreen(transition, rule, createdKey);
        Minecraft.getInstance().setScreen(keyScreen);
        return true;
    }

    private String groupKeyForSave(ERewardRule rule, String groupKey) {
        return groupKey == null && rule == ERewardRule.BASE_REWARD ? "base" : groupKey;
    }

    private boolean editReward(ERewardRule rule, String key, int index) {
        Reward reward = RewardConfigManager.getReward(rule, key, index);
        return RewardEditorCoordinator.openEdit(this, reward, updated -> {
            RewardConfigManager.addUndoRewardOption(rule);
            RewardConfigManager.clearRedoList();
            RewardConfigManager.updateReward(rule, key, index, updated);
            RewardConfigManager.saveRewardOption();
            updateLayout();
        });
    }

    private void editRewardGroup(ERewardRule rule, String key) {
        if (rule == ERewardRule.PERSONAL_DATE_REWARD) {
            PersonalDatePreset preset = RewardConfigManager.getRewardConfig()
                    .getPersonalDatePresets().stream()
                    .filter(value -> key.equals(value.getId())).findFirst().orElse(null);
            if (preset != null) {
                Minecraft.getInstance().setScreen(
                        getPersonalDatePresetInputScreen(this, preset, new String[]{key}));
            }
            return;
        }
        if (rule == ERewardRule.LOTTERY_REWARD) {
            LotteryPool pool = RewardConfigManager.lotteryPool(key);
            if (pool != null) {
                Minecraft.getInstance().setScreen(
                        getLotteryPoolInputScreen(this, pool, new String[]{key}));
            }
            return;
        }
        if (rule == ERewardRule.CDK_REWARD) {
            String[] split = key.split("\\|");
            if (split.length != 4 && split.length != 3 && split.length != 2) {
                split = new String[]{"", DateUtils.toString(
                        DateUtils.addMonth(SakuraClock.clientNow(), 1)), "-1", "1"};
            }
            Minecraft.getInstance().setScreen(new StringInputScreen(this,
                    new TextList(
                            Text.trans(SakuraSignIn.MODID,
                                    "word.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()),
                            Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_valid_until"),
                            Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_num")),
                    new TextList(Text.trans(SakuraSignIn.MODID,
                            "word.sakura_sign_in.enter_something")),
                    new StringList("\\w*", "", "\\d*"),
                    new StringList(split[0], split[1], split[3]),
                    input -> {
                        StringList result = new StringList("", "", "");
                        if (!RewardConfigManager.validateKeyName(rule, input.get(0))) {
                            result.set(0, SakuraComponent.get().transClient(
                                    "format", "reward_rule_s_error", input.get(0)).toString());
                        }
                        if (StringUtils.isNotNullOrEmpty(input.get(1))
                                && DateUtils.format(input.get(1)) == null) {
                            result.set(1, SakuraComponent.get().transClient(
                                    "format", "valid_until_s_error", input.get(1)).toString());
                        }
                        if (StringUtils.isNotNullOrEmpty(input.get(2))
                                && NumberUtils.toInt(input.get(2)) == 0) {
                            result.set(2, SakuraComponent.get().transClient(
                                    "format", "num_s_error", input.get(2)).toString());
                        }
                        if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.updateKeyName(rule, key,
                                    String.format("%s|%s|-1|%d", input.get(0), input.get(1),
                                            NumberUtils.toInt(input.get(2), 1)));
                            RewardConfigManager.saveRewardOption();
                        }
                        return result;
                    }));
            return;
        }

        Minecraft.getInstance().setScreen(new StringInputScreen(
                this,
                Text.trans(SakuraSignIn.MODID,
                        "word.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()),
                Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_something"),
                ruleInputRegex(rule),
                displayRuleInput(rule, key),
                input -> {
                    StringList result = new StringList();
                    String normalized = normalizeRuleInput(rule, input.get(0));
                    if (RewardConfigManager.validateKeyName(rule, normalized)) {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        RewardConfigManager.updateKeyName(rule, key, normalized);
                        RewardConfigManager.saveRewardOption();
                    } else {
                        result.add(SakuraComponent.get().transClient(
                                "format", "reward_rule_s_error", input.get(0)).toString());
                    }
                    return result;
                }));
    }

    private Screen getPersonalDatePresetInputScreen(
            Screen callbackScreen, PersonalDatePreset existing, String[] createdKey) {
        return xin.vanilla.sakura.client.gui.PersonalDatePresetForm.create(
                callbackScreen, existing, candidate -> {
                    RewardConfigManager.addUndoRewardOption(ERewardRule.PERSONAL_DATE_REWARD);
                    if (existing == null) {
                        RewardConfigManager.getRewardConfig().getPersonalDatePresets().add(candidate);
                    } else {
                        int index = RewardConfigManager.getRewardConfig()
                                .getPersonalDatePresets().indexOf(existing);
                        RewardConfigManager.getRewardConfig().getPersonalDatePresets()
                                .set(index, candidate);
                    }
                    createdKey[0] = candidate.getId();
                    RewardConfigManager.saveRewardOption();
                    updateLayout();
                });
    }

    private Screen getLotteryPoolInputScreen(
            Screen callbackScreen, LotteryPool existing, String[] createdKey) {
        return xin.vanilla.sakura.client.gui.LotteryPoolForm.create(
                callbackScreen, existing, candidate -> {
                    RewardConfigManager.addUndoRewardOption(ERewardRule.LOTTERY_REWARD);
                    RewardConfigManager.clearRedoList();
                    if (existing == null) {
                        RewardConfigManager.getRewardConfig().getLotteryPools().add(candidate);
                    } else {
                        int index = RewardConfigManager.getRewardConfig()
                                .getLotteryPools().indexOf(existing);
                        RewardConfigManager.getRewardConfig().getLotteryPools()
                                .set(index, candidate);
                    }
                    createdKey[0] = candidate.getId();
                    RewardConfigManager.saveRewardOption();
                    updateLayout();
                });
    }

    private boolean editRewardProbability(ERewardRule rule, String key, int index) {
        Reward reward = RewardConfigManager.getReward(rule, key, index).clone();
        return RewardEditorCoordinator.openProbability(this, reward, updated -> {
            RewardConfigManager.addUndoRewardOption(rule);
            RewardConfigManager.clearRedoList();
            RewardConfigManager.updateReward(rule, key, index, updated);
            RewardConfigManager.saveRewardOption();
            updateLayout();
        });
    }

    /** 奖励组与整个规则的破坏性操作使用可见确认页。 */
    private void requestConfirmation(String messageKey, Runnable action) {
        requestConfirmation(SakuraComponent.get().transClient("word", messageKey), action);
    }

    private void requestConfirmation(Component message, Runnable action) {
        Minecraft.getInstance().setScreen(new ConfirmDialogScreen(
                new ConfirmDialogScreen.Args()
                        .parentScreen(this)
                        .title(SakuraComponent.get().transClient("word", "confirm_operation"))
                        .message(message)
                        .onConfirm(action)
        ));
    }

    private void requestGroupConfirmation(String messageKey, String key, Runnable action) {
        requestConfirmation(SakuraComponent.get().transClient(
                "format", messageKey, rewardGroupDisplayName(key)), action);
    }

    private boolean requestDeleteConfirmation() {
        if (StringUtils.isNullOrEmptyEx(currRewardButton)
                || currRewardButton.equalsIgnoreCase("panel")) {
            return false;
        }
        if (!currRewardButton.startsWith("标题")) {
            return editHandler.handleDelete();
        }
        String key = currRewardButton.substring("标题,".length());
        requestGroupConfirmation("confirm_delete_reward_group", key,
                editHandler::handleDelete);
        return true;
    }

    private void clearRewardRule(int opCode, ERewardRule rule) {
        OperationButtonType operation = OperationButtonType.valueOf(opCode);
        if (operation == null) {
            return;
        }
        RewardConfigManager.addUndoRewardOption(rule);
        RewardConfigManager.clearRedoList();
        switch (operation) {
            case BASE_REWARD:
                RewardConfigManager.getRewardConfig().getBaseRewards().clear();
                break;
            case CONTINUOUS_REWARD:
                RewardConfigManager.getRewardConfig().getContinuousRewards().clear();
                break;
            case CYCLE_REWARD:
                RewardConfigManager.getRewardConfig().getCycleRewards().clear();
                break;
            case YEAR_REWARD:
                RewardConfigManager.getRewardConfig().getYearRewards().clear();
                break;
            case MONTH_REWARD:
                RewardConfigManager.getRewardConfig().getMonthRewards().clear();
                break;
            case WEEK_REWARD:
                RewardConfigManager.getRewardConfig().getWeekRewards().clear();
                break;
            case DATE_TIME_REWARD:
                RewardConfigManager.getRewardConfig().getDateTimeRewards().clear();
                break;
            case CUMULATIVE_REWARD:
                RewardConfigManager.getRewardConfig().getCumulativeRewards().clear();
                break;
            case RANDOM_REWARD:
                RewardConfigManager.getRewardConfig().getRandomRewardGroups().clear();
                break;
            case CDK_REWARD:
                RewardConfigManager.getRewardConfig().getCdkRewards().clear();
                break;
            default:
                return;
        }
        RewardConfigManager.saveRewardOption();
        updateLayout();
    }

    /**
     * 生成操作按钮的自定义渲染函数
     *
     * @param content 按钮内容
     */
    private Consumer<RewardOperationWidget.RenderContext> generateCustomRenderFunction(
            String content, ERewardRule rule) {
        return context -> {
            RewardOperationWidget widget = context.getWidget();
            PoseStack stack = context.getStack();
            int realX = (int) widget.realX();
            int realY = (int) widget.realY();
            double realWidth = widget.realWidth();
            double realHeight = widget.realHeight();
            int realX2 = (int) (widget.realX() + realWidth);
            int realY2 = (int) (widget.realY() + realHeight);
            boolean redacted = RewardConfigManager.isRuleRedacted(rule);
            if (this.currOpButton == widget.getOperation()) {
                AbstractGuiUtils.fill(stack, realX + 1, realY,
                        Math.max(0, realX2 - realX - 2), Math.max(0, realY2 - realY), 0x44ACACAC);
            }
            if (widget.hovered() && !redacted) {
                AbstractGuiUtils.fill(stack, realX, realY,
                        Math.max(0, realX2 - realX), Math.max(0, realY2 - realY), 0x99ACACAC);
            }
            drawLimitedText(stack,
                    SakuraComponent.get().transClient("word", content).toString(),
                    realX + 4, (int) (realY + (realHeight - super.font.lineHeight) / 2),
                    (int) (realWidth - 22), redacted ? 0xFF777777 : 0xFFEBD4B1, false);
        };
    }

    private void updateLayout() {
        this.leftBarWidth = SakuraClientState.isRewardOptionBarOpened() ? 100 : 20;
        this.lineItemCount = Math.max(1,
                (super.width - leftBarWidth - leftMargin - rightMargin
                        - rightBarWidth - groupContentIndent)
                        / (itemIconSize + itemRightMargin));
        // 重置奖励面板坐标
        OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode()).bounds(
                new ScreenCoordinate(leftBarWidth, 0,
                        super.width - leftBarWidth - rightBarWidth, super.height));
        updateRuleListLayout();
        // 清空弹出层选项
        popupOption.clear();
        // 更新奖励面板列表内容
        this.updateRewardList();
    }

    private void setYOffset(double offset) {
        this.yOffset = RewardPanelViewport.clampOffset(
                offset, rewardContentHeight, super.height);
    }

    private void updateRuleListLayout() {
        int rowStep = Math.max(1, leftBarTitleHeight - 1);
        int rowHeight = Math.max(1, leftBarTitleHeight - 2);
        ruleListContentHeight = REWARD_RULES.length * rowStep;
        ruleListViewportHeight = Math.max(0, height - leftBarTitleHeight);
        ruleListOffset = RewardRuleListViewport.clampOffset(ruleListOffset,
                ruleListContentHeight, ruleListViewportHeight);
        ScreenCoordinate clip = new ScreenCoordinate(0, leftBarTitleHeight,
                Math.max(1, leftBarWidth), Math.max(1, ruleListViewportHeight));
        for (int i = 0; i < REWARD_RULES.length; i++) {
            OperationButtonType type = OperationButtonType.valueOf(REWARD_RULES[i].name());
            RewardOperationWidget widget = OP_BUTTONS.get(type.getCode());
            if (widget != null) {
                widget.bounds(new ScreenCoordinate(0,
                        leftBarTitleHeight + ruleListOffset + rowStep * i,
                        100, rowHeight));
                widget.setClipBounds(clip);
            }
        }
    }

    private void scrollRuleList(double amount) {
        ruleListOffset = RewardRuleListViewport.clampOffset(
                ruleListOffset + amount, ruleListContentHeight,
                ruleListViewportHeight);
        updateRuleListLayout();
    }

    private void scrollRewardPanel(double amount) {
        setYOffset(yOffset + amount);
        if (draggingRewardId != null) {
            updateRewardDrag(inputState.mouseX(), inputState.mouseY());
        }
    }

    @Data
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static class RewardGroupLayout {
        private final String key;
        private final double topY;
        private double bottomY;
    }

    @Data
    @Accessors(fluent = true)
    private static class DraggedReward {
        private final String sourceKey;
        private final int sourceIndex;
        private final Reward reward;
    }

    @Data
    class EditCommandHandler {
        private final Screen screen;

        private ERewardRule rule;
        private String key;
        private String index;

        /**
         * 仅解析当前规则，撤销与重做不依赖界面选择。
         */
        private boolean updateRule() {
            OperationButtonType buttonType = OperationButtonType.valueOf(currOpButton);
            if (buttonType == null) return true;
            rule = ERewardRule.valueOf(buttonType.toString());
            return false;
        }

        /**
         * 解析当前奖励或奖励组。
         */
        private boolean updateSelection() {
            if (updateRule() || StringUtils.isNullOrEmptyEx(currRewardButton)) return true;

            key = null;
            index = null;

            if (currRewardButton.startsWith("标题")) {
                key = currRewardButton.substring(3);
            } else if (!currRewardButton.equalsIgnoreCase("panel")) {
                String[] split = currRewardButton.split(",");
                if (split.length != 2) {
                    LOGGER.error("Invalid popup option id: {}", currRewardButton);
                    return true;
                }
                key = split[0];
                index = split[1];
            }
            return false;
        }

        public boolean handleCopy() {
            if (updateSelection()) return false;

            List<String> selectedIds = selectedRewardIds();
            if (!selectedIds.isEmpty()) {
                RewardList rewards = new RewardList();
                String firstKey = key;
                for (String selectedId : selectedIds) {
                    String[] parts = selectedId.split(",", 2);
                    if (parts.length != 2) {
                        continue;
                    }
                    if (rewards.isEmpty()) {
                        firstKey = parts[0];
                    }
                    rewards.add(RewardConfigManager.getReward(
                            rule, parts[0], Integer.parseInt(parts[1])).clone());
                }
                if (!rewards.isEmpty()) {
                    RewardClipboardManager.setClipboard(rewards, firstKey);
                    return true;
                }
            }

            // 面板
            if (currRewardButton.equalsIgnoreCase("panel")) {
                return false;
            }
            // 标题
            else if (currRewardButton.startsWith("标题")) {
                RewardList rewardList = RewardConfigManager.getKeyName(rule, key).clone();
                RewardClipboardManager.setClipboard(rewardList, key);
            }
            // 普通按钮
            else {
                Reward reward = RewardConfigManager.getReward(rule, key, Integer.parseInt(index)).clone();
                RewardClipboardManager.setClipboard(reward, key);
            }
            return true;
        }

        public boolean handleCut() {
            if (updateSelection()) return false;

            if (!selectedRewardIds().isEmpty()) {
                handleCopy();
                return deleteSelectedRewards();
            }

            // 面板
            if (currRewardButton.equalsIgnoreCase("panel")) {
                return false;
            }
            // 标题
            else if (currRewardButton.startsWith("标题")) {
                RewardConfigManager.addUndoRewardOption(rule);
                RewardConfigManager.clearRedoList();
                RewardList rewardList = RewardConfigManager.getKeyName(rule, key).clone();
                RewardClipboardManager.setClipboard(rewardList, key);
                RewardConfigManager.deleteKey(rule, key);
                RewardConfigManager.saveRewardOption();
            }
            // 普通按钮
            else {
                RewardConfigManager.addUndoRewardOption(rule);
                RewardConfigManager.clearRedoList();
                Reward reward = RewardConfigManager.getReward(rule, key, Integer.parseInt(index)).clone();
                RewardClipboardManager.setClipboard(reward, key);
                RewardConfigManager.deleteReward(rule, key, Integer.parseInt(index));
                RewardConfigManager.saveRewardOption();
            }
            updateLayout();
            return true;
        }

        public boolean handlePaste() {
            return handlePaste(false);
        }

        private boolean handlePaste(boolean pasteToPanel) {
            if (updateRule() || !RewardClipboardManager.isClipboardValid()) return false;

            if (!pasteToPanel) {
                List<String> groupKeys = selectedGroupKeys();
                if (!groupKeys.isEmpty()) {
                    return pasteToSelectedGroups(groupKeys);
                }
                if (rewardSelection.selectedIds().isEmpty()) {
                    return pasteWithoutSelection();
                }
                if (updateSelection()) return false;
            }

            if (RewardClipboardManager.isClipboardValid()) {
                // 面板
                if (pasteToPanel || currRewardButton.equalsIgnoreCase("panel")) {
                    if (rule == ERewardRule.CDK_REWARD) {
                        Minecraft.getInstance().setScreen(new StringInputScreen(screen
                                , new TextList(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_reward_rule_key_" + rule.getCode())
                                , Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_valid_until")
                                , Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_num"))
                                , new TextList(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_something"))
                                , new StringList("\\w*"
                                , ""
                                , "\\d*")
                                , new StringList(RewardConfigManager.getCdkRewardKey(RewardClipboardManager.deSerializeRewardList().getKey())
                                , DateUtils.toString(DateUtils.addMonth(SakuraClock.clientNow(), 1))
                                , RewardConfigManager.getCdkRewardNum(RewardClipboardManager.deSerializeRewardList().getKey()) + "")
                                , input -> {
                            StringList result = new StringList("", "", "");
                            if (CollectionUtils.isNotNullOrEmpty(input)) {
                                if (!RewardConfigManager.validateKeyName(rule, input.get(0))) {
                                    result.set(0, SakuraComponent.get().transClient("format", "reward_rule_s_error", input.get(0)).toString());
                                }
                                if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                                    if (DateUtils.format(input.get(1)) == null) {
                                        result.set(1, SakuraComponent.get().transClient("format", "valid_until_s_error", input.get(1)).toString());
                                    }
                                }
                                if (StringUtils.isNotNullOrEmpty(input.get(2))) {
                                    if (NumberUtils.toInt(input.get(2)) == 0) {
                                        result.set(2, SakuraComponent.get().transClient("format", "num_s_error", input.get(2)).toString());
                                    }
                                }
                                if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                                    RewardConfigManager.addKeyName(rule, String.format("%s|%s|-1|%d", input.get(0), input.get(1), NumberUtils.toInt(input.get(2), 1)), rewardList);
                                    RewardConfigManager.saveRewardOption();
                                }
                            }
                            return result;
                        }));
                    } else if (rule != ERewardRule.BASE_REWARD) {
                        Minecraft.getInstance().setScreen(new StringInputScreen(screen
                                , Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_reward_rule_key_" + rule.getCode())
                                , Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.enter_something")
                                , ruleInputRegex(rule)
                                , displayRuleInput(rule, RewardClipboardManager.deSerializeRewardList().getKey())
                                , input -> {
                            StringList result = new StringList();
                            if (CollectionUtils.isNotNullOrEmpty(input)) {
                                String normalized = normalizeRuleInput(rule, input.get(0));
                                if (RewardConfigManager.validateKeyName(rule, normalized)) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                                    RewardConfigManager.addKeyName(rule, normalized, rewardList);
                                    RewardConfigManager.saveRewardOption();
                                } else {
                                    result.add(SakuraComponent.get().transClient("format", "reward_rule_s_error", input.get(0)).toString());
                                }
                            }
                            return result;
                        }));
                    } else {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                        RewardConfigManager.addKeyName(rule, "", rewardList);
                        RewardConfigManager.saveRewardOption();
                    }

                }
                // 标题
                else if (currRewardButton.startsWith("标题")) {
                    RewardConfigManager.addUndoRewardOption(rule);
                    RewardConfigManager.clearRedoList();
                    RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                    RewardConfigManager.addKeyName(rule, key, rewardList);
                    RewardConfigManager.saveRewardOption();
                }
                // 普通按钮
                else {
                    RewardConfigManager.addUndoRewardOption(rule);
                    RewardConfigManager.clearRedoList();
                    for (Reward reward : RewardClipboardManager.deSerializeRewardList()) {
                        RewardConfigManager.addReward(rule, key, reward);
                    }
                    RewardConfigManager.saveRewardOption();
                }
                updateLayout();
                return true;
            }
            return false;
        }

        /** 无选择目标时，基础奖励直接追加，其他规则沿用新建组表单。 */
        private boolean pasteWithoutSelection() {
            if (rule != ERewardRule.BASE_REWARD) {
                return handlePaste(true);
            }
            RewardList rewards = RewardClipboardManager.deSerializeRewardList().toRewardList();
            if (rewards.isEmpty()) {
                return false;
            }
            RewardConfigManager.addUndoRewardOption(rule);
            RewardConfigManager.clearRedoList();
            RewardConfigManager.addKeyName(rule, "base", rewards);
            RewardConfigManager.saveRewardOption();
            updateLayout();
            return true;
        }

        /**
         * 一次编辑事务可将剪贴板奖励追加到多个已选奖励组。
         */
        private boolean pasteToSelectedGroups(List<String> groupKeys) {
            Map<String, RewardList> rewardMap = RewardConfigManager.getRewardMap(rule);
            RewardList clipboardRewards = RewardClipboardManager.deSerializeRewardList().toRewardList();
            List<String> targets = new ArrayList<>();
            for (String groupKey : groupKeys) {
                if (rewardMap.containsKey(groupKey)) {
                    targets.add(groupKey);
                }
            }
            if (targets.isEmpty() || clipboardRewards.isEmpty()) {
                return false;
            }

            RewardConfigManager.addUndoRewardOption(rule);
            RewardConfigManager.clearRedoList();
            for (String groupKey : targets) {
                for (Reward reward : clipboardRewards) {
                    RewardConfigManager.addReward(rule, groupKey, reward.clone());
                }
            }
            RewardConfigManager.saveRewardOption();
            updateLayout();
            return true;
        }

        public boolean handleDelete() {
            if (updateSelection()) return false;

            if (!selectedRewardIds().isEmpty()) {
                return deleteSelectedRewards();
            }

            // 面板
            if (currRewardButton.equalsIgnoreCase("panel")) {
                return false;
            }
            // 标题
            else if (currRewardButton.startsWith("标题")) {
                RewardConfigManager.addUndoRewardOption(rule);
                RewardConfigManager.clearRedoList();
                if (rule == ERewardRule.BASE_REWARD) {
                    RewardConfigManager.clearKey(rule, key);
                } else {
                    RewardConfigManager.deleteKey(rule, key);
                }
                RewardConfigManager.saveRewardOption();
            }
            // 普通按钮
            else {
                RewardConfigManager.addUndoRewardOption(rule);
                RewardConfigManager.clearRedoList();
                RewardConfigManager.deleteReward(rule, key, Integer.parseInt(index));
                RewardConfigManager.saveRewardOption();
            }
            updateLayout();
            return true;
        }

        /**
         * 按奖励组逆序删除索引，避免前一个删除动作改变后续条目的位置。
         */
        private boolean deleteSelectedRewards() {
            Map<String, List<Integer>> groupedIndices = new LinkedHashMap<>();
            for (String selectedId : selectedRewardIds()) {
                String[] parts = selectedId.split(",", 2);
                if (parts.length != 2) {
                    continue;
                }
                groupedIndices.computeIfAbsent(parts[0], ignored -> new ArrayList<>())
                        .add(Integer.parseInt(parts[1]));
            }
            if (groupedIndices.isEmpty()) {
                return false;
            }
            RewardConfigManager.addUndoRewardOption(rule);
            RewardConfigManager.clearRedoList();
            groupedIndices.forEach((groupKey, indices) -> {
                indices.sort(Collections.reverseOrder());
                for (Integer rewardIndex : indices) {
                    RewardConfigManager.deleteReward(rule, groupKey, rewardIndex);
                }
            });
            RewardConfigManager.saveRewardOption();
            rewardSelection.clear();
            currRewardButton = null;
            updateLayout();
            return true;
        }

        /**
         * 撤销
         */
        public boolean handleUndo() {
            if (updateRule()) return false;
            Map<String, RewardList> map = RewardConfigManager.getUnDoRewardOption(rule);
            if (!map.isEmpty()) {
                RewardConfigManager.addRedoRewardOption(rule);
                RewardConfigManager.setRewardMap(RewardConfigManager.getRewardConfig(), rule, map);
                RewardConfigManager.saveRewardOption();
                updateLayout();
                return true;
            }
            return false;
        }

        /**
         * 重做
         */
        public boolean handleRedo() {
            if (updateRule()) return false;

            Map<String, RewardList> map = RewardConfigManager.getReDoRewardOption(rule);
            if (!map.isEmpty()) {
                RewardConfigManager.addUndoRewardOption(rule);
                RewardConfigManager.setRewardMap(RewardConfigManager.getRewardConfig(), rule, map);
                RewardConfigManager.saveRewardOption();
                updateLayout();
                return true;
            }
            return false;
        }

    }

    public RewardOptionScreen() {
        super(SakuraComponent.get().transClient("word", "reward_option_title"));
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    private static String ruleInputRegex(ERewardRule rule) {
        if (rule == ERewardRule.RANDOM_REWARD) {
            return RewardProbabilityInput.PERCENT_REGEX;
        }
        if (rule == ERewardRule.PERSONAL_DATE_REWARD || rule == ERewardRule.LOTTERY_REWARD) {
            return "[a-z0-9_.-]{0,64}";
        }
        return "[\\d +~/:.T-]*";
    }

    private static String displayRuleInput(ERewardRule rule, String internalKey) {
        if (rule != ERewardRule.RANDOM_REWARD) {
            return internalKey == null ? "" : internalKey;
        }
        String probability = RewardConfigManager.getDisplayKey(rule, internalKey);
        return RewardProbabilityInput.display(NumberUtils.toBigDecimal(probability));
    }

    private static String normalizeRuleInput(ERewardRule rule, String input) {
        String trimmed = input == null ? "" : input.trim();
        return rule == ERewardRule.RANDOM_REWARD
                ? NumberUtils.toFixedEx(RewardProbabilityInput.parse(trimmed), 10)
                : trimmed;
    }

    @Override
    protected void onInit() {
        this.leftBarTitleHeight = 5 * 2 + super.font.lineHeight;
        ClientEventHandler.loadThemeTexture();
        tips = Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.reward_option_screen_tips");
    }

    @Override
    protected void initWidgets() {
        OP_BUTTONS.clear();
        REWARD_BUTTONS.clear();

        registerOperation(new RewardOperationWidget(this, OperationButtonType.REWARD_PANEL.getCode(), context -> {
            if ("panel".equals(currRewardButton)) {
                RewardOperationWidget widget = context.getWidget();
                AbstractGuiUtils.fillOutLine(context.getStack(),
                        (int) widget.realX() - 1, (int) widget.realY(),
                        (int) widget.realWidth() + 2, (int) widget.realHeight(),
                        1, 0x88FFF13B);
            }
        }), new ScreenCoordinate(20, 0, width - 40, height));
        OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode())
                .setDragHandler(event -> scrollRewardPanel(event.dragY()));

        registerOperation(createThemeIcon(OperationButtonType.OPEN,
                        SakuraClientState.getThemeTextureCoordinate().getArrowUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowHoverUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowTapUV())
                        .setHoverTint(0)
                        .setVisualBounds(new ScreenCoordinate(4, (height - 16) / 2.0, 16, 16))
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.open_sidebar")),
                new ScreenCoordinate(0, 0, 20, height));
        registerOperation(createThemeIcon(OperationButtonType.CLOSE,
                        SakuraClientState.getThemeTextureCoordinate().getArrowUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowHoverUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowTapUV())
                        .setFlipHorizontal(true)
                        .setHoverTint(0)
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.close_sidebar")),
                new ScreenCoordinate(80, (leftBarTitleHeight - 16) / 2.0, 16, 16));

        for (int i = 0; i < REWARD_RULES.length; i++) {
            OperationButtonType type = OperationButtonType.valueOf(REWARD_RULES[i].name());
            RewardOperationWidget widget = new RewardOperationWidget(this, type.getCode(),
                    generateCustomRenderFunction(
                            SakuraUtils.getRewardRuleI18nKeyName(REWARD_RULES[i]), REWARD_RULES[i]))
                    .setDragHandler(event -> scrollRuleList(event.dragY()));
            registerOperation(widget,
                    new ScreenCoordinate(0, leftBarTitleHeight + (leftBarTitleHeight - 1) * i,
                            100, leftBarTitleHeight - 2));
        }

        registerOperation(new RewardOperationWidget(this, OperationButtonType.OFFSET_Y.getCode(), context -> {
            context.getGraphics().drawString(font, "OY:", width - rightBarWidth + 1,
                    height - font.lineHeight * 2 - 2, 0xFFACACAC, false);
            drawLimitedText(context.getStack(), String.valueOf((int) yOffset),
                    width - rightBarWidth + 1, height - font.lineHeight - 2,
                    rightBarWidth, 0xFFACACAC, false);
        }), new ScreenCoordinate(width - rightBarWidth, height - font.lineHeight * 2 - 2,
                rightBarWidth, font.lineHeight * 2 + 2));

        registerOperation(createDrawnIcon(OperationButtonType.HELP),
                new ScreenCoordinate(width - rightBarWidth + 1, 2, 18, 18));
        registerOperation(createDrawnIcon(OperationButtonType.DOWNLOAD)
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.download_reward_config")),
                new ScreenCoordinate(width - rightBarWidth + 1, 22, 18, 18));
        registerOperation(createDrawnIcon(OperationButtonType.UPLOAD),
                new ScreenCoordinate(width - rightBarWidth + 1, 42, 18, 18));
        registerOperation(createDrawnIcon(OperationButtonType.FOLDER)
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.open_config_folder")),
                new ScreenCoordinate(width - rightBarWidth + 1, 62, 18, 18));
        registerOperation(createDrawnIcon(OperationButtonType.SORT)
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.reward_rule_sort")),
                new ScreenCoordinate(width - rightBarWidth + 1, 82, 18, 18));
        registerOperation(createDrawnIcon(OperationButtonType.MERGE)
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.merge_same_rewards")),
                new ScreenCoordinate(width - rightBarWidth + 1, 102, 18, 18));
        updateLayout();
    }

    private RewardOperationWidget createDrawnIcon(OperationButtonType type) {
        return new RewardOperationWidget(this, type.getCode(), context -> {
            RewardOperationWidget widget = context.getWidget();
            int x = (int) widget.realX();
            int y = (int) widget.realY();
            int width = Math.max(1, (int) widget.realWidth());
            int height = Math.max(1, (int) widget.realHeight());
            int color = widget.pressed() ? getEffectiveTheme().accentPressed()
                    : widget.hovered() ? getEffectiveTheme().accentHover()
                    : getEffectiveTheme().accentFocused();
            drawOperationIcon(context.getStack(), type, x, y + (widget.pressed() ? 1 : 0),
                    width, height, color);
        });
    }

    /** 右侧工具栏使用无背景像素线条，避免小尺寸按钮出现厚重色块。 */
    private void drawOperationIcon(PoseStack stack, OperationButtonType type,
                                   int x, int y, int width, int height, int color) {
        int left = x + (width - 12) / 2;
        int top = y + (height - 12) / 2;
        switch (type) {
            case HELP:
                iconRect(stack, left + 3, top + 1, 5, 1, color);
                iconRect(stack, left + 8, top + 2, 1, 3, color);
                iconRect(stack, left + 6, top + 5, 3, 1, color);
                iconRect(stack, left + 5, top + 6, 2, 2, color);
                iconRect(stack, left + 5, top + 10, 2, 2, color);
                break;
            case DOWNLOAD:
                drawTransferArrow(stack, left, top, false, color);
                break;
            case UPLOAD:
                drawTransferArrow(stack, left, top, true, color);
                break;
            case FOLDER:
                iconRect(stack, left + 1, top + 3, 5, 1, color);
                iconRect(stack, left + 1, top + 3, 1, 8, color);
                iconRect(stack, left + 5, top + 4, 1, 2, color);
                iconRect(stack, left + 5, top + 5, 6, 1, color);
                iconRect(stack, left + 10, top + 5, 1, 6, color);
                iconRect(stack, left + 1, top + 10, 10, 1, color);
                break;
            case SORT:
                iconRect(stack, left + 1, top + 2, 10, 1, color);
                iconRect(stack, left + 1, top + 5, 7, 1, color);
                iconRect(stack, left + 1, top + 8, 5, 1, color);
                iconRect(stack, left + 1, top + 11, 3, 1, color);
                break;
            case MERGE:
                iconRect(stack, left + 1, top + 2, 4, 1, color);
                iconRect(stack, left + 1, top + 9, 4, 1, color);
                iconRect(stack, left + 4, top + 2, 1, 8, color);
                iconRect(stack, left + 4, top + 5, 6, 2, color);
                iconRect(stack, left + 8, top + 4, 2, 1, color);
                iconRect(stack, left + 8, top + 7, 2, 1, color);
                break;
            default:
                break;
        }
    }

    private static void drawTransferArrow(PoseStack stack, int left, int top,
                                          boolean upload, int color) {
        float centerX = left + 6;
        float terminalY = top + 10;
        float tipY = upload ? top + 2 : terminalY;
        float shoulderY = top + (upload ? 5 : 6);
        float shaftStartY = upload ? terminalY : top + 2;
        float shaftEndY = top + (upload ? 3 : 8);
        AbstractGuiUtils.drawLineWithSquareCaps(stack, centerX, shaftStartY, centerX, shaftEndY,
                1.6F, color);
        AbstractGuiUtils.drawLineWithSquareCaps(stack, centerX, tipY, centerX - 3, shoulderY,
                1.6F, color);
        AbstractGuiUtils.drawLineWithSquareCaps(stack, centerX, tipY, centerX + 3, shoulderY,
                1.6F, color);
        AbstractGuiUtils.drawLineWithSquareCaps(stack, left + 2, top + 11, left + 10, top + 11,
                1.6F, color);
    }

    private static void iconRect(PoseStack stack, int x, int y,
                                 int width, int height, int color) {
        AbstractGuiUtils.fill(stack, x, y, width, height, color);
    }

    private RewardOperationWidget createThemeIcon(OperationButtonType type, Coordinate coordinate) {
        return createThemeIcon(type, coordinate, coordinate, coordinate);
    }

    private RewardOperationWidget createThemeIcon(OperationButtonType type, Coordinate normal,
                                                   Coordinate hover, Coordinate pressed) {
        return new RewardOperationWidget(this, type.getCode(), SakuraClientState.getThemeTexture())
                .setNormal(normal)
                .setHover(hover)
                .setPressed(pressed)
                .setTextureWidth(SakuraClientState.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraClientState.getThemeTextureCoordinate().getTotalHeight())
                .setHoverTint(0xAA808080)
                .setPressedTint(0xAAA0A0A0);
    }

    private void registerOperation(RewardOperationWidget widget, ScreenCoordinate bounds) {
        widget.bounds(bounds);
        widget.setReleaseHandler(event -> {
            AtomicBoolean updateLayout = new AtomicBoolean(false);
            AtomicBoolean handled = new AtomicBoolean(false);
            handleOperation(event, widget, updateLayout, handled);
            if (handled.get()) {
                rewardSelection.clear();
                currRewardButton = null;
            }
            if (updateLayout.get()) {
                updateLayout();
            }
        });
        OP_BUTTONS.put(widget.getOperation(), widget);
        addWidget(widget);
    }

    @Override
    protected void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack matrixStack = graphics.pose();
        this.ms = matrixStack;
        this.renderBackgroundTexture(matrixStack);

        // 重置Y轴偏移
        if (this.yOffsetResetTime > 0) {
            double elapsed = System.currentTimeMillis() - this.yOffsetResetTime;
            if (elapsed >= 1000) {
                this.yOffsetResetTime = 0;
                this.yOffsetOld = 0;
                this.setYOffset(0);
            } else {
                double t = elapsed / 1000.0;
                double progress = 1 - Math.pow(1 - t, 3);
                this.setYOffset((1 - progress) * this.yOffsetOld);
            }
        }

        // 刷新数据
        if (RewardConfigManager.isRewardOptionDataChanged()) this.updateLayout();

        // 绘制操作提示
        if (isCurrentRuleRedacted()) {
            renderPermissionDenied(graphics);
        } else if (OperationButtonType.valueOf(currOpButton) == null) {
            BaseShapeWidget.drawShape(new ShapeDrawArgs()
                    .stack(matrixStack)
                    .type(ShapeDrawArgs.ShapeType.RECT)
                    .color(0x88000000)
                    .rect(new ShapeDrawArgs.RectParams()
                            .x(this.leftBarWidth + 4).y(4)
                            .width(super.width - this.leftBarWidth
                                    - this.rightBarWidth - 8)
                            .height(super.height - 8).radius(15).border(0)));
            tips.stack(matrixStack).font(super.font);
            drawWelcomeTips(matrixStack);
        }
        else this.prepareRewardList();

        // 绘制左侧边栏列表背景
        AbstractGuiUtils.fill(matrixStack, 0, 0, leftBarWidth, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(matrixStack, 0, 0, leftBarWidth, super.height, 1, 0xFF000000);
        // 绘制左侧边栏列表标题
        if (SakuraClientState.isRewardOptionBarOpened()) {
            drawLimitedText(matrixStack,
                    SakuraComponent.get().transClient("word", "reward_rule_type").toString(),
                    4, 5, Math.max(0, leftBarWidth - 8), 0xFFACACAC, false);
            AbstractGuiUtils.fill(matrixStack, 0, leftBarTitleHeight,
                    leftBarWidth, 1, 0xAA000000);
        }
        // 绘制右侧边栏列表背景
        AbstractGuiUtils.fill(matrixStack, super.width - rightBarWidth, 0,
                rightBarWidth, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(matrixStack, super.width - rightBarWidth, 0, rightBarWidth, super.height, 1, 0xFF000000);

        updateOperationPresentation();
        renderWidgets(graphics, partialTicks);
        renderDraggedReward(graphics);
        addDeferredTooltipRender(stack -> {
            // 弹出菜单是当前交互焦点，避免下层奖励或工具提示穿透到菜单上方。
            if (!popupOption.isEmpty() || draggingRewardId != null) {
                return;
            }
            for (RewardListEntryWidget entry : REWARD_BUTTONS.values()) {
                entry.renderTooltip(stack, inputState.mouseX(), inputState.mouseY());
            }
            for (RewardOperationWidget widget : OP_BUTTONS.values()) {
                widget.renderTooltip(stack, inputState.mouseX(), inputState.mouseY());
            }
        });
    }

    private void updateOperationPresentation() {
        boolean opened = SakuraClientState.isRewardOptionBarOpened();
        OP_BUTTONS.forEach((operation, widget) -> {
            boolean rule = operation > 200 && operation <= 299;
            widget.visible(operation == OperationButtonType.OPEN.getCode() ? !opened
                    : operation == OperationButtonType.CLOSE.getCode() || rule ? opened : true);
        });

        OP_BUTTONS.get(OperationButtonType.OFFSET_Y.getCode()).setTooltip(
                Text.trans(SakuraSignIn.MODID, "format.sakura_sign_in.y_offset",
                        NumberUtils.toFixedEx(yOffset, 1)));
        OP_BUTTONS.get(OperationButtonType.HELP.getCode()).setTooltip(
                Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.help_button"));

        LocalPlayer player = Minecraft.getInstance().player;
        RewardOperationWidget upload = OP_BUTTONS.get(OperationButtonType.UPLOAD.getCode());
        if (player != null && player.hasPermissions(CommonConfig.get().permission().permissionEditReward())) {
            upload.setTooltip(Text.trans(SakuraSignIn.MODID,
                            "word.sakura_sign_in.upload_reward_config"))
                    .setPressedTint(0xAAA0A0A0);
        } else {
            upload.setTooltip(Text.trans(SakuraSignIn.MODID,
                            "word.sakura_sign_in.upload_reward_config_no_permission").color(0xFFFF0000))
                    .setPressedTint(0xAA808080);
        }
    }

    private boolean isCurrentRuleRedacted() {
        return RewardConfigManager.isRuleRedacted(currentRewardRule());
    }

    /** 无权限规则只展示占位页，客户端不持有其服务端奖励内容。 */
    private void renderPermissionDenied(GuiGraphics graphics) {
        PoseStack stack = graphics.pose();
        String message = SakuraComponent.get().transClient(
                "word", "reward_rule_permission_denied_page").toString();
        float scale = 1.5F;
        float centerX = leftBarWidth
                + (width - leftBarWidth - rightBarWidth) / 2.0F;
        float centerY = height / 2.0F;
        stack.pushPose();
        stack.scale(scale, scale, 1.0F);
        graphics.drawString(font, message,
                (int) (centerX / scale - font.width(message) / 2.0F),
                (int) (centerY / scale - font.lineHeight / 2.0F),
                getEffectiveTheme().buttonText(), false);
        stack.popPose();
    }

    @Override
    protected void onMouseScrolled(MouseScrolledHandleArgs eventArgs) {
        if (SakuraClientState.isRewardOptionBarOpened()
                && eventArgs.mouseX() >= 0 && eventArgs.mouseX() < leftBarWidth
                && eventArgs.mouseY() >= leftBarTitleHeight) {
            scrollRuleList(eventArgs.delta() * Math.max(1, leftBarTitleHeight - 1));
            eventArgs.consumed(true);
            return;
        }
        RewardOperationWidget panel = OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode());
        if (panel != null && panel.isMouseInside(eventArgs.mouseX(), eventArgs.mouseY())) {
            scrollRewardPanel(eventArgs.delta() * rewardWheelStep);
            eventArgs.consumed(true);
        }
    }

    @Override
    protected void onKeyPressed(KeyPressedHandleArgs eventArgs) {
        if (eventArgs.keyCode() == GLFWKey.GLFW_KEY_ESCAPE) {
            onClose();
            eventArgs.consumed(true);
        } else if (eventArgs.keyCode() == GLFWKey.GLFW_KEY_F5) {
            refreshRewardScreen();
            eventArgs.consumed(true);
        } else if (beginNavigation(eventArgs.keyCode())) {
            eventArgs.consumed(true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        repeatHeldNavigation();
    }

    /** 重新构建当前奖励界面，并恢复默认垂直偏移。 */
    private void refreshRewardScreen() {
        rewardSelection.clear();
        currRewardButton = null;
        draggingRewardId = null;
        dragTargetGroupKey = null;
        popupOption.clear();
        clearHeldNavigation();
        yOffsetResetTime = 0;
        yOffsetOld = 0;
        setYOffset(0);
        updateLayout();
    }

    private boolean beginNavigation(int keyCode) {
        if (navigationDirection(keyCode) == null) {
            return false;
        }
        if (heldNavigationKey == keyCode) {
            return true;
        }
        if (!moveRewardSelection(keyCode)) {
            return false;
        }
        long now = System.currentTimeMillis();
        heldNavigationKey = keyCode;
        navigationHeldSince = now;
        nextNavigationRepeatAt = now + 350L;
        return true;
    }

    private void repeatHeldNavigation() {
        if (heldNavigationKey < 0 || !BaniraInput.isKeyDown(heldNavigationKey)) {
            clearHeldNavigation();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextNavigationRepeatAt) {
            return;
        }
        moveRewardSelection(heldNavigationKey);
        long heldMillis = now - navigationHeldSince;
        long interval = Math.max(50L, 180L - heldMillis / 15L);
        nextNavigationRepeatAt = now + interval;
    }

    private void clearHeldNavigation() {
        heldNavigationKey = -1;
        navigationHeldSince = 0;
        nextNavigationRepeatAt = 0;
    }

    private boolean moveRewardSelection(int keyCode) {
        RewardKeyboardNavigator.Direction direction = navigationDirection(keyCode);
        if (direction == null || rewardSelection.primary() == null
                || rewardSelection.primary().startsWith("标题")
                || !popupOption.isEmpty() || draggingRewardId != null
                || inputState.isCtrlPressing() || inputState.isShiftPressing()
                || BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_LEFT_ALT)
                || BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_RIGHT_ALT)) {
            return false;
        }
        List<RewardKeyboardNavigator.Point> points = new ArrayList<>();
        List<String> orderedIds = new ArrayList<>();
        REWARD_BUTTONS.forEach((id, widget) -> {
            if (!id.startsWith("标题")) {
                orderedIds.add(id);
                points.add(new RewardKeyboardNavigator.Point(
                        id,
                        widget.realX() + widget.realWidth() / 2.0,
                        widget.realY() + widget.realHeight() / 2.0));
            }
        });
        LinkedHashSet<String> moved = new LinkedHashSet<>();
        boolean changed = false;
        for (String selected : rewardSelection.selectedIds()) {
            if (selected.startsWith("标题") || !orderedIds.contains(selected)) {
                continue;
            }
            String next;
            if (direction == RewardKeyboardNavigator.Direction.LEFT
                    || direction == RewardKeyboardNavigator.Direction.RIGHT) {
                next = RewardKeyboardNavigator.findAdjacent(selected, orderedIds, direction);
            } else {
                next = RewardKeyboardNavigator.findNext(selected, points, direction);
            }
            moved.add(next == null ? selected : next);
            changed |= next != null && !next.equals(selected);
        }
        if (!changed || moved.isEmpty()) {
            return false;
        }
        rewardSelection.selectOnly(moved);
        currRewardButton = rewardSelection.primary();
        ensureRewardVisible(currRewardButton);
        return true;
    }

    private RewardKeyboardNavigator.Direction navigationDirection(int keyCode) {
        if (keyCode == GLFWKey.GLFW_KEY_LEFT || keyCode == GLFWKey.GLFW_KEY_A) {
            return RewardKeyboardNavigator.Direction.LEFT;
        }
        if (keyCode == GLFWKey.GLFW_KEY_RIGHT || keyCode == GLFWKey.GLFW_KEY_D) {
            return RewardKeyboardNavigator.Direction.RIGHT;
        }
        if (keyCode == GLFWKey.GLFW_KEY_UP || keyCode == GLFWKey.GLFW_KEY_W) {
            return RewardKeyboardNavigator.Direction.UP;
        }
        if (keyCode == GLFWKey.GLFW_KEY_DOWN || keyCode == GLFWKey.GLFW_KEY_S) {
            return RewardKeyboardNavigator.Direction.DOWN;
        }
        return null;
    }

    private void ensureRewardVisible(String rewardId) {
        RewardListEntryWidget widget = REWARD_BUTTONS.get(rewardId);
        if (widget == null) {
            return;
        }
        double top = widget.realY();
        double bottom = top + widget.realHeight();
        if (top < topMargin) {
            setYOffset(yOffset + topMargin - top);
        } else if (bottom > height - bottomMargin) {
            setYOffset(yOffset - (bottom - height + bottomMargin));
        }
    }

    @Override
    protected void onKeyReleased(KeyReleasedHandleArgs eventArgs) {
        if (eventArgs.keyCode() == heldNavigationKey) {
            clearHeldNavigation();
        }
        if (isCurrentRuleRedacted()) {
            return;
        }
        boolean consumed = false;
        int keyCode = eventArgs.keyCode();
        if (matchesShortcut(ClientConfig.get().rewardKeys().copy(), keyCode)) {
            consumed = editHandler.handleCopy();
        } else if (matchesShortcut(ClientConfig.get().rewardKeys().paste(), keyCode)) {
            consumed = editHandler.handlePaste();
        } else if (matchesShortcut(ClientConfig.get().rewardKeys().cut(), keyCode)) {
            consumed = editHandler.handleCut();
        } else if (matchesShortcut(ClientConfig.get().rewardKeys().delete(), keyCode)) {
            consumed = requestDeleteConfirmation();
        } else if (matchesShortcut(ClientConfig.get().rewardKeys().undo(), keyCode)) {
            consumed = editHandler.handleUndo();
        } else if (matchesShortcut(ClientConfig.get().rewardKeys().redo(), keyCode)) {
            consumed = editHandler.handleRedo();
        }
        eventArgs.consumed(consumed);
    }

    private boolean matchesShortcut(List<String> bindings, int releasedKey) {
        int[] keys = {
                releasedKey,
                BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_LEFT_CONTROL)
                        ? GLFWKey.GLFW_KEY_LEFT_CONTROL : GLFWKey.GLFW_KEY_UNKNOWN,
                BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_RIGHT_CONTROL)
                        ? GLFWKey.GLFW_KEY_RIGHT_CONTROL : GLFWKey.GLFW_KEY_UNKNOWN,
                BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_LEFT_SHIFT)
                        ? GLFWKey.GLFW_KEY_LEFT_SHIFT : GLFWKey.GLFW_KEY_UNKNOWN,
                BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_RIGHT_SHIFT)
                        ? GLFWKey.GLFW_KEY_RIGHT_SHIFT : GLFWKey.GLFW_KEY_UNKNOWN,
                BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_LEFT_ALT)
                        ? GLFWKey.GLFW_KEY_LEFT_ALT : GLFWKey.GLFW_KEY_UNKNOWN,
                BaniraInput.isKeyDown(GLFWKey.GLFW_KEY_RIGHT_ALT)
                        ? GLFWKey.GLFW_KEY_RIGHT_ALT : GLFWKey.GLFW_KEY_UNKNOWN
        };
        int[] pressed = Arrays.stream(keys)
                .filter(key -> key != GLFWKey.GLFW_KEY_UNKNOWN)
                .distinct()
                .toArray();
        return bindings.stream().anyMatch(binding -> GLFWKeyHelper.matchKey(binding, pressed));
    }

}
