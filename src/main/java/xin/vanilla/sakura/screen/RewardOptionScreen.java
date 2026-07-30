package xin.vanilla.sakura.screen;

import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.client.gui.component.TextList;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.gui.ConfirmDialogScreen;
import xin.vanilla.banira.client.util.InputStateManager;
import xin.vanilla.banira.client.util.SystemUtils;
import xin.vanilla.banira.client.gui.widget.PopupOption;
import xin.vanilla.sakura.text.SakuraComponent;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Data;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.client.SakuraClientState;
import xin.vanilla.sakura.client.gui.AdvancementRewardSelectionFlow;
import xin.vanilla.sakura.client.gui.EffectRewardSelectionFlow;
import xin.vanilla.sakura.client.gui.ItemRewardSelectionFlow;
import xin.vanilla.sakura.client.gui.RewardListEntryWidget;
import xin.vanilla.sakura.client.gui.RewardOperationWidget;
import xin.vanilla.sakura.config.*;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.network.SakuraNetwork;
import xin.vanilla.sakura.network.packet.DownloadRewardOptionNotice;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.notification.SakuraClientNotifications;
import xin.vanilla.sakura.notification.SakuraNotificationTypes;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardClipboardManager;
import xin.vanilla.sakura.rewards.RewardList;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.DateUtils;
import xin.vanilla.sakura.util.GLFWKey;
import xin.vanilla.sakura.util.GLFWKeyHelper;
import xin.vanilla.sakura.util.SakuraUtils;
import xin.vanilla.sakura.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
public class RewardOptionScreen extends BaniraScreen {
    private static final Logger LOGGER = LogManager.getLogger();

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
    /**
     * 右侧边栏宽度
     */
    private final int rightBarWidth = 20;

    // region 奖励列表相关参数
    // 物品图标的大小
    private final int itemIconSize = 16;
    private final int itemRightMargin = 4;
    private final int itemBottomMargin = 8;
    // 标题的大小
    private final int titleHeight = 16;
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
    private MatrixStack ms;
    /**
     * 奖励列表索引(用于计算渲染Y坐标)
     */
    AtomicInteger rewardListIndex = new AtomicInteger(0);
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
    enum OperationButtonType {
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
        OFFSET_Y(301),
        HELP(302),
        DOWNLOAD(303),
        UPLOAD(304),
        FOLDER(305),
        SORT(306);

        final int code;

        OperationButtonType(int code) {
            this.code = code;
        }

        static OperationButtonType valueOf(int code) {
            return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
        }
    }

    /**
     * 绘制背景纹理
     */
    private void renderBackgroundTexture(MatrixStack matrixStack) {
        // 启用混合模式以支持透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 绑定背景纹理
        Minecraft.getInstance().getTextureManager().bind(SakuraClientState.getThemeTexture());

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

        // 计算UV比例
        float uMin = u0 / textureTotalWidth;
        float vMin = v0 / textureTotalHeight;
        float uMax = (u0 + regionWidth) / textureTotalWidth;
        float vMax = (v0 + regionHeight) / textureTotalHeight;

        // 使用Tessellator绘制平铺的纹理片段
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();

        // 绘制完整的纹理块
        for (int x = 0; x <= screenWidth - regionWidth; x += (int) regionWidth) {
            for (int y = 0; y <= screenHeight - regionHeight; y += (int) regionHeight) {
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
                buffer.vertex(matrixStack.last().pose(), x, y + regionHeight, 0).uv(uMin, vMax).endVertex();
                buffer.vertex(matrixStack.last().pose(), x + regionWidth, y + regionHeight, 0).uv(uMax, vMax).endVertex();
                buffer.vertex(matrixStack.last().pose(), x + regionWidth, y, 0).uv(uMax, vMin).endVertex();
                buffer.vertex(matrixStack.last().pose(), x, y, 0).uv(uMin, vMin).endVertex();
                tessellator.end();
            }
        }

        // 绘制剩余的竖条（右边缘）
        float leftoverWidth = screenWidth % regionWidth;
        float u = uMin + (leftoverWidth / regionWidth) * (uMax - uMin);
        if (leftoverWidth > 0) {
            for (int y = 0; y <= screenHeight - regionHeight; y += (int) regionHeight) {
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
                buffer.vertex(matrixStack.last().pose(), screenWidth - leftoverWidth, y + regionHeight, 0).uv(uMin, vMax).endVertex();
                buffer.vertex(matrixStack.last().pose(), screenWidth, y + regionHeight, 0).uv(u, vMax).endVertex();
                buffer.vertex(matrixStack.last().pose(), screenWidth, y, 0).uv(u, vMin).endVertex();
                buffer.vertex(matrixStack.last().pose(), screenWidth - leftoverWidth, y, 0).uv(uMin, vMin).endVertex();
                tessellator.end();
            }
        }

        // 绘制剩余的横条（底边缘）
        float leftoverHeight = screenHeight % regionHeight;
        float v = vMin + (leftoverHeight / regionHeight) * (vMax - vMin);
        if (leftoverHeight > 0) {
            for (int x = 0; x <= screenWidth - regionWidth; x += (int) regionWidth) {
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
                buffer.vertex(matrixStack.last().pose(), x, screenHeight, 0).uv(uMin, v).endVertex();
                buffer.vertex(matrixStack.last().pose(), x + regionWidth, screenHeight, 0).uv(uMax, v).endVertex();
                buffer.vertex(matrixStack.last().pose(), x + regionWidth, screenHeight - leftoverHeight, 0).uv(uMax, vMin).endVertex();
                buffer.vertex(matrixStack.last().pose(), x, screenHeight - leftoverHeight, 0).uv(uMin, vMin).endVertex();
                tessellator.end();
            }
        }

        // 绘制右下角的剩余区域
        if (leftoverWidth > 0 && leftoverHeight > 0) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.vertex(matrixStack.last().pose(), screenWidth - leftoverWidth, screenHeight, 0).uv(uMin, v).endVertex();
            buffer.vertex(matrixStack.last().pose(), screenWidth, screenHeight, 0).uv(u, v).endVertex();
            buffer.vertex(matrixStack.last().pose(), screenWidth, screenHeight - leftoverHeight, 0).uv(u, vMin).endVertex();
            buffer.vertex(matrixStack.last().pose(), screenWidth - leftoverWidth, screenHeight - leftoverHeight, 0).uv(uMin, vMin).endVertex();
            tessellator.end();
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
     * @param index      奖励列表的索引
     */
    private void addRewardTitleButton(String title, String key, int titleIndex, int index) {
        RewardListEntryWidget entry = new RewardListEntryWidget(this, titleIndex, context -> {
            RewardListEntryWidget widget = context.getEntry();
            AbstractGui.fill(context.getStack(), (int) widget.realX(), (int) widget.realY(),
                    (int) (widget.realX() + widget.realWidth()), (int) widget.realY() + 1, 0xAC000000);
            AbstractGuiUtils.drawLimitedText(context.getStack(), super.font, title,
                    (int) widget.realX(),
                    (int) (widget.realY() + (widget.realHeight() - super.font.lineHeight) / 2),
                    (int) widget.realWidth(), 0xAC000000, false);
            AbstractGui.fill(context.getStack(), (int) widget.realX(),
                    (int) (widget.realY() + widget.realHeight()),
                    (int) (widget.realX() + super.font.width(title)),
                    (int) (widget.realY() + widget.realHeight() - 1), 0xAC000000);
        }).setBaseX(leftBarWidth);
        entry.bounds()
                .x(leftMargin)
                .y(topMargin + (itemIconSize + itemBottomMargin)
                        * Math.floor((double) index / lineItemCount))
                .width(super.width - leftBarWidth - leftMargin - rightMargin - rightBarWidth)
                .height(titleHeight);
        registerRewardEntry(String.format("标题,%s", key), entry);
    }

    /**
     * 添加奖励图标按钮渲染方法
     *
     * @param rewardMap 奖励列表
     * @param key       奖励列表的key
     * @param index     奖励列表的索引
     */
    private void addRewardButton(Map<String, RewardList> rewardMap, String key, AtomicInteger index) {
        for (int j = 0; j < rewardMap.get(key).size(); j++, index.incrementAndGet()) {
            RewardListEntryWidget entry = new RewardListEntryWidget(this, j, context -> {
                RewardListEntryWidget widget = context.getEntry();
                Reward reward = rewardMap.get(key).get(widget.getOperation());
                AbstractGuiUtils.renderCustomReward(context.getStack(), this.itemRenderer, super.font,
                        SakuraClientState.getThemeTexture(), SakuraClientState.getThemeTextureCoordinate(),
                        reward, (int) widget.realX(), (int) widget.realY(), true);
            }).setBaseX(leftBarWidth)
                    .setTooltip(Text.from(rewardMap.get(key).get(j)
                            .getName(SakuraUtils.getClientLanguage(), true)));
            entry.bounds()
                    .x(leftMargin + (j % lineItemCount) * (itemIconSize + itemRightMargin))
                    .y(topMargin + (itemIconSize + itemBottomMargin)
                            * Math.floor((double) index.get() / lineItemCount))
                    .width(itemIconSize)
                    .height(itemIconSize);
            registerRewardEntry(String.format("%s,%s", key, j), entry);
        }
    }

    private void registerRewardEntry(String key, RewardListEntryWidget entry) {
        entry.setDragHandler(event -> setYOffset(yOffset + event.dragY()));
        entry.setReleaseHandler(event -> {
            AtomicBoolean updateLayout = new AtomicBoolean(false);
            AtomicBoolean handled = new AtomicBoolean(false);
            handleRewardOption(event.mouseX(), event.mouseY(), event.button(),
                    key, entry, updateLayout, handled);
            if (updateLayout.get()) {
                updateLayout();
            }
        });
        REWARD_BUTTONS.put(key, entry);
        addWidget(entry);
    }

    private StringInputScreen getRuleKeyInputScreen(Screen callbackScreen, ERewardRule rule, String[] key) {
        String validator = rule == ERewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
        return new StringInputScreen(callbackScreen, Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()).shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"), validator, "", input -> {
            StringList result = new StringList();
            if (CollectionUtils.isNotNullOrEmpty(input)) {
                if (RewardConfigManager.validateKeyName(rule, input.get(0))) {
                    key[0] = input.get(0);
                } else {
                    result.add(SakuraComponent.get().transClient("tips", "reward_rule_s_error", input.get(0)).toString());
                }
            }
            return result;
        });
    }

    private StringInputScreen getCdkRuleKeyInputScreen(Screen callbackScreen, ERewardRule rule, String[] key) {
        return new StringInputScreen(callbackScreen
                , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()).shadow(true)
                , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_valid_until").shadow(true)
                , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_num").shadow(true))
                , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                , new StringList("\\w*", "", "\\d*")
                , new StringList("", DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1)), "1")
                , input -> {
            StringList result = new StringList("", "", "");
            if (CollectionUtils.isNotNullOrEmpty(input)) {
                if (!RewardConfigManager.validateKeyName(rule, input.get(0))) {
                    result.set(0, SakuraComponent.get().transClient("tips", "reward_rule_s_error", input.get(0)).toString());
                }
                if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                    if (DateUtils.format(input.get(1)) == null) {
                        result.set(1, SakuraComponent.get().transClient("tips", "valid_until_s_error", input.get(1)).toString());
                    }
                }
                if (StringUtils.isNotNullOrEmpty(input.get(2))) {
                    if (StringUtils.toInt(input.get(2)) == 0) {
                        result.set(2, SakuraComponent.get().transClient("tips", "num_s_error", input.get(2)).toString());
                    }
                }
                if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                    key[0] = String.format("%s|%s|-1|%d", input.get(0), input.get(1), StringUtils.toInt(input.get(2), 1));
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
        if (OperationButtonType.valueOf(currOpButton) == null) return;
        RewardConfig rewardConfig = RewardConfigManager.getRewardConfig();
        int titleIndex = -1;
        rewardListIndex.set(0);
        switch (OperationButtonType.valueOf(currOpButton)) {
            case BASE_REWARD: {
                this.addRewardTitleButton(SakuraComponent.get().transClient("title", "base_reward").toString(), "base", titleIndex, rewardListIndex.get());
                rewardListIndex.addAndGet(lineItemCount);
                this.addRewardButton(new HashMap<String, RewardList>() {{
                    put("base", rewardConfig.getBaseRewards());
                }}, "base", rewardListIndex);
            }
            break;
            case CONTINUOUS_REWARD: {
                for (String key : rewardConfig.getContinuousRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(SakuraComponent.get().transClient("title", "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getContinuousRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case CYCLE_REWARD: {
                for (String key : rewardConfig.getCycleRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(SakuraComponent.get().transClient("title", "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getCycleRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case YEAR_REWARD: {
                for (String key : rewardConfig.getYearRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(SakuraComponent.get().transClient("title", "year_day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getYearRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case MONTH_REWARD: {
                for (String key : rewardConfig.getMonthRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(SakuraComponent.get().transClient("title", "month_day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getMonthRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case WEEK_REWARD: {
                for (String key : rewardConfig.getWeekRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(SakuraComponent.get().transClient("title", "week_" + key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getWeekRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case DATE_TIME_REWARD: {
                for (String key : rewardConfig.getDateTimeRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(String.format("%s", key), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getDateTimeRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case CUMULATIVE_REWARD: {
                for (String key : rewardConfig.getCumulativeRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(SakuraComponent.get().transClient("title", "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getCumulativeRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case RANDOM_REWARD: {
                Map<String, RewardList> randomRewards =
                        RewardConfigManager.getRewardMap(ERewardRule.RANDOM_REWARD);
                for (String key : randomRewards.keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    String probability = RewardConfigManager.getDisplayKey(
                            ERewardRule.RANDOM_REWARD, key);
                    this.addRewardTitleButton(String.format("%s%%", StringUtils.toFixedEx(new BigDecimal(probability).multiply(new BigDecimal(100)), 10)), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(randomRewards, key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case CDK_REWARD: {
                for (int i = 0; i < rewardConfig.getCdkRewards().size(); i++) {
                    KeyValue<KeyValue<String, String>, KeyValue<RewardList, AtomicInteger>> keyValue = rewardConfig.getCdkRewards().get(i);
                    String key = String.format("%s|%s|%d|%d", keyValue.getKey().getKey(), keyValue.getKey().getValue(), i, keyValue.getValue().getValue().get());
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(SakuraComponent.get().transClient("title", "s_valid_until_s", keyValue.getKey().getKey(), keyValue.getKey().getValue(), keyValue.getValue().getValue()).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(new HashMap<String, RewardList>() {{
                        put(key, keyValue.getValue().getKey());
                    }}, key, rewardListIndex);
                }
            }
            break;
        }
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
                    .setSelected(item.getKey().equals(this.currRewardButton))
                    .setSelectedColor((selectedColor & 0x00FFFFFF) | 0xCC000000);
        }
    }

    private final Consumer<PopupOption> pasteConsumer = option -> {
        String paste = SakuraComponent.get().transClient("option", "paste").toString();
        if (paste.equalsIgnoreCase(option.getSelectedString())) {
            option.getRenderList().stream()
                    .filter(item -> paste.equalsIgnoreCase(item.content()))
                    .forEach(item -> item.color(RewardClipboardManager.isClipboardValid() ? 0xFFFFFFFF : 0xFF999999));
        }
    };

    /**
     * 处理操作按钮事件
     *
     * @param mouseX       鼠标X坐标
     * @param mouseY       鼠标Y坐标
     * @param button       鼠标按键
     * @param value        操作按钮
     * @param updateLayout 是否更新布局
     * @param flag         是否处理过事件
     */
    private void handleOperation(double mouseX, double mouseY, int button, RewardOperationWidget value, AtomicBoolean updateLayout, AtomicBoolean flag) {
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
                try {
                    ClientPlayerEntity player = Minecraft.getInstance().player;
                    assert player != null;
                    ERewardRule rewardRule = ERewardRule.valueOf(OperationButtonType.valueOf(value.getOperation()).name());
                    if (!player.hasPermissions(SakuraUtils.getRewardPermissionLevel(rewardRule))) {
                        Component component = SakuraComponent.get().transClient("message", "no_permission_to_view_reward", SakuraComponent.get().transClient("word", SakuraUtils.getRewardRuleI18nKeyName(rewardRule)));
                        SakuraClientNotifications.error(component, SakuraNotificationTypes.REWARD);
                    }
                    if (!player.hasPermissions(CommonConfig.get().permission().permissionEditReward())) {
                        Component component = SakuraComponent.get().transClient("message", "no_permission_to_edit_reward");
                        SakuraClientNotifications.warning(component, SakuraNotificationTypes.REWARD);
                    }
                } catch (Exception ignored) {
                }
            } else if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                // 绘制弹出层选项
                this.popupOption.clear()
                        .addOptionWithId("clear",
                                Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.clear").color(0xFFFF0000));
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
            if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                if (this.currOpButton > 200 && this.currOpButton <= 299) {
                    this.popupOption.clear();
                    this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.paste"));
                    for (ERewardType rewardType : ERewardType.values()) {
                        this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.reward_type_" + rewardType.getCode()));
                    }
                    this.popupOption.setBeforeRender(pasteConsumer);
                    this.showPopup(mouseX, mouseY, String.format("奖励面板按钮:%s", this.currOpButton));
                    flag.set(true);
                }
            }
        }
        // 帮助按钮
        else if (value.getOperation() == OperationButtonType.HELP.getCode()) {
            // 绘制弹出层提示
            this.popupOption.clear();
            this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_1"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_2"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_3"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_4"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_5"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_6"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_7"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_8"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_9"))
                    .addOption(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_description_10"));
            this.showPopup(mouseX, mouseY, "reward_rule_description");
            flag.set(true);
        }
        // 上传奖励配置
        else if (value.getOperation() == OperationButtonType.UPLOAD.getCode()) {
            // 仅管理员可上传
            if (!Minecraft.getInstance().isLocalServer()) {
                ClientPlayerEntity player = Minecraft.getInstance().player;
                if (player != null) {
                    if (player.hasPermissions(CommonConfig.get().permission().permissionEditReward())) {
                        SakuraNetwork.sendSplitToServer(RewardConfigManager.toSyncPacket(player));
                        flag.set(true);
                    }
                }
            } else {
                Component component = SakuraComponent.get().trans("message", "local_server_not_support_this_operation");
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
                    Component component = SakuraComponent.get().trans("message", "local_server_not_support_this_operation");
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
        // 打开配置文件夹
        else if (value.getOperation() == OperationButtonType.FOLDER.getCode()) {
            SystemUtils.openFileInFolder(
                    RewardConfigManager.getConfigDirectory()
                            .resolve(RewardConfigManager.FILE_NAME));
            flag.set(true);
        }
    }

    /**
     * 处理奖励按钮事件
     *
     * @param mouseX       鼠标X坐标
     * @param mouseY       鼠标Y坐标
     * @param button       鼠标按键
     * @param value        奖励按钮
     * @param updateLayout 是否更新布局
     * @param flag         是否处理过事件
     */
    private void handleRewardOption(double mouseX, double mouseY, int button, String key,
                                    RewardListEntryWidget value, AtomicBoolean updateLayout,
                                    AtomicBoolean flag) {
        LOGGER.debug("选择了奖励配置:\tButton: {}\tOperation: {}\tKey: {}\tIndex: {}", button, this.currOpButton, key, value.getOperation());

        this.currRewardButton = button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT
                && key.equalsIgnoreCase(this.currRewardButton) ? null : key;

        if (button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
            if (key.startsWith("标题")) {
                this.popupOption.clear();
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.edit"));
                }
                this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.copy"));
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.cut"));
                }
                this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.paste"));
                for (ERewardType rewardType : ERewardType.values()) {
                    this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "word.sakura_sign_in.reward_type_" + rewardType.getCode()));
                }
                this.popupOption.addOptionWithId("clear",
                        Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.clear").color(0xFFFF0000));
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOptionWithId("delete",
                            Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.delete").color(0xFFFF0000));
                }
                this.showPopup(mouseX, mouseY, String.format("奖励按钮:%s", key));
            } else {
                this.popupOption.clear();
                this.popupOption.addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.edit"))
                        .addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.copy"))
                        .addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.cut"))
                        .addOption(Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.paste"))
                        .addOptionWithId("delete",
                                Text.trans(SakuraSignIn.MODID, "option.sakura_sign_in.delete").color(0xFFFF0000));
                this.showPopup(mouseX, mouseY, String.format("奖励按钮:%s", key));
            }
            this.popupOption.setBeforeRender(pasteConsumer);
            flag.set(true);
        }
    }

    private void showPopup(double mouseX, double mouseY, String contextId) {
        this.popupContextId = contextId;
        this.popupOption.onSelect(this::handlePopupSelection).showAt(mouseX, mouseY, contextId);
    }

    private void handlePopupSelection(PopupOption.SelectEvent event) {
        if (event.button() != GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        AtomicBoolean updateLayout = new AtomicBoolean(false);
        AtomicBoolean handled = new AtomicBoolean(false);
        this.handlePopupOption(event.button(), popupContextId, event.index(), event.text(), updateLayout, handled);
        if (updateLayout.get()) {
            this.updateLayout();
        }
    }

    /**
     * 处理弹出层选项
     *
     * @param button       鼠标按键
     * @param updateLayout 是否更新布局
     * @param flag         是否处理过事件
     */
    private void handlePopupOption(int button, String popupId, int selectedIndex, String selectedString,
                                   AtomicBoolean updateLayout, AtomicBoolean flag) {
        LOGGER.debug("选择了弹出选项:\tButton: {}\tId: {}\tIndex: {}\tContent: {}",
                button, popupId, selectedIndex, selectedString);
        OperationButtonType buttonType = OperationButtonType.valueOf(currOpButton);
        if (buttonType == null) return;
        ERewardRule rule = ERewardRule.valueOf(buttonType.toString());
        if (popupId.startsWith("奖励规则类型按钮:")) {
            int opCode = StringUtils.toInt(popupId.replace("奖励规则类型按钮:", ""));
            if (selectedIndex == 0 && opCode > 200 && opCode <= 299) {
                requestConfirmation("confirm_clear_reward_rule",
                        () -> clearRewardRule(opCode, rule));
                flag.set(true);
            }
        } else if (popupId.startsWith("奖励面板按钮:")) {
            String[] key = new String[]{""};
            if (SakuraComponent.get().transClient("option", "paste").toString().equalsIgnoreCase(selectedString)) {
                editHandler.handlePaste();
            }
            // 物品
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.ITEM.getCode()).equalsIgnoreCase(selectedString)) {
                Screen callbackScreen = ItemRewardSelectionFlow.create(this,
                        new Reward(new ItemStack(Items.AIR), ERewardType.ITEM),
                        () -> StringUtils.isNullOrEmpty(key[0]),
                        input -> {
                    if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        RewardConfigManager.addReward(rule, key[0], input);
                        RewardConfigManager.saveRewardOption();
                    }
                });
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 药水效果
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.EFFECT.getCode()).equalsIgnoreCase(selectedString)) {
                Screen callbackScreen = EffectRewardSelectionFlow.create(this,
                        new Reward(new EffectInstance(Effects.LUCK), ERewardType.EFFECT),
                        () -> StringUtils.isNullOrEmpty(key[0]),
                        input -> {
                    if (input != null && ((EffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0 && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        RewardConfigManager.addReward(rule, key[0], input);
                        RewardConfigManager.saveRewardOption();
                    }
                });
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 经验点
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.EXP_POINT.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_exp_point").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                        , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        int count = StringUtils.toInt(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        if (count != 0) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, ERewardType.EXP_POINT), ERewardType.EXP_POINT, p));
                            RewardConfigManager.saveRewardOption();
                        } else {
                            result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                        }
                    }
                    return result;
                }, () -> StringUtils.isNullOrEmpty(key[0]));
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 经验等级
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.EXP_LEVEL.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_exp_level").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                        , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        int count = StringUtils.toInt(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        if (count != 0) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, ERewardType.EXP_LEVEL), ERewardType.EXP_LEVEL, p));
                            RewardConfigManager.saveRewardOption();
                        } else {
                            result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                        }
                    }
                    return result;
                }, () -> StringUtils.isNullOrEmpty(key[0]));
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 补签卡
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.SIGN_IN_CARD.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_sign_in_card").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                        , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        int count = StringUtils.toInt(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        if (count != 0) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, ERewardType.SIGN_IN_CARD), ERewardType.SIGN_IN_CARD, p));
                            RewardConfigManager.saveRewardOption();
                        } else {
                            result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                        }
                    }
                    return result;
                }, () -> StringUtils.isNullOrEmpty(key[0]));
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 进度
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.ADVANCEMENT.getCode()).equalsIgnoreCase(selectedString)) {
                Screen callbackScreen = AdvancementRewardSelectionFlow.create(this,
                        new Reward(new ResourceLocation(""), ERewardType.ADVANCEMENT),
                        () -> StringUtils.isNullOrEmpty(key[0]),
                        input -> {
                    if (input != null && StringUtils.isNotNullOrEmpty(input.toString()) && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        RewardConfigManager.addReward(rule, key[0], input);
                        RewardConfigManager.saveRewardOption();
                    }
                });
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 消息
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.MESSAGE.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_message").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                        , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("", "1")
                        , input -> {
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        Component component = SakuraComponent.get().literal(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(component, ERewardType.MESSAGE), ERewardType.MESSAGE, p));
                        RewardConfigManager.saveRewardOption();
                    }
                }, () -> StringUtils.isNullOrEmpty(key[0]));
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 指令
            else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.COMMAND.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_command").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                        , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                        , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("", "1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && input.get(0).startsWith("/") && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(input.get(0), ERewardType.COMMAND), ERewardType.COMMAND, p));
                        RewardConfigManager.saveRewardOption();
                    } else {
                        result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                    }
                    return result;
                }, () -> StringUtils.isNullOrEmpty(key[0]));
                if (rule == ERewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != ERewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 实现其他奖励类型
        } else if (popupId.startsWith("奖励按钮:")) {
            String id = popupId.replace("奖励按钮:", "");
            if (id.startsWith("标题")) {
                String key = id.substring(3);
                if (SakuraComponent.get().transClient("option", "edit").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        if (rule == ERewardRule.CDK_REWARD) {
                            String[] split = key.split("\\|");
                            if (split.length != 4 && split.length != 3 && split.length != 2)
                                split = new String[]{"", DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1)), "-1", "1"};
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()).shadow(true)
                                    , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_valid_until").shadow(true)
                                    , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_num").shadow(true))
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                                    , new StringList("\\w*", "", "\\d*")
                                    , new StringList(split[0], split[1], split[3])
                                    , input -> {
                                StringList result = new StringList("", "", "");
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    if (!RewardConfigManager.validateKeyName(rule, input.get(0))) {
                                        result.set(0, SakuraComponent.get().transClient("tips", "reward_rule_s_error", input.get(0)).toString());
                                    }
                                    if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                                        if (DateUtils.format(input.get(1)) == null) {
                                            result.set(1, SakuraComponent.get().transClient("tips", "valid_until_s_error", input.get(1)).toString());
                                        }
                                    }
                                    if (StringUtils.isNotNullOrEmpty(input.get(2))) {
                                        if (StringUtils.toInt(input.get(2)) == 0) {
                                            result.set(2, SakuraComponent.get().transClient("tips", "num_s_error", input.get(2)).toString());
                                        }
                                    }
                                    if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateKeyName(rule, key, String.format("%s|%s|-1|%d", input.get(0), input.get(1), StringUtils.toInt(input.get(2), 1)));
                                        RewardConfigManager.saveRewardOption();
                                    }
                                }
                                return result;
                            }));
                        } else {
                            String validator = rule == ERewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
                            Minecraft.getInstance().setScreen(new StringInputScreen(this, Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()).shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"), validator, key, input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    if (RewardConfigManager.validateKeyName(rule, input.get(0))) {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateKeyName(rule, key, input.get(0));
                                        RewardConfigManager.saveRewardOption();
                                    } else {
                                        result.add(SakuraComponent.get().transClient("tips", "reward_rule_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }));
                        }
                    }
                } else if (SakuraComponent.get().transClient("option", "copy").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCopy();
                    }
                } else if (SakuraComponent.get().transClient("option", "cut").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCut();
                    }
                } else if (SakuraComponent.get().transClient("option", "paste").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handlePaste();
                    }
                } else if (SakuraComponent.get().transClient("option", "clear").toString().equalsIgnoreCase(selectedString)) {
                    requestConfirmation("confirm_clear_reward_group", () -> {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        RewardConfigManager.clearKey(rule, key);
                        RewardConfigManager.saveRewardOption();
                        updateLayout();
                    });
                } else if (SakuraComponent.get().transClient("option", "delete").toString().equalsIgnoreCase(selectedString)) {
                    requestDeleteConfirmation();
                }
                // 添加物品
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.ITEM.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(ItemRewardSelectionFlow.create(
                            this,
                            new Reward(new ItemStack(Items.AIR), ERewardType.ITEM),
                            input -> {
                        if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key, input);
                            RewardConfigManager.saveRewardOption();
                        }
                    }));
                }
                // 药水效果
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.EFFECT.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(EffectRewardSelectionFlow.create(
                            this,
                            new Reward(new EffectInstance(Effects.LUCK), ERewardType.EFFECT),
                            input -> {
                        if (input != null && ((EffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key, input);
                            RewardConfigManager.saveRewardOption();
                        }
                    }));
                }
                // 经验点
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.EXP_POINT.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_exp_point").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                            , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            int count = StringUtils.toInt(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            if (count != 0) {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, ERewardType.EXP_POINT), ERewardType.EXP_POINT, p));
                                RewardConfigManager.saveRewardOption();
                            } else {
                                result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                            }
                        }
                        return result;
                    }));
                }
                // 经验等级
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.EXP_LEVEL.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_exp_level").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                            , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            int count = StringUtils.toInt(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            if (count != 0) {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, ERewardType.EXP_LEVEL), ERewardType.EXP_LEVEL, p));
                                RewardConfigManager.saveRewardOption();
                            } else {
                                result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                            }
                        }
                        return result;
                    }));
                }
                // 补签卡
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.SIGN_IN_CARD.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_sign_in_card").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                            , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            int count = StringUtils.toInt(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            if (count != 0) {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, ERewardType.SIGN_IN_CARD), ERewardType.SIGN_IN_CARD, p));
                                RewardConfigManager.saveRewardOption();
                            } else {
                                result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                            }
                        }
                        return result;
                    }));
                }
                // 进度
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.ADVANCEMENT.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(AdvancementRewardSelectionFlow.create(
                            this,
                            new Reward(new ResourceLocation(""), ERewardType.ADVANCEMENT),
                            input -> {
                        if (input != null && StringUtils.isNotNullOrEmpty(((ResourceLocation) RewardManager.deserializeReward(input)).toString())) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key, input);
                            RewardConfigManager.saveRewardOption();
                        }
                    }));

                }
                // 消息
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.MESSAGE.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_message").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                            , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("", "1")
                            , input -> {
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            Component component = SakuraComponent.get().literal(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(component, ERewardType.MESSAGE), ERewardType.MESSAGE, p));
                            RewardConfigManager.saveRewardOption();
                        }
                    }));
                }
                // 指令
                else if (SakuraComponent.get().translateClient("word", "reward_type_" + ERewardType.COMMAND.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_command").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                            , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                            , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("", "1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input) && input.get(0).startsWith("/")) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(input.get(0), ERewardType.COMMAND), ERewardType.COMMAND, p));
                            RewardConfigManager.saveRewardOption();
                        } else {
                            result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                        }
                        return result;
                    }));
                }
                // 实现其他奖励类型
            } else {
                String[] split = id.split(",");
                if (split.length != 2) {
                    LOGGER.error("Invalid popup option id: {}", id);
                    return;
                }
                String key = split[0];
                String index = split[1];
                if (SakuraComponent.get().transClient("option", "edit").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        Reward reward = RewardConfigManager.getReward(rule, key, Integer.parseInt(index)).clone();
                        if (reward.getType() == ERewardType.ITEM) {
                            Minecraft.getInstance().setScreen(ItemRewardSelectionFlow.create(
                                    this,
                                    reward,
                                    input -> {
                                if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), input);
                                    RewardConfigManager.saveRewardOption();
                                }
                            }));
                        }
                        // 药水效果
                        else if (reward.getType() == ERewardType.EFFECT) {
                            Minecraft.getInstance().setScreen(EffectRewardSelectionFlow.create(
                                    this,
                                    reward,
                                    input -> {
                                if (input != null && ((EffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), input);
                                    RewardConfigManager.saveRewardOption();
                                }
                            }));
                        }
                        // 经验点
                        else if (reward.getType() == ERewardType.EXP_POINT) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_exp_point").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                                    , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(String.valueOf((Integer) RewardManager.deserializeReward(reward)), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    int count = StringUtils.toInt(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    if (count != 0) {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, ERewardType.EXP_POINT), ERewardType.EXP_POINT, p));
                                        RewardConfigManager.saveRewardOption();
                                    } else {
                                        result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }
                            ));
                        }
                        // 经验等级
                        else if (reward.getType() == ERewardType.EXP_LEVEL) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_exp_level").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                                    , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(String.valueOf((Integer) RewardManager.deserializeReward(reward)), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    int count = StringUtils.toInt(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    if (count != 0) {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, ERewardType.EXP_LEVEL), ERewardType.EXP_LEVEL, p));
                                        RewardConfigManager.saveRewardOption();
                                    } else {
                                        result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }
                            ));
                        }
                        // 补签卡
                        else if (reward.getType() == ERewardType.SIGN_IN_CARD) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_sign_in_card").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                                    , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(String.valueOf((Integer) RewardManager.deserializeReward(reward)), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    int count = StringUtils.toInt(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    if (count != 0) {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, ERewardType.SIGN_IN_CARD), ERewardType.SIGN_IN_CARD, p));
                                        RewardConfigManager.saveRewardOption();
                                    } else {
                                        result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }
                            ));
                        }
                        // 进度
                        else if (reward.getType() == ERewardType.ADVANCEMENT) {
                            Minecraft.getInstance().setScreen(AdvancementRewardSelectionFlow.create(
                                    this,
                                    reward,
                                    input -> {
                                if (input != null && StringUtils.isNotNullOrEmpty(((ResourceLocation) RewardManager.deserializeReward(input)).toString()) && StringUtils.isNotNullOrEmpty(key)) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), input);
                                    RewardConfigManager.saveRewardOption();
                                }
                            }));
                        }
                        // 消息
                        else if (reward.getType() == ERewardType.MESSAGE) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_message").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                                    , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(RewardManager.deserializeReward(reward).toString(), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    Component textToComponent = SakuraComponent.get().literal(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(textToComponent, ERewardType.MESSAGE), ERewardType.MESSAGE, p));
                                    RewardConfigManager.saveRewardOption();
                                }
                            }
                            ));
                        }
                        // 指令
                        else if (reward.getType() == ERewardType.COMMAND) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_command").shadow(true), Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_probability").shadow(true))
                                    , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                                    , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(RewardManager.deserializeReward(reward), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input) && input.get(0).startsWith("/")) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(input.get(0), ERewardType.COMMAND), ERewardType.COMMAND, p));
                                    RewardConfigManager.saveRewardOption();
                                } else {
                                    result.add(SakuraComponent.get().transClient("tips", "enter_value_s_error", input.get(0)).toString());
                                }
                                return result;
                            }
                            ));
                        }
                    }
                } else if (SakuraComponent.get().transClient("option", "copy").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCopy();
                    }
                } else if (SakuraComponent.get().transClient("option", "cut").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCut();
                    }
                } else if (SakuraComponent.get().transClient("option", "paste").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handlePaste();
                    }
                } else if (SakuraComponent.get().transClient("option", "delete").toString().equalsIgnoreCase(selectedString)) {
                    requestDeleteConfirmation();
                }
            }
            updateLayout.set(true);
            flag.set(true);
        }
    }

    /**
     * 危险操作统一通过可见确认页执行，不再依赖组合键或特殊鼠标键。
     */
    private void requestConfirmation(String messageKey, Runnable action) {
        Minecraft.getInstance().setScreen(new ConfirmDialogScreen(
                new ConfirmDialogScreen.Args()
                        .parentScreen(this)
                        .title(SakuraComponent.get().transClient("title", "confirm_operation"))
                        .message(SakuraComponent.get().transClient("tips", messageKey))
                        .onConfirm(action)
        ));
    }

    private boolean requestDeleteConfirmation() {
        if (StringUtils.isNullOrEmptyEx(currRewardButton)
                || currRewardButton.equalsIgnoreCase("panel")) {
            return false;
        }
        requestConfirmation(
                currRewardButton.startsWith("标题")
                        ? "confirm_delete_reward_group"
                        : "confirm_delete_reward",
                editHandler::handleDelete
        );
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
    private Consumer<RewardOperationWidget.RenderContext> generateCustomRenderFunction(String content) {
        return context -> {
            RewardOperationWidget widget = context.getWidget();
            MatrixStack stack = context.getStack();
            int realX = (int) widget.realX();
            int realY = (int) widget.realY();
            double realWidth = widget.realWidth();
            double realHeight = widget.realHeight();
            int realX2 = (int) (widget.realX() + realWidth);
            int realY2 = (int) (widget.realY() + realHeight);
            if (this.currOpButton == widget.getOperation()) {
                AbstractGui.fill(stack, realX + 1, realY, realX2 - 1, realY2, 0x44ACACAC);
            }
            if (widget.hovered()) {
                AbstractGui.fill(stack, realX, realY, realX2, realY2, 0x99ACACAC);
            }
            AbstractGuiUtils.drawLimitedText(stack, super.font,
                    SakuraComponent.get().transClient("word", content).toString(),
                    realX + 4, (int) (realY + (realHeight - super.font.lineHeight) / 2),
                    (int) (realWidth - 22), 0xFFEBD4B1);
        };
    }

    private void updateLayout() {
        this.leftBarWidth = SakuraClientState.isRewardOptionBarOpened() ? 100 : 20;
        this.lineItemCount = Math.max(1,
                (super.width - leftBarWidth - leftMargin - rightMargin - rightBarWidth)
                        / (itemIconSize + itemRightMargin));
        // 重置奖励面板坐标
        OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode()).bounds(
                new ScreenCoordinate(leftBarWidth, 0,
                        super.width - leftBarWidth - rightBarWidth, super.height));
        // 清空弹出层选项
        popupOption.clear();
        // 更新奖励面板列表内容
        this.updateRewardList();
    }

    private void setYOffset(double offset) {
        // y坐标往上(-)不应该超过奖励高度+屏幕高度, 往下(+)不应该超过屏幕高度
        this.yOffset = Math.min(Math.max(offset, -(this.topMargin + (double) this.rewardListIndex.get() / this.lineItemCount * (this.itemIconSize + this.itemBottomMargin) + super.height)), super.height);
    }

    @Data
    class EditCommandHandler {
        private final Screen screen;

        private ERewardRule rule;
        private String key;
        private String index;

        /**
         * 更新参数
         *
         * @return 是否更新失败
         */
        private boolean update() {
            if (StringUtils.isNullOrEmptyEx(currRewardButton)) return true;

            OperationButtonType buttonType = OperationButtonType.valueOf(currOpButton);
            if (buttonType == null) return true;
            rule = ERewardRule.valueOf(buttonType.toString());

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
            if (update()) return false;

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
            if (update()) return false;

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
            if (update()) return false;

            if (RewardClipboardManager.isClipboardValid()) {
                // 面板
                if (currRewardButton.equalsIgnoreCase("panel")) {
                    if (rule == ERewardRule.CDK_REWARD) {
                        Minecraft.getInstance().setScreen(new StringInputScreen(screen
                                , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()).shadow(true)
                                , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_valid_until").shadow(true)
                                , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_num").shadow(true))
                                , new TextList(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something"))
                                , new StringList("\\w*"
                                , ""
                                , "\\d*")
                                , new StringList(RewardConfigManager.getCdkRewardKey(RewardClipboardManager.deSerializeRewardList().getKey())
                                , DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1))
                                , RewardConfigManager.getCdkRewardNum(RewardClipboardManager.deSerializeRewardList().getKey()) + "")
                                , input -> {
                            StringList result = new StringList("", "", "");
                            if (CollectionUtils.isNotNullOrEmpty(input)) {
                                if (!RewardConfigManager.validateKeyName(rule, input.get(0))) {
                                    result.set(0, SakuraComponent.get().transClient("tips", "reward_rule_s_error", input.get(0)).toString());
                                }
                                if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                                    if (DateUtils.format(input.get(1)) == null) {
                                        result.set(1, SakuraComponent.get().transClient("tips", "valid_until_s_error", input.get(1)).toString());
                                    }
                                }
                                if (StringUtils.isNotNullOrEmpty(input.get(2))) {
                                    if (StringUtils.toInt(input.get(2)) == 0) {
                                        result.set(2, SakuraComponent.get().transClient("tips", "num_s_error", input.get(2)).toString());
                                    }
                                }
                                if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                                    RewardConfigManager.addKeyName(rule, String.format("%s|%s|-1|%d", input.get(0), input.get(1), StringUtils.toInt(input.get(2), 1)), rewardList);
                                    RewardConfigManager.saveRewardOption();
                                }
                            }
                            return result;
                        }));
                    } else if (rule != ERewardRule.BASE_REWARD) {
                        String validator = rule == ERewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
                        Minecraft.getInstance().setScreen(new StringInputScreen(screen
                                , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_reward_rule_key_" + rule.getCode()).shadow(true)
                                , Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.enter_something")
                                , validator
                                , RewardClipboardManager.deSerializeRewardList().getKey()
                                , input -> {
                            StringList result = new StringList();
                            if (CollectionUtils.isNotNullOrEmpty(input)) {
                                if (RewardConfigManager.validateKeyName(rule, input.get(0))) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                                    RewardConfigManager.addKeyName(rule, input.get(0), rewardList);
                                    RewardConfigManager.saveRewardOption();
                                } else {
                                    result.add(SakuraComponent.get().transClient("tips", "reward_rule_s_error", input.get(0)).toString());
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

        public boolean handleDelete() {
            if (update()) return false;

            // 面板
            if (currRewardButton.equalsIgnoreCase("panel")) {
                return false;
            }
            // 标题
            else if (currRewardButton.startsWith("标题")) {
                RewardConfigManager.addUndoRewardOption(rule);
                RewardConfigManager.clearRedoList();
                RewardConfigManager.deleteKey(rule, key);
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
         * 撤销
         */
        public boolean handleUndo() {
            if (update()) return false;
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
            if (update()) return false;

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
        super(SakuraComponent.get().transClient("title", "reward_option_title"));
        season(BaniraThemes.seasonFor(SakuraSignIn.MODID));
    }

    @Override
    protected void onInit() {
        this.leftBarTitleHeight = 5 * 2 + super.font.lineHeight;
        ClientEventHandler.loadThemeTexture();
        tips = Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_option_screen_tips");
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
                .setDragHandler(event -> setYOffset(yOffset + event.dragY()));

        registerOperation(createThemeIcon(OperationButtonType.OPEN,
                        SakuraClientState.getThemeTextureCoordinate().getArrowUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowHoverUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowTapUV())
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.open_sidebar")),
                new ScreenCoordinate(4, (height - 16) / 2.0, 16, 16));
        registerOperation(createThemeIcon(OperationButtonType.CLOSE,
                        SakuraClientState.getThemeTextureCoordinate().getArrowUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowHoverUV(),
                        SakuraClientState.getThemeTextureCoordinate().getArrowTapUV())
                        .setFlipHorizontal(true)
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.close_sidebar")),
                new ScreenCoordinate(80, (leftBarTitleHeight - 16) / 2.0, 16, 16));

        ERewardRule[] rules = {
                ERewardRule.BASE_REWARD, ERewardRule.CONTINUOUS_REWARD, ERewardRule.CYCLE_REWARD,
                ERewardRule.YEAR_REWARD, ERewardRule.MONTH_REWARD, ERewardRule.WEEK_REWARD,
                ERewardRule.DATE_TIME_REWARD, ERewardRule.CUMULATIVE_REWARD,
                ERewardRule.RANDOM_REWARD, ERewardRule.CDK_REWARD
        };
        for (int i = 0; i < rules.length; i++) {
            OperationButtonType type = OperationButtonType.valueOf(rules[i].name());
            registerOperation(new RewardOperationWidget(this, type.getCode(),
                            generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(rules[i]))),
                    new ScreenCoordinate(0, leftBarTitleHeight + (leftBarTitleHeight - 1) * i,
                            100, leftBarTitleHeight - 2));
        }

        registerOperation(new RewardOperationWidget(this, OperationButtonType.OFFSET_Y.getCode(), context -> {
            AbstractGuiUtils.drawString(context.getStack(), font, "OY:",
                    width - rightBarWidth + 1, height - font.lineHeight * 2 - 2, 0xFFACACAC);
            AbstractGuiUtils.drawLimitedText(context.getStack(), font, String.valueOf((int) yOffset),
                    width - rightBarWidth + 1, height - font.lineHeight - 2,
                    rightBarWidth, 0xFFACACAC);
        }), new ScreenCoordinate(width - rightBarWidth, height - font.lineHeight * 2 - 2,
                rightBarWidth, font.lineHeight * 2 + 2));

        registerOperation(createThemeIcon(OperationButtonType.HELP,
                        SakuraClientState.getThemeTextureCoordinate().getHelpUV()),
                new ScreenCoordinate(width - rightBarWidth + 1, 2, 18, 18));
        registerOperation(createThemeIcon(OperationButtonType.DOWNLOAD,
                        SakuraClientState.getThemeTextureCoordinate().getDownloadUV())
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.download_reward_config")),
                new ScreenCoordinate(width - rightBarWidth + 1, 22, 18, 18));
        registerOperation(createThemeIcon(OperationButtonType.UPLOAD,
                        SakuraClientState.getThemeTextureCoordinate().getUploadUV()),
                new ScreenCoordinate(width - rightBarWidth + 1, 42, 18, 18));
        registerOperation(createThemeIcon(OperationButtonType.FOLDER,
                        SakuraClientState.getThemeTextureCoordinate().getFolderUV())
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.open_config_folder")),
                new ScreenCoordinate(width - rightBarWidth + 1, 62, 18, 18));
        registerOperation(createThemeIcon(OperationButtonType.SORT,
                        SakuraClientState.getThemeTextureCoordinate().getSortUV())
                        .setTooltip(Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.reward_rule_sort")),
                new ScreenCoordinate(width - rightBarWidth + 1, 82, 18, 18));
        updateLayout();
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
            handleOperation(event.mouseX(), event.mouseY(), event.button(), widget, updateLayout, handled);
            if (handled.get()) {
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
    protected void onRender(MatrixStack matrixStack, float partialTicks) {
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
        if (OperationButtonType.valueOf(currOpButton) == null) {
            AbstractGuiUtils.fill(matrixStack, this.leftBarWidth + 4, 4, super.width - this.leftBarWidth - this.rightBarWidth - 8, super.height - 8, 0x88000000, 15);
            float x, y;
            tips.stack(matrixStack).font(super.font);
            int textHeight = AbstractGuiUtils.multilineTextHeight(tips);
            int textWidth = AbstractGuiUtils.multilineTextWidth(tips);
            x = this.leftBarWidth + ((super.width - this.leftBarWidth - this.rightBarWidth) - textWidth) / 2.0f;
            y = (super.height - (textHeight + 4)) / 2.0f;
            AbstractGuiUtils.drawString(tips, x, y);
        }
        else this.prepareRewardList();

        // 绘制左侧边栏列表背景
        AbstractGui.fill(matrixStack, 0, 0, leftBarWidth, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(matrixStack, 0, 0, leftBarWidth, super.height, 1, 0xFF000000);
        // 绘制左侧边栏列表标题
        if (SakuraClientState.isRewardOptionBarOpened()) {
            AbstractGui.drawString(matrixStack, super.font, SakuraComponent.get().transClient("title", "reward_rule_type").toString(), 4, 5, 0xFFACACAC);
            AbstractGui.fill(matrixStack, 0, leftBarTitleHeight, leftBarWidth, leftBarTitleHeight - 1, 0xAA000000);
        }
        // 绘制右侧边栏列表背景
        AbstractGui.fill(matrixStack, super.width - rightBarWidth, 0, super.width, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(matrixStack, super.width - rightBarWidth, 0, rightBarWidth, super.height, 1, 0xFF000000);

        updateOperationPresentation();
        renderWidgets(matrixStack, partialTicks);
        addDeferredTooltipRender(stack -> {
            // 弹出菜单是当前交互焦点，避免下层奖励或工具提示穿透到菜单上方。
            if (!popupOption.isEmpty()) {
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
                Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in.y_offset",
                        StringUtils.toFixedEx(yOffset, 1)));
        OP_BUTTONS.get(OperationButtonType.HELP.getCode()).setTooltip(
                Text.trans(SakuraSignIn.MODID, "tips.sakura_sign_in."
                        + (inputState.onlyShiftPressed() ? "help_button_shift" : "help_button")));

        ClientPlayerEntity player = Minecraft.getInstance().player;
        RewardOperationWidget upload = OP_BUTTONS.get(OperationButtonType.UPLOAD.getCode());
        if (player != null && player.hasPermissions(CommonConfig.get().permission().permissionEditReward())) {
            upload.setTooltip(Text.trans(SakuraSignIn.MODID,
                            "tips.sakura_sign_in.upload_reward_config"))
                    .setPressedTint(0xAAA0A0A0);
        } else {
            upload.setTooltip(Text.trans(SakuraSignIn.MODID,
                            "tips.sakura_sign_in.upload_reward_config_no_permission").color(0xFFFF0000))
                    .setPressedTint(0xAA808080);
        }
    }

    @Override
    protected void onMouseScrolled(MouseScrolledHandleArgs eventArgs) {
        RewardOperationWidget panel = OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode());
        if (panel != null && panel.isMouseInside(eventArgs.mouseX(), eventArgs.mouseY())) {
            setYOffset(yOffset + eventArgs.delta());
            eventArgs.consumed(true);
        }
    }

    @Override
    protected void onKeyPressed(KeyPressedHandleArgs eventArgs) {
        if (eventArgs.keyCode() == GLFWKey.GLFW_KEY_ESCAPE) {
            onClose();
            eventArgs.consumed(true);
        }
    }

    @Override
    protected void onKeyReleased(KeyReleasedHandleArgs eventArgs) {
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
                InputStateManager.isKeyPressing(GLFWKey.GLFW_KEY_LEFT_CONTROL)
                        ? GLFWKey.GLFW_KEY_LEFT_CONTROL : GLFWKey.GLFW_KEY_UNKNOWN,
                InputStateManager.isKeyPressing(GLFWKey.GLFW_KEY_RIGHT_CONTROL)
                        ? GLFWKey.GLFW_KEY_RIGHT_CONTROL : GLFWKey.GLFW_KEY_UNKNOWN,
                InputStateManager.isKeyPressing(GLFWKey.GLFW_KEY_LEFT_SHIFT)
                        ? GLFWKey.GLFW_KEY_LEFT_SHIFT : GLFWKey.GLFW_KEY_UNKNOWN,
                InputStateManager.isKeyPressing(GLFWKey.GLFW_KEY_RIGHT_SHIFT)
                        ? GLFWKey.GLFW_KEY_RIGHT_SHIFT : GLFWKey.GLFW_KEY_UNKNOWN,
                InputStateManager.isKeyPressing(GLFWKey.GLFW_KEY_LEFT_ALT)
                        ? GLFWKey.GLFW_KEY_LEFT_ALT : GLFWKey.GLFW_KEY_UNKNOWN,
                InputStateManager.isKeyPressing(GLFWKey.GLFW_KEY_RIGHT_ALT)
                        ? GLFWKey.GLFW_KEY_RIGHT_ALT : GLFWKey.GLFW_KEY_UNKNOWN
        };
        int[] pressed = Arrays.stream(keys)
                .filter(key -> key != GLFWKey.GLFW_KEY_UNKNOWN)
                .distinct()
                .toArray();
        return bindings.stream().anyMatch(binding -> GLFWKeyHelper.matchKey(binding, pressed));
    }

}
