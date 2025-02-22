package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.*;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.event.ClientEventHandler;
import xin.vanilla.sakura.network.DownloadRewardOptionNotice;
import xin.vanilla.sakura.network.ModNetworkHandler;
import xin.vanilla.sakura.network.RewardOptionSyncPacket;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardList;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.component.*;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.util.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


@OnlyIn(Dist.CLIENT)
public class RewardOptionScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 父级 Screen
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    private Screen previousScreen;

    private Text tips;

    /**
     * 当前按下的按键
     */
    private int keyCode = -1;
    /**
     * 按键的组合键 Shift 1, Ctrl 2, Alt 4
     */
    private int modifiers = -1;

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

    /**
     * 弹出层选项
     */
    private PopupOption popupOption;

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
    private GuiGraphics gg;
    /**
     * 奖励列表索引(用于计算渲染Y坐标)
     */
    AtomicInteger rewardListIndex = new AtomicInteger(0);
    // Y坐标偏移
    private double yOffset, yOffsetOld;
    // 鼠标按下时的X坐标
    private double mouseDownX = -1;
    // 鼠标按下时的Y坐标
    private double mouseDownY = -1;
    // endregion 奖励列表相关参数

    /**
     * 鼠标光标
     */
    private MouseCursor cursor;
    /**
     * 当前选中的操作按钮
     */
    private int currOpButton;

    /**
     * 操作按钮集合
     */
    private final Map<Integer, OperationButton> OP_BUTTONS = new HashMap<>();

    /**
     * 奖励列表按钮集合
     */
    private final Map<String, OperationButton> REWARD_BUTTONS = new HashMap<>();

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
    private void renderBackgroundTexture(GuiGraphics graphics) {
        // 启用混合模式以支持透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        // 绑定背景纹理
        AbstractGuiUtils.bindTexture(SakuraSignIn.getThemeTexture());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // 获取屏幕宽高
        int screenWidth = super.width;
        int screenHeight = super.height;

        // 获取纹理指定区域的坐标和大小
        float u0 = (float) SakuraSignIn.getThemeTextureCoordinate().getOptionBgUV().getU0();
        float v0 = (float) SakuraSignIn.getThemeTextureCoordinate().getOptionBgUV().getV0();
        float regionWidth = (float) SakuraSignIn.getThemeTextureCoordinate().getOptionBgUV().getUWidth();
        float regionHeight = (float) SakuraSignIn.getThemeTextureCoordinate().getOptionBgUV().getVHeight();
        if (regionWidth == 0) regionWidth = screenWidth;
        if (regionHeight == 0) regionHeight = screenHeight;
        int textureTotalWidth = SakuraSignIn.getThemeTextureCoordinate().getTotalWidth();
        int textureTotalHeight = SakuraSignIn.getThemeTextureCoordinate().getTotalHeight();

        // 计算UV比例
        float uMin = u0 / textureTotalWidth;
        float vMin = v0 / textureTotalHeight;
        float uMax = (u0 + regionWidth) / textureTotalWidth;
        float vMax = (v0 + regionHeight) / textureTotalHeight;

        // 使用Tessellator绘制平铺的纹理片段
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // 绘制完整的纹理块
        for (int x = 0; x <= screenWidth - regionWidth; x += (int) regionWidth) {
            for (int y = 0; y <= screenHeight - regionHeight; y += (int) regionHeight) {
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                buffer.vertex(graphics.pose().last().pose(), x, y + regionHeight, 0).uv(uMin, vMax).endVertex();
                buffer.vertex(graphics.pose().last().pose(), x + regionWidth, y + regionHeight, 0).uv(uMax, vMax).endVertex();
                buffer.vertex(graphics.pose().last().pose(), x + regionWidth, y, 0).uv(uMax, vMin).endVertex();
                buffer.vertex(graphics.pose().last().pose(), x, y, 0).uv(uMin, vMin).endVertex();
                tesselator.end();
            }
        }

        // 绘制剩余的竖条（右边缘）
        float leftoverWidth = screenWidth % regionWidth;
        float u = uMin + (leftoverWidth / regionWidth) * (uMax - uMin);
        if (leftoverWidth > 0) {
            for (int y = 0; y <= screenHeight - regionHeight; y += (int) regionHeight) {
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                buffer.vertex(graphics.pose().last().pose(), screenWidth - leftoverWidth, y + regionHeight, 0).uv(uMin, vMax).endVertex();
                buffer.vertex(graphics.pose().last().pose(), screenWidth, y + regionHeight, 0).uv(u, vMax).endVertex();
                buffer.vertex(graphics.pose().last().pose(), screenWidth, y, 0).uv(u, vMin).endVertex();
                buffer.vertex(graphics.pose().last().pose(), screenWidth - leftoverWidth, y, 0).uv(uMin, vMin).endVertex();
                tesselator.end();
            }
        }

        // 绘制剩余的横条（底边缘）
        float leftoverHeight = screenHeight % regionHeight;
        float v = vMin + (leftoverHeight / regionHeight) * (vMax - vMin);
        if (leftoverHeight > 0) {
            for (int x = 0; x <= screenWidth - regionWidth; x += (int) regionWidth) {
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                buffer.vertex(graphics.pose().last().pose(), x, screenHeight, 0).uv(uMin, v).endVertex();
                buffer.vertex(graphics.pose().last().pose(), x + regionWidth, screenHeight, 0).uv(uMax, v).endVertex();
                buffer.vertex(graphics.pose().last().pose(), x + regionWidth, screenHeight - leftoverHeight, 0).uv(uMax, vMin).endVertex();
                buffer.vertex(graphics.pose().last().pose(), x, screenHeight - leftoverHeight, 0).uv(uMin, vMin).endVertex();
                tesselator.end();
            }
        }

        // 绘制右下角的剩余区域
        if (leftoverWidth > 0 && leftoverHeight > 0) {
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(graphics.pose().last().pose(), screenWidth - leftoverWidth, screenHeight, 0).uv(uMin, v).endVertex();
            buffer.vertex(graphics.pose().last().pose(), screenWidth, screenHeight, 0).uv(u, v).endVertex();
            buffer.vertex(graphics.pose().last().pose(), screenWidth, screenHeight - leftoverHeight, 0).uv(u, vMin).endVertex();
            buffer.vertex(graphics.pose().last().pose(), screenWidth - leftoverWidth, screenHeight - leftoverHeight, 0).uv(uMin, vMin).endVertex();
            tesselator.end();
        }

        RenderSystem.enableDepthTest();
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
        REWARD_BUTTONS.put(String.format("标题,%s", key), new OperationButton(titleIndex, context -> {
            if (context.button().getRealY() < super.height && context.button().getRealY() + context.button().getRealHeight() >= 0) {
                context.graphics().fill((int) context.button().getRealX(), (int) (context.button().getRealY()), (int) (context.button().getRealX() + context.button().getRealWidth()), (int) (context.button().getRealY() + 1), 0xAC000000);
                AbstractGuiUtils.drawLimitedText(this.gg, super.font, title, (int) context.button().getRealX(), (int) (context.button().getRealY() + (context.button().getRealHeight() - super.font.lineHeight) / 2), (int) context.button().getRealWidth(), 0xAC000000, false);
                context.graphics().fill((int) context.button().getRealX(), (int) (context.button().getRealY() + context.button().getRealHeight()), (int) (context.button().getRealX() + super.font.width(title)), (int) (context.button().getRealY() + context.button().getRealHeight() - 1), 0xAC000000);
            }
        })
                .setX(leftMargin)
                .setY(topMargin + (itemIconSize + itemBottomMargin) * Math.floor((double) index / lineItemCount))
                .setWidth(super.width - leftBarWidth - leftMargin - rightMargin - rightBarWidth)
                .setHeight(titleHeight)
                .setBaseX(leftBarWidth));
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
            REWARD_BUTTONS.put(String.format("%s,%s", key, j), new OperationButton(j, context -> {
                if (context.button().getRealY() < super.height && context.button().getRealY() + context.button().getRealHeight() >= 0) {
                    Reward reward = rewardMap.get(key).get(context.button().getOperation());
                    AbstractGuiUtils.renderCustomReward(this.gg, super.font, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), reward, (int) context.button().getRealX(), (int) context.button().getRealY(), true);
                }
            })
                    .setX(leftMargin + (j % lineItemCount) * (itemIconSize + itemRightMargin))
                    .setY(topMargin + (itemIconSize + itemBottomMargin) * Math.floor((double) index.get() / lineItemCount))
                    .setWidth(itemIconSize)
                    .setHeight(itemIconSize)
                    .setBaseX(leftBarWidth)
                    .setTooltip(rewardMap.get(key).get(j).getName(SakuraUtils.getClientLanguage())));
        }
    }

    private StringInputScreen getRuleKeyInputScreen(Screen callbackScreen, ERewardRule rule, String[] key) {
        String validator = rule == ERewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
        return new StringInputScreen(callbackScreen, Text.translatable(EI18nType.TIPS, "enter_reward_rule_key").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_something"), validator, "", input -> {
            StringList result = new StringList();
            if (CollectionUtils.isNotNullOrEmpty(input)) {
                if (RewardOptionDataManager.validateKeyName(rule, input.get(0))) {
                    key[0] = input.get(0);
                } else {
                    result.add(Component.translatableClient(EI18nType.TIPS, "reward_rule_s_error", input.get(0)).toString());
                }
            }
            return result;
        });
    }

    private StringInputScreen getCdkRuleKeyInputScreen(Screen callbackScreen, ERewardRule rule, String[] key) {
        return new StringInputScreen(callbackScreen
                , new TextList(Text.translatable(EI18nType.TIPS, "enter_reward_rule_key").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_valid_until").setShadow(true))
                , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                , new StringList("\\w*", "")
                , new StringList("", DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1)))
                , input -> {
            StringList result = new StringList("", "");
            if (CollectionUtils.isNotNullOrEmpty(input)) {
                if (!RewardOptionDataManager.validateKeyName(rule, input.get(0))) {
                    result.set(0, Component.translatableClient(EI18nType.TIPS, "reward_rule_s_error", input.get(0)).toString());
                }
                if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                    if (DateUtils.format(input.get(1)) == null) {
                        result.set(1, Component.translatableClient(EI18nType.TIPS, "valid_until_s_error", input.get(1)).toString());
                    }
                }
                if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                    key[0] = String.format("%s|%s|-1", input.get(0), input.get(1));
                }
            }
            return result;
        });
    }

    /**
     * 更新奖励列表渲染方法集合
     */
    private void updateRewardList() {
        if (OperationButtonType.valueOf(currOpButton) == null) return;
        REWARD_BUTTONS.clear();
        RewardOptionDataManager.setRewardOptionDataChanged(false);
        RewardOptionData rewardOptionData = RewardOptionDataManager.getRewardOptionData();
        int titleIndex = -1;
        rewardListIndex.set(0);
        switch (OperationButtonType.valueOf(currOpButton)) {
            case BASE_REWARD: {
                this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "base_reward").toString(), "base", titleIndex, rewardListIndex.get());
                rewardListIndex.addAndGet(lineItemCount);
                this.addRewardButton(new HashMap<>() {{
                    put("base", rewardOptionData.getBaseRewards());
                }}, "base", rewardListIndex);
            }
            break;
            case CONTINUOUS_REWARD: {
                for (String key : rewardOptionData.getContinuousRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getContinuousRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case CYCLE_REWARD: {
                for (String key : rewardOptionData.getCycleRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getCycleRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case YEAR_REWARD: {
                for (String key : rewardOptionData.getYearRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "year_day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getYearRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case MONTH_REWARD: {
                for (String key : rewardOptionData.getMonthRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "month_day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getMonthRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case WEEK_REWARD: {
                for (String key : rewardOptionData.getWeekRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "week_" + key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getWeekRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case DATE_TIME_REWARD: {
                for (String key : rewardOptionData.getDateTimeRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(String.format("%s", key), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getDateTimeRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case CUMULATIVE_REWARD: {
                for (String key : rewardOptionData.getCumulativeRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getCumulativeRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case RANDOM_REWARD: {
                for (String key : rewardOptionData.getRandomRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(String.format("%s%%", StringUtils.toFixedEx(new BigDecimal(key).multiply(new BigDecimal(100)), 10)), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardOptionData.getRandomRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case CDK_REWARD: {
                for (int i = 0; i < rewardOptionData.getCdkRewards().size(); i++) {
                    KeyValue<String, KeyValue<String, RewardList>> keyValue = rewardOptionData.getCdkRewards().get(i);
                    String key = String.format("%s|%s|%d", keyValue.getKey(), keyValue.getValue().getKey(), i);
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(Component.translatableClient(EI18nType.TITLE, "s_valid_until_s", keyValue.getKey(), keyValue.getValue().getKey()).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(new HashMap<>() {{
                        put(key, keyValue.getValue().getValue());
                    }}, key, rewardListIndex);
                }
            }
            break;
        }
    }

    /**
     * 渲染奖励列表
     */
    private void renderRewardList(GuiGraphics graphics, double mouseX, double mouseY) {
        if (REWARD_BUTTONS.isEmpty()) return;

        // 直接渲染奖励列表 REWARD_BUTTONS
        for (String key : REWARD_BUTTONS.keySet()) {
            OperationButton operationButton = REWARD_BUTTONS.get(key);
            // 渲染物品图标
            operationButton.setBaseY(yOffset).render(graphics, mouseX, mouseY);
        }
        // 渲染Tips
        for (String key : REWARD_BUTTONS.keySet()) {
            OperationButton operationButton = REWARD_BUTTONS.get(key);
            // 渲染物品图标
            operationButton.setBaseY(yOffset).renderPopup(graphics, mouseX, mouseY, this.keyCode, this.modifiers);
        }
    }

    private final Consumer<PopupOption> pasteConsumer = option -> {
        String paste = Component.translatableClient(EI18nType.OPTION, "paste").toString();
        if (paste.equalsIgnoreCase(option.getSelectedString())) {
            option.getRenderList().stream()
                    .filter(item -> paste.equalsIgnoreCase(item.getContent()))
                    .forEach(item -> item.setColor(!SakuraSignIn.getClipboard().isEmpty() ? 0xFFFFFFFF : 0xFF999999));
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
    private void handleOperation(double mouseX, double mouseY, int button, OperationButton value, AtomicBoolean updateLayout, AtomicBoolean flag) {
        // 展开左侧边栏
        if (value.getOperation() == OperationButtonType.OPEN.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setRewardOptionBarOpened(true);
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 关闭左侧边栏
        else if (value.getOperation() == OperationButtonType.CLOSE.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setRewardOptionBarOpened(false);
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 左侧边栏奖励规则类型按钮
        else if (value.getOperation() > 200 && value.getOperation() <= 299) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.currOpButton = value.getOperation();
                updateLayout.set(true);
                try {
                    LocalPlayer player = Minecraft.getInstance().player;
                    assert player != null;
                    ERewardRule rewardRule = ERewardRule.valueOf(OperationButtonType.valueOf(value.getOperation()).name());
                    if (!player.hasPermissions(SakuraUtils.getRewardPermissionLevel(rewardRule))) {
                        Component component = Component.translatableClient(EI18nType.MESSAGE, "no_permission_to_view_reward", Component.translatableClient(EI18nType.WORD, SakuraUtils.getRewardRuleI18nKeyName(rewardRule)));
                        NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgColor(0x88FF5555));
                    }
                    if (!player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())) {
                        Component component = Component.translatableClient(EI18nType.MESSAGE, "no_permission_to_edit_reward");
                        NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgColor(0xAAFCFCB9));
                    }
                } catch (Exception ignored) {
                }
            } else {
                // 绘制弹出层选项
                this.popupOption.clear();
                this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "clear").setColor(0xFFFF0000))
                        .addTips(Text.translatable(EI18nType.TIPS, "cancel_or_confirm"))
                        .setTipsKeyCode(GLFW.GLFW_KEY_LEFT_SHIFT)
                        .setTipsModifiers(GLFW.GLFW_MOD_SHIFT)
                        .build(super.font, mouseX, mouseY, String.format("奖励规则类型按钮:%s", value.getOperation()));
            }
        }
        // 重置偏移量
        else if (value.getOperation() == OperationButtonType.OFFSET_Y.getCode()) {
            this.setYOffset(0);
        }
        // 奖励配置列表面板
        else if (value.getOperation() == OperationButtonType.REWARD_PANEL.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (this.currOpButton > 200 && this.currOpButton <= 299) {
                    this.popupOption.clear();
                    this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "paste"));
                    for (ERewardType rewardType : ERewardType.values()) {
                        this.popupOption.addOption(Text.translatable(EI18nType.WORD, "reward_type_" + rewardType.getCode()));
                    }
                    this.popupOption.build(super.font, mouseX, mouseY, String.format("奖励面板按钮:%s", this.currOpButton));
                    this.popupOption.setBeforeRender(pasteConsumer);
                    flag.set(true);
                }
            }
        }
        // 帮助按钮
        else if (value.getOperation() == OperationButtonType.HELP.getCode()) {
            // 绘制弹出层提示
            this.popupOption.clear();
            this.popupOption.addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_1"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_2"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_3"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_4"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_5"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_6"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_7"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_8"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_9"))
                    .addOption(Text.translatable(EI18nType.TIPS, "reward_rule_description_10"))
                    .build(super.font, mouseX, mouseY, "reward_rule_description");
        }
        // 上传奖励配置
        else if (value.getOperation() == OperationButtonType.UPLOAD.getCode()) {
            // 仅管理员可上传
            if (!Minecraft.getInstance().isLocalServer()) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    if (player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())) {
                        for (RewardOptionSyncPacket rewardOptionSyncPacket : RewardOptionDataManager.toSyncPacket(player).split()) {
                            ModNetworkHandler.INSTANCE.send(rewardOptionSyncPacket, PacketDistributor.SERVER.noArg());
                        }
                        flag.set(true);
                    }
                }
            } else {
                Component component = Component.translatable(EI18nType.MESSAGE, "local_server_not_support_this_operation");
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgColor(0xAAFCFCB9));
            }
        }
        // 下载奖励配置
        else if (value.getOperation() == OperationButtonType.DOWNLOAD.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (!Minecraft.getInstance().isLocalServer()) {
                    // 备份签到奖励配置
                    RewardOptionDataManager.backupRewardOption();
                    // 同步签到奖励配置到客户端
                    ModNetworkHandler.INSTANCE.send(new DownloadRewardOptionNotice(), PacketDistributor.SERVER.noArg());
                    flag.set(true);
                } else {
                    Component component = Component.translatable(EI18nType.MESSAGE, "local_server_not_support_this_operation");
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgColor(0xAAFCFCB9));
                }
            }
        }
        // 排序
        else if (value.getOperation() == OperationButtonType.SORT.getCode()) {
            RewardOptionDataManager.sortRewards();
            RewardOptionDataManager.saveRewardOption();
            updateLayout.set(true);
            flag.set(true);
        }
        // 打开配置文件夹
        else if (value.getOperation() == OperationButtonType.FOLDER.getCode()) {
            SakuraSignIn.openFileInFolder(new File(FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID).toFile(), RewardOptionDataManager.FILE_NAME).toPath());
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
    private void handleRewardOption(double mouseX, double mouseY, int button, String key, OperationButton value, AtomicBoolean updateLayout, AtomicBoolean flag) {
        LOGGER.debug("选择了奖励配置:\tButton: {}\tOperation: {}\tKey: {}\tIndex: {}", button, this.currOpButton, key, value.getOperation());
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (key.startsWith("标题")) {
                this.popupOption.clear();
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "edit"));
                }
                this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "copy"));
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "cut"));
                }
                this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "paste"));
                for (ERewardType rewardType : ERewardType.values()) {
                    this.popupOption.addOption(Text.translatable(EI18nType.WORD, "reward_type_" + rewardType.getCode()));
                }
                this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "clear").setColor(0xFFFF0000));
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "delete").setColor(0xFFFF0000));
                }
                this.popupOption.addTips(Text.translatable(EI18nType.TIPS, "cancel_or_confirm"), -1)
                        .addTips(Text.translatable(EI18nType.TIPS, "cancel_or_confirm"), -2)
                        .setTipsKeyCode(GLFW.GLFW_KEY_LEFT_SHIFT)
                        .setTipsModifiers(GLFW.GLFW_MOD_SHIFT)
                        .build(super.font, mouseX, mouseY, String.format("奖励按钮:%s", key));
            } else {
                this.popupOption.clear();
                this.popupOption.addOption(Text.translatable(EI18nType.OPTION, "edit"))
                        .addOption(Text.translatable(EI18nType.OPTION, "copy"))
                        .addOption(Text.translatable(EI18nType.OPTION, "cut"))
                        .addOption(Text.translatable(EI18nType.OPTION, "paste"))
                        .addOption(Text.translatable(EI18nType.OPTION, "delete").setColor(0xFFFF0000))
                        .addTips(Text.translatable(EI18nType.TIPS, "cancel_or_confirm"), -1)
                        .setTipsKeyCode(GLFW.GLFW_KEY_LEFT_SHIFT)
                        .setTipsModifiers(GLFW.GLFW_MOD_SHIFT)
                        .build(super.font, mouseX, mouseY, String.format("奖励按钮:%s", key));
            }
            this.popupOption.setBeforeRender(pasteConsumer);
            flag.set(true);
        }
    }

    /**
     * 处理弹出层选项
     *
     * @param mouseX       鼠标X坐标
     * @param mouseY       鼠标Y坐标
     * @param button       鼠标按键
     * @param updateLayout 是否更新布局
     * @param flag         是否处理过事件
     */
    private void handlePopupOption(double mouseX, double mouseY, int button, AtomicBoolean updateLayout, AtomicBoolean flag) {
        LOGGER.debug("选择了弹出选项:\tButton: {}\tId: {}\tIndex: {}\tContent: {}", button, popupOption.getId(), popupOption.getSelectedIndex(), popupOption.getSelectedString());
        String selectedString = popupOption.getSelectedString();
        OperationButtonType buttonType = OperationButtonType.valueOf(currOpButton);
        if (buttonType == null) return;
        ERewardRule rule = ERewardRule.valueOf(buttonType.toString());
        if (popupOption.getId().startsWith("奖励规则类型按钮:")) {
            int opCode = StringUtils.toInt(popupOption.getId().replace("奖励规则类型按钮:", ""));
            // 若选择了清空
            if (popupOption.getSelectedIndex() == 0 && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                // 并且按住了Control按钮
                if ((this.keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || this.keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) && this.modifiers == GLFW.GLFW_MOD_CONTROL) {
                    if (opCode > 200 && opCode <= 299) {
                        switch (OperationButtonType.valueOf(opCode)) {
                            case BASE_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getBaseRewards().clear();
                            }
                            break;
                            case CONTINUOUS_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getContinuousRewards().clear();
                            }
                            break;
                            case CYCLE_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getCycleRewards().clear();
                            }
                            break;
                            case YEAR_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getYearRewards().clear();
                            }
                            break;
                            case MONTH_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getMonthRewards().clear();
                            }
                            break;
                            case WEEK_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getWeekRewards().clear();
                            }
                            break;
                            case DATE_TIME_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getDateTimeRewards().clear();
                            }
                            break;
                            case CUMULATIVE_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getCumulativeRewards().clear();
                            }
                            break;
                            case RANDOM_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getRandomRewards().clear();
                            }
                            break;
                            case CDK_REWARD: {
                                RewardOptionDataManager.getRewardOptionData().getCdkRewards().clear();
                            }
                            break;
                        }
                        RewardOptionDataManager.saveRewardOption();
                        updateLayout.set(true);
                        flag.set(true);
                    }
                }
            }
        } else if (popupOption.getId().startsWith("奖励面板按钮:")) {
            String[] key = new String[]{""};
            if (Component.translatableClient(EI18nType.OPTION, "paste").toString().equalsIgnoreCase(selectedString)) {
                if (!SakuraSignIn.getClipboard().isEmpty()) {
                    if (rule == ERewardRule.CDK_REWARD) {
                        Minecraft.getInstance().setScreen(new StringInputScreen(this
                                , new TextList(Text.translatable(EI18nType.TIPS, "enter_reward_rule_key").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_valid_until").setShadow(true))
                                , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                                , new StringList("\\w*", "")
                                , new StringList("", DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1)))
                                , input -> {
                            StringList result = new StringList("", "");
                            if (CollectionUtils.isNotNullOrEmpty(input)) {
                                if (!RewardOptionDataManager.validateKeyName(rule, input.get(0))) {
                                    result.set(0, Component.translatableClient(EI18nType.TIPS, "reward_rule_s_error", input.get(0)).toString());
                                }
                                if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                                    if (DateUtils.format(input.get(1)) == null) {
                                        result.set(1, Component.translatableClient(EI18nType.TIPS, "valid_until_s_error", input.get(1)).toString());
                                    }
                                }
                                if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                                    RewardList rewardList = SakuraSignIn.getClipboard().clone();
                                    RewardOptionDataManager.addKeyName(rule, String.format("%s|%s|-1", input.get(0), input.get(1)), rewardList);
                                    RewardOptionDataManager.saveRewardOption();
                                }
                            }
                            return result;
                        }));
                    } else if (rule != ERewardRule.BASE_REWARD) {
                        String validator = rule == ERewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
                        Minecraft.getInstance().setScreen(new StringInputScreen(this
                                , Text.translatable(EI18nType.TIPS, "enter_reward_rule_key").setShadow(true)
                                , Text.translatable(EI18nType.TIPS, "enter_something")
                                , validator
                                , ""
                                , input -> {
                            StringList result = new StringList();
                            if (CollectionUtils.isNotNullOrEmpty(input)) {
                                if (RewardOptionDataManager.validateKeyName(rule, input.get(0))) {
                                    RewardList rewardList = SakuraSignIn.getClipboard().clone();
                                    RewardOptionDataManager.addKeyName(rule, input.get(0), rewardList);
                                    RewardOptionDataManager.saveRewardOption();
                                } else {
                                    result.add(Component.translatableClient(EI18nType.TIPS, "reward_rule_s_error", input.get(0)).toString());
                                }
                            }
                            return result;
                        }));
                    } else {
                        RewardList rewardList = SakuraSignIn.getClipboard().clone();
                        RewardOptionDataManager.addKeyName(rule, "", rewardList);
                        RewardOptionDataManager.saveRewardOption();
                    }
                }
            }
            // 物品
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.ITEM.getCode()).equalsIgnoreCase(selectedString)) {
                ItemSelectScreen callbackScreen = new ItemSelectScreen(this, input -> {
                    if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardOptionDataManager.addReward(rule, key[0], input);
                        RewardOptionDataManager.saveRewardOption();
                    }
                }, new Reward(new ItemStack(Items.AIR), ERewardType.ITEM), () -> StringUtils.isNullOrEmpty(key[0]));
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
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.EFFECT.getCode()).equalsIgnoreCase(selectedString)) {
                EffecrSelectScreen callbackScreen = new EffecrSelectScreen(this, input -> {
                    if (input != null && ((MobEffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0 && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardOptionDataManager.addReward(rule, key[0], input);
                        RewardOptionDataManager.saveRewardOption();
                    }
                }, new Reward(new MobEffectInstance(MobEffects.LUCK), ERewardType.EFFECT), () -> StringUtils.isNullOrEmpty(key[0]));
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
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.EXP_POINT.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_exp_point").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                        , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        int count = StringUtils.toInt(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        if (count != 0) {
                            RewardOptionDataManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, ERewardType.EXP_POINT), ERewardType.EXP_POINT, p));
                            RewardOptionDataManager.saveRewardOption();
                        } else {
                            result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
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
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.EXP_LEVEL.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_exp_level").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                        , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        int count = StringUtils.toInt(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        if (count != 0) {
                            RewardOptionDataManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, ERewardType.EXP_LEVEL), ERewardType.EXP_LEVEL, p));
                            RewardOptionDataManager.saveRewardOption();
                        } else {
                            result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
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
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.SIGN_IN_CARD.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_sign_in_card").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                        , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        int count = StringUtils.toInt(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        if (count != 0) {
                            RewardOptionDataManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, ERewardType.SIGN_IN_CARD), ERewardType.SIGN_IN_CARD, p));
                            RewardOptionDataManager.saveRewardOption();
                        } else {
                            result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
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
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.ADVANCEMENT.getCode()).equalsIgnoreCase(selectedString)) {
                AdvancementSelectScreen callbackScreen = new AdvancementSelectScreen(this, input -> {
                    if (input != null && StringUtils.isNotNullOrEmpty(input.toString()) && StringUtils.isNotNullOrEmpty(key[0])) {
                        RewardOptionDataManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(input, ERewardType.ADVANCEMENT), ERewardType.ADVANCEMENT));
                        RewardOptionDataManager.saveRewardOption();
                    }
                }, new Reward(new ResourceLocation(""), ERewardType.ADVANCEMENT), () -> StringUtils.isNullOrEmpty(key[0]));
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
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.MESSAGE.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_message").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                        , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("", "1")
                        , input -> {
                    if (CollectionUtils.isNotNullOrEmpty(input) && StringUtils.isNotNullOrEmpty(key[0])) {
                        Component component = Component.literal(input.get(0));
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        RewardOptionDataManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(component, ERewardType.MESSAGE), ERewardType.MESSAGE, p));
                        RewardOptionDataManager.saveRewardOption();
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
            else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.COMMAND.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen callbackScreen = new StringInputScreen(this
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_command").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                        , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                        , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                        , new StringList("", "1")
                        , input -> {
                    StringList result = new StringList();
                    if (CollectionUtils.isNotNullOrEmpty(input) && input.get(0).startsWith("/") && StringUtils.isNotNullOrEmpty(key[0])) {
                        BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                        RewardOptionDataManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(input.get(0), ERewardType.COMMAND), ERewardType.COMMAND, p));
                        RewardOptionDataManager.saveRewardOption();
                    } else {
                        result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
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
        } else if (popupOption.getId().startsWith("奖励按钮:")) {
            String id = popupOption.getId().replace("奖励按钮:", "");
            if (id.startsWith("标题")) {
                String key = id.substring(3);
                if (Component.translatableClient(EI18nType.OPTION, "edit").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        if (rule == ERewardRule.CDK_REWARD) {
                            String[] split = key.split("\\|");
                            if (split.length != 3 && split.length != 2)
                                split = new String[]{"", DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1))};
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_reward_rule_key").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_valid_until").setShadow(true))
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                                    , new StringList("\\w*", "")
                                    , new StringList(split[0], split[1])
                                    , input -> {
                                StringList result = new StringList("", "");
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    if (!RewardOptionDataManager.validateKeyName(rule, input.get(0))) {
                                        result.set(0, Component.translatableClient(EI18nType.TIPS, "reward_rule_s_error", input.get(0)).toString());
                                    }
                                    if (StringUtils.isNotNullOrEmpty(input.get(1))) {
                                        if (DateUtils.format(input.get(1)) == null) {
                                            result.set(1, Component.translatableClient(EI18nType.TIPS, "valid_until_s_error", input.get(1)).toString());
                                        }
                                    }
                                    if (result.stream().allMatch(StringUtils::isNullOrEmptyEx)) {
                                        RewardOptionDataManager.updateKeyName(rule, key, String.format("%s|%s|-1", input.get(0), input.get(1)));
                                        RewardOptionDataManager.saveRewardOption();
                                    }
                                }
                                return result;
                            }));
                        } else {
                            String validator = rule == ERewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
                            Minecraft.getInstance().setScreen(new StringInputScreen(this, Text.translatable(EI18nType.TIPS, "enter_reward_rule_key").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_something"), validator, key, input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    if (RewardOptionDataManager.validateKeyName(rule, input.get(0))) {
                                        RewardOptionDataManager.updateKeyName(rule, key, input.get(0));
                                        RewardOptionDataManager.saveRewardOption();
                                    } else {
                                        result.add(Component.translatableClient(EI18nType.TIPS, "reward_rule_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }));
                        }
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "copy").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        RewardList rewardList = RewardOptionDataManager.getKeyName(rule, key).clone();
                        SakuraSignIn.getClipboard().clear();
                        SakuraSignIn.getClipboard().addAll(rewardList);
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "cut").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        RewardList rewardList = RewardOptionDataManager.getKeyName(rule, key).clone();
                        RewardOptionDataManager.deleteKey(rule, key);
                        RewardOptionDataManager.saveRewardOption();
                        SakuraSignIn.getClipboard().clear();
                        SakuraSignIn.getClipboard().addAll(rewardList);
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "paste").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        if (!SakuraSignIn.getClipboard().isEmpty()) {
                            RewardList rewardList = SakuraSignIn.getClipboard().clone();
                            RewardOptionDataManager.addKeyName(rule, key, rewardList);
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "clear").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        if ((this.keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || this.keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) && this.modifiers == GLFW.GLFW_MOD_CONTROL) {
                            RewardOptionDataManager.clearKey(rule, key);
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "delete").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        if ((this.keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || this.keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) && this.modifiers == GLFW.GLFW_MOD_CONTROL) {
                            RewardOptionDataManager.deleteKey(rule, key);
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }
                }
                // 添加物品
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.ITEM.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new ItemSelectScreen(this, input -> {
                        if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR) {
                            RewardOptionDataManager.addReward(rule, key, input);
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }, new Reward(new ItemStack(Items.AIR), ERewardType.ITEM)));
                }
                // 药水效果
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.EFFECT.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new EffecrSelectScreen(this, input -> {
                        if (input != null && ((MobEffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0) {
                            RewardOptionDataManager.addReward(rule, key, input);
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }, new Reward(new MobEffectInstance(MobEffects.LUCK), ERewardType.EFFECT)));
                }
                // 经验点
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.EXP_POINT.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_exp_point").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                            , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            int count = StringUtils.toInt(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            if (count != 0) {
                                RewardOptionDataManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, ERewardType.EXP_POINT), ERewardType.EXP_POINT, p));
                                RewardOptionDataManager.saveRewardOption();
                            } else {
                                result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
                            }
                        }
                        return result;
                    }));
                }
                // 经验等级
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.EXP_LEVEL.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_exp_level").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                            , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            int count = StringUtils.toInt(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            if (count != 0) {
                                RewardOptionDataManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, ERewardType.EXP_LEVEL), ERewardType.EXP_LEVEL, p));
                                RewardOptionDataManager.saveRewardOption();
                            } else {
                                result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
                            }
                        }
                        return result;
                    }));
                }
                // 补签卡
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.SIGN_IN_CARD.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_sign_in_card").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                            , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            int count = StringUtils.toInt(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            if (count != 0) {
                                RewardOptionDataManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, ERewardType.SIGN_IN_CARD), ERewardType.SIGN_IN_CARD, p));
                                RewardOptionDataManager.saveRewardOption();
                            } else {
                                result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
                            }
                        }
                        return result;
                    }));
                }
                // 进度
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.ADVANCEMENT.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new AdvancementSelectScreen(this, input -> {
                        if (input != null && StringUtils.isNotNullOrEmpty(((ResourceLocation) RewardManager.deserializeReward(input)).toString())) {
                            RewardOptionDataManager.addReward(rule, key, input);
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }, new Reward(new ResourceLocation(""), ERewardType.ADVANCEMENT)));

                }
                // 消息
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.MESSAGE.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_message").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                            , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("", "1")
                            , input -> {
                        if (CollectionUtils.isNotNullOrEmpty(input)) {
                            Component component = Component.literal(input.get(0));
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            RewardOptionDataManager.addReward(rule, key, new Reward(RewardManager.serializeReward(component, ERewardType.MESSAGE), ERewardType.MESSAGE, p));
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }));
                }
                // 指令
                else if (I18nUtils.getTranslationClient(EI18nType.WORD, "reward_type_" + ERewardType.COMMAND.getCode()).equalsIgnoreCase(selectedString)) {
                    Minecraft.getInstance().setScreen(new StringInputScreen(this
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_command").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                            , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                            , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                            , new StringList("", "1")
                            , input -> {
                        StringList result = new StringList();
                        if (CollectionUtils.isNotNullOrEmpty(input) && input.get(0).startsWith("/")) {
                            BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                            RewardOptionDataManager.addReward(rule, key, new Reward(RewardManager.serializeReward(input.get(0), ERewardType.COMMAND), ERewardType.COMMAND, p));
                            RewardOptionDataManager.saveRewardOption();
                        } else {
                            result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
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
                if (Component.translatableClient(EI18nType.OPTION, "edit").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        Reward reward = RewardOptionDataManager.getReward(rule, key, Integer.parseInt(index)).clone();
                        if (reward.getType() == ERewardType.ITEM) {
                            Minecraft.getInstance().setScreen(new ItemSelectScreen(this, input -> {
                                if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR) {
                                    RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), input);
                                    RewardOptionDataManager.saveRewardOption();
                                }
                            }, reward));
                        }
                        // 药水效果
                        else if (reward.getType() == ERewardType.EFFECT) {
                            Minecraft.getInstance().setScreen(new EffecrSelectScreen(this, input -> {
                                if (input != null && ((MobEffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0) {
                                    RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), input);
                                    RewardOptionDataManager.saveRewardOption();
                                }
                            }, reward));
                        }
                        // 经验点
                        else if (reward.getType() == ERewardType.EXP_POINT) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_exp_point").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                                    , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(String.valueOf((Integer) RewardManager.deserializeReward(reward)), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    int count = StringUtils.toInt(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    if (count != 0) {
                                        RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, ERewardType.EXP_POINT), ERewardType.EXP_POINT, p));
                                        RewardOptionDataManager.saveRewardOption();
                                    } else {
                                        result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }
                            ));
                        }
                        // 经验等级
                        else if (reward.getType() == ERewardType.EXP_LEVEL) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_exp_level").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                                    , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(String.valueOf((Integer) RewardManager.deserializeReward(reward)), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    int count = StringUtils.toInt(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    if (count != 0) {
                                        RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, ERewardType.EXP_LEVEL), ERewardType.EXP_LEVEL, p));
                                        RewardOptionDataManager.saveRewardOption();
                                    } else {
                                        result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }
                            ));
                        }
                        // 补签卡
                        else if (reward.getType() == ERewardType.SIGN_IN_CARD) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_sign_in_card").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                                    , new StringList("-?\\d*", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(String.valueOf((Integer) RewardManager.deserializeReward(reward)), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    int count = StringUtils.toInt(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    if (count != 0) {
                                        RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, ERewardType.SIGN_IN_CARD), ERewardType.SIGN_IN_CARD, p));
                                        RewardOptionDataManager.saveRewardOption();
                                    } else {
                                        result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
                                    }
                                }
                                return result;
                            }
                            ));
                        }
                        // 进度
                        else if (reward.getType() == ERewardType.ADVANCEMENT) {
                            Minecraft.getInstance().setScreen(new AdvancementSelectScreen(this, input -> {
                                if (input != null && StringUtils.isNotNullOrEmpty(((ResourceLocation) RewardManager.deserializeReward(input)).toString()) && StringUtils.isNotNullOrEmpty(key)) {
                                    RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), input);
                                    RewardOptionDataManager.saveRewardOption();
                                }
                            }, reward));
                        }
                        // 消息
                        else if (reward.getType() == ERewardType.MESSAGE) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_message").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                                    , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(RewardManager.deserializeReward(reward).toString(), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                if (CollectionUtils.isNotNullOrEmpty(input)) {
                                    Component textToComponent = Component.literal(input.get(0));
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(textToComponent, ERewardType.MESSAGE), ERewardType.MESSAGE, p));
                                    RewardOptionDataManager.saveRewardOption();
                                }
                            }
                            ));
                        }
                        // 指令
                        else if (reward.getType() == ERewardType.COMMAND) {
                            Minecraft.getInstance().setScreen(new StringInputScreen(this
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_command").setShadow(true), Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    , new TextList(Text.translatable(EI18nType.TIPS, "enter_something"))
                                    , new StringList("", "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?")
                                    , new StringList(RewardManager.deserializeReward(reward), StringUtils.toFixedEx(reward.getProbability(), 5))
                                    , input -> {
                                StringList result = new StringList();
                                if (CollectionUtils.isNotNullOrEmpty(input) && input.get(0).startsWith("/")) {
                                    BigDecimal p = StringUtils.toBigDecimal(input.get(1), BigDecimal.ONE);
                                    RewardOptionDataManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(input.get(0), ERewardType.COMMAND), ERewardType.COMMAND, p));
                                    RewardOptionDataManager.saveRewardOption();
                                } else {
                                    result.add(Component.translatableClient(EI18nType.TIPS, "enter_value_s_error", input.get(0)).toString());
                                }
                                return result;
                            }
                            ));
                        }
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "copy").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        Reward reward = RewardOptionDataManager.getReward(rule, key, Integer.parseInt(index)).clone();
                        SakuraSignIn.getClipboard().clear();
                        SakuraSignIn.getClipboard().add(reward);
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "cut").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        Reward reward = RewardOptionDataManager.getReward(rule, key, Integer.parseInt(index)).clone();
                        RewardOptionDataManager.deleteReward(rule, key, Integer.parseInt(index));
                        RewardOptionDataManager.saveRewardOption();
                        SakuraSignIn.getClipboard().clear();
                        SakuraSignIn.getClipboard().add(reward);
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "paste").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        if (!SakuraSignIn.getClipboard().isEmpty()) {
                            for (Reward reward : SakuraSignIn.getClipboard().clone()) {
                                RewardOptionDataManager.addReward(rule, key, reward);
                            }
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }
                } else if (Component.translatableClient(EI18nType.OPTION, "delete").toString().equalsIgnoreCase(selectedString)) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        if ((this.keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || this.keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) && this.modifiers == GLFW.GLFW_MOD_CONTROL) {
                            RewardOptionDataManager.deleteReward(rule, key, Integer.parseInt(index));
                            RewardOptionDataManager.saveRewardOption();
                        }
                    }
                }
            }
            updateLayout.set(true);
            flag.set(true);
        }
    }

    /**
     * 生成操作按钮的自定义渲染函数
     *
     * @param content 按钮内容
     */
    private Consumer<OperationButton.RenderContext> generateCustomRenderFunction(String content) {
        return context -> {
            int realX = (int) context.button().getRealX();
            int realY = (int) context.button().getRealY();
            double realWidth = context.button().getRealWidth();
            double realHeight = context.button().getRealHeight();
            int realX2 = (int) (context.button().getRealX() + realWidth);
            int realY2 = (int) (context.button().getRealY() + realHeight);
            if (this.currOpButton == context.button().getOperation()) {
                context.graphics().fill(realX + 1, realY, realX2 - 1, realY2, 0x44ACACAC);
            }
            if (context.button().isHovered()) {
                context.graphics().fill(realX, realY, realX2, realY2, 0x99ACACAC);
            }
            AbstractGuiUtils.drawLimitedText(context.graphics(), super.font, Component.translatableClient(EI18nType.WORD, content).toString(), realX + 4, (int) (realY + (realHeight - super.font.lineHeight) / 2), (int) (realWidth - 22), 0xFFEBD4B1);
        };
    }

    private void updateLayout() {
        this.leftBarWidth = SakuraSignIn.isRewardOptionBarOpened() ? 100 : 20;
        this.lineItemCount = (super.width - leftBarWidth - leftMargin - rightMargin - rightBarWidth) / (itemIconSize + itemRightMargin);
        // 重置奖励面板坐标
        OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode()).setX(leftBarWidth).setY(0).setWidth(super.width - leftBarWidth - rightBarWidth).setHeight(super.height);
        // 更新奖励面板列表内容
        this.updateRewardList();
    }

    private void setYOffset(double offset) {
        // y坐标往上(-)不应该超过奖励高度+屏幕高度, 往下(+)不应该超过屏幕高度
        this.yOffset = Math.min(Math.max(offset, -(this.topMargin + (double) this.rewardListIndex.get() / this.lineItemCount * (this.itemIconSize + this.itemBottomMargin) + super.height)), super.height);
    }

    public RewardOptionScreen() {
        super(Component.translatableClient(EI18nType.TITLE, "reward_option_title").toTextComponent());
    }

    @Override
    protected void init() {
        this.cursor = MouseCursor.init();
        this.popupOption = PopupOption.init(super.font);
        super.init();
        this.leftBarTitleHeight = 5 * 2 + super.font.lineHeight;
        // 初始化材质及材质坐标信息
        ClientEventHandler.loadThemeTexture();
        OP_BUTTONS.put(OperationButtonType.REWARD_PANEL.getCode(), new OperationButton(OperationButtonType.REWARD_PANEL.getCode(), context -> {
        })
                .setTransparentCheck(false));
        OP_BUTTONS.put(OperationButtonType.OPEN.getCode(), new OperationButton(OperationButtonType.OPEN.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(new Coordinate().setX(4).setY((super.height - 16) / 2.0).setWidth(16).setHeight(16))
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getArrowUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getArrowHoverUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getArrowTapUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EI18nType.TIPS, "open_sidebar").toString()));
        OP_BUTTONS.put(OperationButtonType.CLOSE.getCode(), new OperationButton(OperationButtonType.CLOSE.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(new Coordinate().setX(80).setY((5 * 2 + super.font.lineHeight - 16) / 2.0).setWidth(16).setHeight(16))
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getArrowUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getArrowHoverUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getArrowTapUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setFlipHorizontal(true)
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EI18nType.TIPS, "close_sidebar").toString()));
        OP_BUTTONS.put(OperationButtonType.BASE_REWARD.getCode()
                , new OperationButton(OperationButtonType.BASE_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.BASE_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CONTINUOUS_REWARD.getCode()
                , new OperationButton(OperationButtonType.CONTINUOUS_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.CONTINUOUS_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1)).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CYCLE_REWARD.getCode()
                , new OperationButton(OperationButtonType.CYCLE_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.CYCLE_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 2).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.YEAR_REWARD.getCode()
                , new OperationButton(OperationButtonType.YEAR_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.YEAR_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 3).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.MONTH_REWARD.getCode()
                , new OperationButton(OperationButtonType.MONTH_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.MONTH_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 4).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.WEEK_REWARD.getCode()
                , new OperationButton(OperationButtonType.WEEK_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.WEEK_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 5).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.DATE_TIME_REWARD.getCode()
                , new OperationButton(OperationButtonType.DATE_TIME_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.DATE_TIME_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 6).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CUMULATIVE_REWARD.getCode()
                , new OperationButton(OperationButtonType.CUMULATIVE_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.CUMULATIVE_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 7).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.RANDOM_REWARD.getCode()
                , new OperationButton(OperationButtonType.RANDOM_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.RANDOM_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 8).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CDK_REWARD.getCode()
                , new OperationButton(OperationButtonType.CDK_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(ERewardRule.CDK_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 9).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.OFFSET_Y.getCode(), new OperationButton(OperationButtonType.OFFSET_Y.getCode(), context -> {
            AbstractGuiUtils.drawString(context.graphics(), super.font, "OY:", super.width - rightBarWidth + 1, super.height - font.lineHeight * 2 - 2, 0xFFACACAC);
            AbstractGuiUtils.drawLimitedText(context.graphics(), super.font, String.valueOf((int) yOffset), super.width - rightBarWidth + 1, super.height - font.lineHeight - 2, rightBarWidth, 0xFFACACAC);
        })
                .setX(super.width - rightBarWidth).setY(super.height - font.lineHeight * 2 - 2).setWidth(rightBarWidth).setHeight(font.lineHeight * 2 + 2)
                .setTransparentCheck(false));
        OP_BUTTONS.put(OperationButtonType.HELP.getCode(), new OperationButton(OperationButtonType.HELP.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(SakuraSignIn.getThemeTextureCoordinate().getHelpUV())
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getHelpUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getHelpUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getHelpUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setX(super.width - rightBarWidth + 1).setY(2).setWidth(18).setHeight(18)
                .setHoverFgColor(0xAA808080).setTapFgColor(0xAA808080)
                .setTransparentCheck(false));
        OP_BUTTONS.put(OperationButtonType.DOWNLOAD.getCode(), new OperationButton(OperationButtonType.DOWNLOAD.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(SakuraSignIn.getThemeTextureCoordinate().getDownloadUV())
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getDownloadUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getDownloadUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getDownloadUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setX(super.width - rightBarWidth + 1).setY(22).setWidth(18).setHeight(18)
                .setHoverFgColor(0xAA808080).setTapFgColor(0xAAA0A0A0)
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EI18nType.TIPS, "download_reward_config").toString()));
        OP_BUTTONS.put(OperationButtonType.UPLOAD.getCode(), new OperationButton(OperationButtonType.UPLOAD.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(SakuraSignIn.getThemeTextureCoordinate().getUploadUV())
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getUploadUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getUploadUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getUploadUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setX(super.width - rightBarWidth + 1).setY(42).setWidth(18).setHeight(18)
                .setHoverFgColor(0xAA808080).setTapFgColor(0xAAA0A0A0)
                .setTransparentCheck(false));
        OP_BUTTONS.put(OperationButtonType.FOLDER.getCode(), new OperationButton(OperationButtonType.FOLDER.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(SakuraSignIn.getThemeTextureCoordinate().getFolderUV())
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getFolderUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getFolderUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getFolderUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setX(super.width - rightBarWidth + 1).setY(62).setWidth(18).setHeight(18)
                .setHoverFgColor(0xAA808080).setTapFgColor(0xAAA0A0A0)
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EI18nType.TIPS, "open_config_folder").toString()));
        OP_BUTTONS.put(OperationButtonType.SORT.getCode(), new OperationButton(OperationButtonType.SORT.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(SakuraSignIn.getThemeTextureCoordinate().getSortUV())
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getSortUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getSortUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getSortUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setX(super.width - rightBarWidth + 1).setY(82).setWidth(18).setHeight(18)
                .setHoverFgColor(0xAA808080).setTapFgColor(0xAAA0A0A0)
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EI18nType.TIPS, "reward_rule_sort").toString()));
        this.updateLayout();

        tips = Text.translatable(EI18nType.TIPS, "reward_option_screen_tips");
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.gg = graphics;
        // 绘制缩放背景纹理
        this.renderBackgroundTexture(graphics);

        // 刷新数据
        if (RewardOptionDataManager.isRewardOptionDataChanged()) this.updateLayout();

        // 绘制操作提示
        if (OperationButtonType.valueOf(currOpButton) == null) {
            AbstractGuiUtils.fill(graphics, this.leftBarWidth + 4, 4, super.width - this.leftBarWidth - this.rightBarWidth - 8, super.height - 8, 0x88000000, 15);
            float x, y;
            tips.setGraphics(graphics).setFont(super.font);
            int textHeight = AbstractGuiUtils.multilineTextHeight(tips);
            int textWidth = AbstractGuiUtils.multilineTextWidth(tips);
            x = this.leftBarWidth + ((super.width - this.leftBarWidth - this.rightBarWidth) - textWidth) / 2.0f;
            y = (super.height - (textHeight + 4)) / 2.0f;
            AbstractGuiUtils.drawString(tips, x, y);
        }
        // 绘制奖励项目
        else {
            this.renderRewardList(graphics, mouseX, mouseY);
        }

        // 绘制左侧边栏列表背景
        graphics.fill(0, 0, leftBarWidth, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(graphics, 0, 0, leftBarWidth, super.height, 1, 0xFF000000);
        // 绘制左侧边栏列表标题
        if (SakuraSignIn.isRewardOptionBarOpened()) {
            graphics.drawString(super.font, Component.translatableClient(EI18nType.TITLE, "reward_rule_type").toString(), 4, 5, 0xFFACACAC);
            graphics.fill(0, leftBarTitleHeight, leftBarWidth, leftBarTitleHeight - 1, 0xAA000000);
        }
        // 绘制右侧边栏列表背景
        graphics.fill(super.width - rightBarWidth, 0, super.width, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(graphics, super.width - rightBarWidth, 0, rightBarWidth, super.height, 1, 0xFF000000);

        // 渲染操作按钮
        for (Integer op : OP_BUTTONS.keySet()) {
            OperationButton button = OP_BUTTONS.get(op);
            // 展开类按钮仅在关闭时绘制
            if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.OPEN.getCode()))) {
                if (!SakuraSignIn.isRewardOptionBarOpened()) {
                    button.render(graphics, mouseX, mouseY);
                }
            }
            // 收起类按钮仅在展开时绘制
            else if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.CLOSE.getCode()))) {
                if (SakuraSignIn.isRewardOptionBarOpened()) {
                    button.render(graphics, mouseX, mouseY);
                }
            }
            // 绘制其他按钮
            else {
                if (op == OperationButtonType.OFFSET_Y.getCode()) {
                    button.setTooltip(Component.translatableClient(EI18nType.TIPS, "y_offset", StringUtils.toFixedEx(this.yOffset, 1)).toString());
                }
                button.render(graphics, mouseX, mouseY);
            }
        }
        // 渲染操作按钮 提示
        for (Integer op : OP_BUTTONS.keySet()) {
            OperationButton button = OP_BUTTONS.get(op);
            // 展开类按钮仅在关闭时绘制
            if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.OPEN.getCode()))) {
                if (!SakuraSignIn.isRewardOptionBarOpened()) {
                    button.renderPopup(graphics, mouseX, mouseY, this.keyCode, this.modifiers);
                }
            }
            // 收起类按钮仅在展开时绘制
            else if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.CLOSE.getCode()))) {
                if (SakuraSignIn.isRewardOptionBarOpened()) {
                    button.renderPopup(graphics, mouseX, mouseY, this.keyCode, this.modifiers);
                }
            }
            // 绘制其他按钮
            else {
                if (op == OperationButtonType.OFFSET_Y.getCode()) {
                    button.setTooltip(Component.translatableClient(EI18nType.TIPS, "y_offset", StringUtils.toFixedEx(this.yOffset, 1)).toString());
                }
                // 帮助按钮
                else if (op == OperationButtonType.HELP.getCode()) {
                    if ((this.keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || this.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) && this.modifiers == GLFW.GLFW_MOD_SHIFT) {
                        button.setTooltip(Component.translatableClient(EI18nType.TIPS, "help_button_shift").toString());
                    } else {
                        button.setTooltip(Component.translatableClient(EI18nType.TIPS, "help_button").toString());
                    }
                } else if (op == OperationButtonType.UPLOAD.getCode()) {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null && player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())) {
                        button.setTooltip(Component.translatableClient(EI18nType.TIPS, "upload_reward_config").toString())
                                .setHoverFgColor(0xAA808080).setTapFgColor(0xAAA0A0A0);
                    } else {
                        button.setTooltip(Text.translatable(EI18nType.TIPS, "upload_reward_config_no_permission").setColor(0xFFFF0000))
                                .setHoverFgColor(0xAA808080).setTapFgColor(0xAA808080);
                    }
                }
                button.renderPopup(graphics, mouseX, mouseY, this.keyCode, this.modifiers);
            }
        }

        // 绘制弹出选项
        popupOption.render(graphics, mouseX, mouseY, this.keyCode, this.modifiers);
        // 绘制鼠标光标
        cursor.draw(graphics, mouseX, mouseY);
    }

    /**
     * 窗口关闭时
     */
    @Override
    public void removed() {
        cursor.removed();
        super.removed();
    }

    /**
     * 检测鼠标点击事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        cursor.mouseClicked(mouseX, mouseY, button);
        // 清空弹出选项
        if (!popupOption.isHovered()) {
            popupOption.clear();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                OP_BUTTONS.forEach((key, value) -> {
                    if (value.isHovered()) {
                        value.setPressed(true);
                        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && key == OperationButtonType.REWARD_PANEL.getCode()) {
                            this.yOffsetOld = this.yOffset;
                            this.mouseDownX = mouseX;
                            this.mouseDownY = mouseY;
                        }
                    }
                });
                REWARD_BUTTONS.forEach((key, value) -> {
                    if (value.isHovered()) {
                        value.setPressed(true);
                    }
                });
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 检测鼠标松开事件
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        cursor.mouseReleased(mouseX, mouseY, button);
        AtomicBoolean updateLayout = new AtomicBoolean(false);
        AtomicBoolean flag = new AtomicBoolean(false);
        if (popupOption.isHovered()) {
            this.handlePopupOption(mouseX, mouseY, button, updateLayout, flag);
            popupOption.clear();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // 控制按钮
            OP_BUTTONS.forEach((key, value) -> {
                // 忽略奖励配置列表面板, 后置处理
                if (key != OperationButtonType.REWARD_PANEL.getCode()) {
                    if (value.isHovered() && value.isPressed()) {
                        this.handleOperation(mouseX, mouseY, button, value, updateLayout, flag);
                    }
                    value.setPressed(false);
                }
            });
            // 奖励按钮
            if (!flag.get()) {
                REWARD_BUTTONS.forEach((key, value) -> {
                    if (value.isHovered() && value.isPressed()) {
                        this.handleRewardOption(mouseX, mouseY, button, key, value, updateLayout, flag);
                    }
                    value.setPressed(false);
                });
            }
            // 奖励配置列表面板
            if (!flag.get()) {
                OperationButton rewardPanel = OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode());
                if (rewardPanel.isHovered() && rewardPanel.isPressed()) {
                    this.handleOperation(mouseX, mouseY, button, rewardPanel, updateLayout, flag);
                    rewardPanel.setPressed(false);
                }
            }
            this.mouseDownX = -1;
            this.mouseDownY = -1;
        }
        if (updateLayout.get()) this.updateLayout();
        return flag.get() ? flag.get() : super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        OP_BUTTONS.forEach((key, value) -> {
            if (SakuraSignIn.isRewardOptionBarOpened()) {
                // 若为开启状态则隐藏开启按钮及其附属按钮
                if (!String.valueOf(key).startsWith(String.valueOf(OperationButtonType.OPEN.getCode()))) {
                    value.setHovered(value.isMouseOverEx(mouseX, mouseY));
                } else {
                    value.setHovered(false);
                }
            } else {
                // 若为关闭状态则隐藏关闭按钮及其附属按钮
                if (!String.valueOf(key).startsWith(String.valueOf(OperationButtonType.CLOSE.getCode()))) {
                    value.setHovered(value.isMouseOverEx(mouseX, mouseY));
                } else {
                    value.setHovered(false);
                }
            }
            // 是否按下并拖动奖励面板
            if (OperationButtonType.REWARD_PANEL.getCode() == key) {
                if (value.isPressed() && this.mouseDownX != -1 && this.mouseDownY != -1) {
                    this.setYOffset(this.yOffsetOld + (mouseY - this.mouseDownY));
                }
            }
        });
        REWARD_BUTTONS.forEach((key, value) -> value.setHovered(value.isMouseOverEx(mouseX, mouseY)));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollH, double scrollV) {
        cursor.mouseScrolled(mouseX, mouseY, scrollV);
        if (!popupOption.addScrollOffset(scrollV)) {
            // y坐标往上(-)不应该超过奖励高度+屏幕高度, 往下(+)不应该超过屏幕高度
            if (OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode()).isHovered()) {
                this.setYOffset(yOffset + scrollV);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollH, scrollV);
    }

    /**
     * 键盘按键按下事件
     *
     * @param keyCode   按键的键码
     * @param scanCode  按键的扫描码
     * @param modifiers 按键时按下的修饰键（如Shift、Ctrl等）
     * @return boolean 表示是否消耗了该按键事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // LOGGER.debug("keyPressed: keyCode = {}, scanCode = {}, modifiers = {}", keyCode, scanCode, modifiers);
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.previousScreen != null) Minecraft.getInstance().setScreen(this.previousScreen);
            else this.onClose();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    /**
     * 键盘按键释放事件
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // LOGGER.debug("keyReleased: keyCode = {}, scanCode = {}, modifiers = {}", keyCode, scanCode, modifiers);
        this.keyCode = -1;
        this.modifiers = -1;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * 窗口打开时是否暂停游戏
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
