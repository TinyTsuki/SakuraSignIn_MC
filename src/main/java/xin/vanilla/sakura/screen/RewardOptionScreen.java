package xin.vanilla.sakura.screen;

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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.ClientConfig;
import xin.vanilla.sakura.config.RewardConfig;
import xin.vanilla.sakura.config.ServerConfig;
import xin.vanilla.sakura.data.KeyValue;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.data.RewardList;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumRegex;
import xin.vanilla.sakura.enums.EnumRewardRule;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.network.packet.RewardOptionRequestToServer;
import xin.vanilla.sakura.network.packet.RewardOptionSyncToBoth;
import xin.vanilla.sakura.rewards.RewardClipboardManager;
import xin.vanilla.sakura.rewards.RewardConfigManager;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.screen.component.NotificationManager;
import xin.vanilla.sakura.screen.component.OperationButton;
import xin.vanilla.sakura.screen.component.PopupOption;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.util.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


@OnlyIn(Dist.CLIENT)
public class RewardOptionScreen extends SakuraScreen {
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
     * 是否已处理过奖励按钮选中
     */
    private boolean handledRewardButton;

    /**
     * 操作按钮集合
     */
    private final Map<Integer, OperationButton> OP_BUTTONS = new HashMap<>();

    /**
     * 奖励列表按钮集合
     */
    private final Map<String, OperationButton> REWARD_BUTTONS = new HashMap<>();

    /**
     * 尝试删除基础奖励标题次数
     */
    private int deleteBaseCount = 0;

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
        AbstractGuiUtils.bindTexture(SakuraSignIn.getThemeTexture());

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
        REWARD_BUTTONS.put(String.format("标题,%s", key), new OperationButton(titleIndex, context -> {
            if (context.button.getRealY() < super.height && context.button.getRealY() + context.button.getRealHeight() >= 0) {
                AbstractGui.fill(this.ms, (int) context.button.getRealX(), (int) (context.button.getRealY()), (int) (context.button.getRealX() + context.button.getRealWidth()), (int) (context.button.getRealY() + 1), 0xAC000000);
                AbstractGuiUtils.drawLimitedText(this.ms, super.font, title, (int) context.button.getRealX(), (int) (context.button.getRealY() + (context.button.getRealHeight() - super.font.lineHeight) / 2), (int) context.button.getRealWidth(), 0xAC000000, false);
                AbstractGui.fill(this.ms, (int) context.button.getRealX(), (int) (context.button.getRealY() + context.button.getRealHeight()), (int) (context.button.getRealX() + super.font.width(title)), (int) (context.button.getRealY() + context.button.getRealHeight() - 1), 0xAC000000);
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
                if (context.button.getRealY() < super.height && context.button.getRealY() + context.button.getRealHeight() >= 0) {
                    Reward reward = rewardMap.get(key).get(context.button.getOperation());
                    AbstractGuiUtils.renderCustomReward(this.ms, this.itemRenderer, super.font, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), reward, (int) context.button.getRealX(), (int) context.button.getRealY(), true);
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

    private StringInputScreen getRuleKeyInputScreen(Screen callbackScreen, EnumRewardRule rule, String[] key) {
        String regex = rule == EnumRewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
        StringInputScreen.Args args = new StringInputScreen.Args()
                .setParentScreen(callbackScreen)
                .addWidget(new StringInputScreen.Widget()
                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_rule_key_" + rule.getCode()).setShadow(true))
                        .setRegex(regex)
                        .setValidator((input) -> {
                            if (!RewardConfigManager.validateKeyName(rule, input.getValue())) {
                                return Component.translatableClient(EnumI18nType.TIPS, "reward_rule_s_error", input.getValue()).toString();
                            }
                            return null;
                        })
                )
                .setCallback(input -> key[0] = input.getFirstValue());
        return new StringInputScreen(args);
    }

    private StringInputScreen getCdkRuleKeyInputScreen(Screen callbackScreen, EnumRewardRule rule, String[] key) {
        StringInputScreen.Args args = new StringInputScreen.Args()
                .setParentScreen(callbackScreen)
                .addWidget(new StringInputScreen.Widget()
                        .setName("key")
                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_rule_key_" + rule.getCode()).setShadow(true))
                        .setRegex("\\w*")
                        .setValidator((input) -> {
                            if (!RewardConfigManager.validateKeyName(rule, input.getValue())) {
                                return Component.translatableClient(EnumI18nType.TIPS, "reward_rule_s_error", input.getValue()).toString();
                            }
                            return null;
                        })
                )
                .addWidget(new StringInputScreen.Widget()
                        .setName("valid")
                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_valid_until").setShadow(true))
                        .setDefaultValue(DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1)))
                        .setValidator((input) -> {
                            if (DateUtils.format(input.getValue()) == null) {
                                return Component.translatableClient(EnumI18nType.TIPS, "valid_until_s_error", input.getValue()).toString();
                            }
                            return null;
                        })
                )
                .addWidget(new StringInputScreen.Widget()
                        .setName("num")
                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_num").setShadow(true))
                        .setRegex("\\d*")
                        .setDefaultValue("1")
                        .setValidator((input) -> {
                            if (StringUtils.toInt(input.getValue()) <= 0) {
                                return Component.translatableClient(EnumI18nType.TIPS, "num_s_error", input.getValue()).toString();
                            }
                            return null;
                        })
                )
                .setCallback(input -> key[0] = String.format("%s|%s|-1|%d"
                        , input.getValue("key")
                        , input.getValue("valid")
                        , StringUtils.toInt(input.getValue("num"), 1)
                ));
        return new StringInputScreen(args);
    }

    /**
     * 更新奖励列表渲染方法集合
     */
    private void updateRewardList() {
        RewardConfigManager.setRewardOptionDataChanged(false);
        if (OperationButtonType.valueOf(currOpButton) == null) return;
        REWARD_BUTTONS.clear();
        RewardConfig rewardConfig = RewardConfigManager.getRewardConfig();
        int titleIndex = -1;
        rewardListIndex.set(0);
        switch (OperationButtonType.valueOf(currOpButton)) {
            case BASE_REWARD: {
                this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "base_reward").toString(), "base", titleIndex, rewardListIndex.get());
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
                    this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
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
                    this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
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
                    this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "year_day_s", key).toString(), key, titleIndex, rewardListIndex.get());
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
                    this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "month_day_s", key).toString(), key, titleIndex, rewardListIndex.get());
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
                    this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "week_" + key).toString(), key, titleIndex, rewardListIndex.get());
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
                    this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "day_s", key).toString(), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getCumulativeRewards(), key, rewardListIndex);
                    titleIndex--;
                }
            }
            break;
            case RANDOM_REWARD: {
                for (String key : rewardConfig.getRandomRewards().keySet()) {
                    if (rewardListIndex.get() > 0) {
                        rewardListIndex.set((int) ((Math.floor((double) rewardListIndex.get() / lineItemCount) + 1) * lineItemCount));
                    }
                    this.addRewardTitleButton(String.format("%s%%", StringUtils.toFixedEx(new BigDecimal(key).multiply(new BigDecimal(100)), 10)), key, titleIndex, rewardListIndex.get());
                    rewardListIndex.addAndGet(lineItemCount);
                    this.addRewardButton(rewardConfig.getRandomRewards(), key, rewardListIndex);
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
                    this.addRewardTitleButton(Component.translatableClient(EnumI18nType.TITLE, "s_valid_until_s", keyValue.getKey().getKey(), keyValue.getKey().getValue(), keyValue.getValue().getValue()).toString(), key, titleIndex, rewardListIndex.get());
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
    private void renderRewardList(MatrixStack matrixStack) {
        if (REWARD_BUTTONS.isEmpty()) return;

        // 直接渲染奖励列表 REWARD_BUTTONS
        for (String key : REWARD_BUTTONS.keySet()) {
            OperationButton operationButton = REWARD_BUTTONS.get(key);
            // 绘制选中边框
            if (key.equals(this.currRewardButton)) {
                AbstractGuiUtils.fillOutLine(matrixStack,
                        (int) operationButton.getRealX() - 1,
                        (int) operationButton.getRealY() - 1,
                        (int) operationButton.getRealWidth() + 2,
                        (int) operationButton.getRealHeight() + 2,
                        1,
                        0x88FFF13B);
            }
            // 渲染物品图标
            operationButton.setBaseY(yOffset).render(matrixStack, keyManager);
        }
        // 渲染Tips
        for (String key : REWARD_BUTTONS.keySet()) {
            OperationButton operationButton = REWARD_BUTTONS.get(key);
            // 渲染物品图标
            operationButton.setBaseY(yOffset).renderPopup(matrixStack, keyManager);
        }
    }

    private final Consumer<PopupOption> pasteConsumer = option -> {
        String paste = Component.translatableClient(EnumI18nType.OPTION, "paste").toString();
        if (paste.equalsIgnoreCase(option.getSelectedString())) {
            option.getRenderList().stream()
                    .filter(item -> paste.equalsIgnoreCase(item.getContent()))
                    .forEach(item -> item.setColorArgb(RewardClipboardManager.isClipboardValid() ? 0xFFFFFFFF : 0xFF999999));
        }
    };

    /**
     * 处理操作按钮事件
     *
     * @param value 操作按钮
     */
    private void handleOperation(MouseReleasedHandleArgs args, OperationButton value) {
        // 展开左侧边栏
        if (value.getOperation() == OperationButtonType.OPEN.getCode()) {
            if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setRewardOptionBarOpened(true);
                args.setLayout(true);
                args.setConsumed(true);
            }
        }
        // 关闭左侧边栏
        else if (value.getOperation() == OperationButtonType.CLOSE.getCode()) {
            if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setRewardOptionBarOpened(false);
                args.setLayout(true);
                args.setConsumed(true);
            }
        }
        // 左侧边栏奖励规则类型按钮
        else if (value.getOperation() > 200 && value.getOperation() <= 299) {
            if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                this.currOpButton = value.getOperation();
                args.setLayout(true);
                args.setConsumed(true);
                try {
                    ClientPlayerEntity player = Minecraft.getInstance().player;
                    Objects.requireNonNull(player);
                    EnumRewardRule rewardRule = EnumRewardRule.valueOf(OperationButtonType.valueOf(value.getOperation()).name());
                    if (!player.hasPermissions(SakuraUtils.getRewardPermissionLevel(rewardRule))) {
                        Component component = Component.translatableClient(EnumI18nType.MESSAGE, "no_permission_to_view_reward", Component.translatableClient(EnumI18nType.WORD, SakuraUtils.getRewardRuleI18nKeyName(rewardRule)));
                        NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FFFF55));
                    }
                    if (!player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())) {
                        Component component = Component.translatableClient(EnumI18nType.MESSAGE, "no_permission_to_edit_reward");
                        NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
                    }
                } catch (Exception ignored) {
                }
            } else {
                // 绘制弹出层选项
                this.popupOption.clear();
                this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "clear").setColorArgb(0xFFFF0000))
                        .addTips(Text.translatable(EnumI18nType.TIPS, "cancel_or_confirm"))
                        .setTipsKeyNames(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT))
                        .build(super.font, args.getMouseX(), args.getMouseY(), String.format("奖励规则类型按钮:%s", value.getOperation()));
                args.setConsumed(true);
            }
        }
        // 重置偏移量
        else if (value.getOperation() == OperationButtonType.OFFSET_Y.getCode()) {
            this.yOffsetResetTime = System.currentTimeMillis();
            this.yOffsetOld = this.yOffset;
            args.setConsumed(true);
        }
        // 奖励配置列表面板
        else if (value.getOperation() == OperationButtonType.REWARD_PANEL.getCode()) {
            if (!this.handledRewardButton) {
                this.handledRewardButton = true;
                this.currRewardButton = args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT && "panel".equalsIgnoreCase(this.currRewardButton) ? null : "panel";
            }

            if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                if (this.currOpButton > 200 && this.currOpButton <= 299) {
                    this.popupOption.clear();
                    this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "paste"));
                    for (EnumRewardType rewardType : EnumRewardType.values()) {
                        if (EnumRewardType.NONE.equals(rewardType)) continue;
                        this.popupOption.addOption(Text.translatable(EnumI18nType.WORD, "reward_type_" + rewardType.getCode()));
                    }
                    this.popupOption.build(super.font, args.getMouseX(), args.getMouseY(), String.format("奖励面板按钮:%s", this.currOpButton));
                    this.popupOption.setBeforeRender(pasteConsumer);
                    args.setConsumed(true);
                }
            }
        }
        // 帮助按钮
        else if (value.getOperation() == OperationButtonType.HELP.getCode()) {
            // 绘制弹出层提示
            this.popupOption.clear();
            this.popupOption.addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_1"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_2"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_3"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_4"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_5"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_6"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_7"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_8"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_9"))
                    .addOption(Text.translatable(EnumI18nType.TIPS, "reward_rule_description_10"))
                    .build(super.font, args.getMouseX(), args.getMouseY(), "reward_rule_description");
            args.setConsumed(true);
        }
        // 上传奖励配置
        else if (value.getOperation() == OperationButtonType.UPLOAD.getCode()) {
            // 仅管理员可上传
            if (!Minecraft.getInstance().isLocalServer()) {
                ClientPlayerEntity player = Minecraft.getInstance().player;
                if (player != null) {
                    if (player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())) {
                        for (RewardOptionSyncToBoth rewardOptionSyncToBoth : RewardConfigManager.toSyncPacket(player).split()) {
                            SakuraUtils.sendPacketToServer(rewardOptionSyncToBoth);
                        }
                        args.setConsumed(true);
                    }
                }
            } else {
                Component component = Component.translatable(EnumI18nType.MESSAGE, "local_server_not_support_this_operation");
                NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
            }
        }
        // 下载奖励配置
        else if (value.getOperation() == OperationButtonType.DOWNLOAD.getCode()) {
            if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                if (!Minecraft.getInstance().isLocalServer()) {
                    // 备份签到奖励配置
                    RewardConfigManager.backupRewardOption();
                    // 同步签到奖励配置到客户端
                    SakuraUtils.sendPacketToServer(new RewardOptionRequestToServer());
                    args.setConsumed(true);
                } else {
                    Component component = Component.translatable(EnumI18nType.MESSAGE, "local_server_not_support_this_operation");
                    NotificationManager.get().addNotification(NotificationManager.Notification.ofComponentWithBlack(component).setBgArgb(0x99FF5555));
                }
            }
        }
        // 排序
        else if (value.getOperation() == OperationButtonType.SORT.getCode()) {
            RewardConfigManager.sortRewards();
            RewardConfigManager.saveRewardOption();
            args.setLayout(true);
            args.setConsumed(true);
        }
        // 打开配置文件夹
        else if (value.getOperation() == OperationButtonType.FOLDER.getCode()) {
            SakuraUtils.openFileInFolder(new File(SakuraUtils.getConfigPath().toFile(), RewardConfigManager.FILE_NAME).toPath());
            args.setConsumed(true);
        }
    }

    /**
     * 处理奖励按钮事件
     *
     * @param value 奖励按钮
     */
    private void handleRewardOption(MouseReleasedHandleArgs args, String key, OperationButton value) {
        LOGGER.debug("选择了奖励配置:\tButton: {}\tOperation: {}\tKey: {}\tIndex: {}", args.getButton(), this.currOpButton, key, value.getOperation());

        if (!this.handledRewardButton) {
            this.handledRewardButton = true;
            this.currRewardButton = args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT && key.equalsIgnoreCase(this.currRewardButton) ? null : key;
        }

        if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT && !keyManager.isMouseMoved()) {
            if (key.startsWith("标题")) {
                this.popupOption.clear();
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "edit"));
                }
                this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "copy"));
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "cut"));
                }
                this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "paste"));
                for (EnumRewardType rewardType : EnumRewardType.values()) {
                    if (EnumRewardType.NONE.equals(rewardType)) continue;
                    this.popupOption.addOption(Text.translatable(EnumI18nType.WORD, "reward_type_" + rewardType.getCode()));
                }
                this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "clear").setColorArgb(0xFFFF0000));
                if (!"标题,base".equalsIgnoreCase(key)) {
                    this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "delete").setColorArgb(0xFFFF0000));
                }
                this.popupOption.addTips(Text.translatable(EnumI18nType.TIPS, "cancel_or_confirm"), -1)
                        .addTips(Text.translatable(EnumI18nType.TIPS, "cancel_or_confirm"), -2)
                        .setTipsKeyNames(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT))
                        .build(super.font, args.getMouseX(), args.getMouseY(), String.format("奖励按钮:%s", key));
            } else {
                this.popupOption.clear();
                this.popupOption.addOption(Text.translatable(EnumI18nType.OPTION, "edit"))
                        .addOption(Text.translatable(EnumI18nType.OPTION, "copy"))
                        .addOption(Text.translatable(EnumI18nType.OPTION, "cut"))
                        .addOption(Text.translatable(EnumI18nType.OPTION, "paste"))
                        .addOption(Text.translatable(EnumI18nType.OPTION, "delete").setColorArgb(0xFFFF0000))
                        .addTips(Text.translatable(EnumI18nType.TIPS, "cancel_or_confirm"), -1)
                        .setTipsKeyNames(GLFWKeyHelper.getKeyDisplayString(GLFWKey.GLFW_KEY_LEFT_SHIFT))
                        .build(super.font, args.getMouseX(), args.getMouseY(), String.format("奖励按钮:%s", key));
            }
            this.popupOption.setBeforeRender(pasteConsumer);
            args.setConsumed(true);
        }
    }

    /**
     * 处理弹出层选项
     */
    @Override
    public void handlePopupOption(MouseReleasedHandleArgs args) {
        LOGGER.debug("选择了弹出选项:\tButton: {}\tId: {}\tIndex: {}\tContent: {}", args.getButton(), popupOption.getId(), popupOption.getSelectedIndex(), popupOption.getSelectedString());
        String selectedString = popupOption.getSelectedString();
        OperationButtonType buttonType = OperationButtonType.valueOf(currOpButton);
        if (buttonType == null) return;
        EnumRewardRule rule = EnumRewardRule.valueOf(buttonType.toString());
        // 奖励规则类型按钮
        if (popupOption.getId().startsWith("奖励规则类型按钮:")) {
            int opCode = StringUtils.toInt(popupOption.getId().replace("奖励规则类型按钮:", ""));
            // 若选择了清空
            if (popupOption.getSelectedIndex() == 0 && args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                // 并且按住了Control按钮
                if (keyManager.onlyCtrlPressed()) {
                    if (opCode > 200 && opCode <= 299) {
                        RewardConfigManager.addUndoRewardOption(rule);
                        RewardConfigManager.clearRedoList();
                        switch (OperationButtonType.valueOf(opCode)) {
                            case BASE_REWARD: {
                                RewardConfigManager.getRewardConfig().getBaseRewards().clear();
                            }
                            break;
                            case CONTINUOUS_REWARD: {
                                RewardConfigManager.getRewardConfig().getContinuousRewards().clear();
                            }
                            break;
                            case CYCLE_REWARD: {
                                RewardConfigManager.getRewardConfig().getCycleRewards().clear();
                            }
                            break;
                            case YEAR_REWARD: {
                                RewardConfigManager.getRewardConfig().getYearRewards().clear();
                            }
                            break;
                            case MONTH_REWARD: {
                                RewardConfigManager.getRewardConfig().getMonthRewards().clear();
                            }
                            break;
                            case WEEK_REWARD: {
                                RewardConfigManager.getRewardConfig().getWeekRewards().clear();
                            }
                            break;
                            case DATE_TIME_REWARD: {
                                RewardConfigManager.getRewardConfig().getDateTimeRewards().clear();
                            }
                            break;
                            case CUMULATIVE_REWARD: {
                                RewardConfigManager.getRewardConfig().getCumulativeRewards().clear();
                            }
                            break;
                            case RANDOM_REWARD: {
                                RewardConfigManager.getRewardConfig().getRandomRewards().clear();
                            }
                            break;
                            case CDK_REWARD: {
                                RewardConfigManager.getRewardConfig().getCdkRewards().clear();
                            }
                            break;
                        }
                        RewardConfigManager.saveRewardOption();
                        args.setLayout(true);
                        args.setConsumed(true);
                    }
                }
            }
        }
        // 奖励面板按钮
        else if (popupOption.getId().startsWith("奖励面板按钮:")) {
            String[] key = new String[]{""};
            // 粘贴
            if (I18nUtils.getTranslationClient(EnumI18nType.OPTION, "paste").equalsIgnoreCase(selectedString)) {
                editHandler.handlePaste();
            }
            // 物品
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.ITEM.getCode()).equalsIgnoreCase(selectedString)) {
                ItemSelectScreen.Args screenArgs = new ItemSelectScreen.Args()
                        .setParentScreen(this)
                        .setShouldClose(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setOnDataReceived(input -> {
                            if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR && StringUtils.isNotNullOrEmpty(key[0])) {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key[0], input);
                                RewardConfigManager.saveRewardOption();
                            }
                        });
                ItemSelectScreen callbackScreen = new ItemSelectScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 药水效果
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.EFFECT.getCode()).equalsIgnoreCase(selectedString)) {
                EffecrSelectScreen.Args screenArgs = new EffecrSelectScreen.Args()
                        .setParentScreen(this)
                        .setShouldClose(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setOnDataReceived(input -> {
                            if (input != null && ((EffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0 && StringUtils.isNotNullOrEmpty(key[0])) {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key[0], input);
                                RewardConfigManager.saveRewardOption();
                            }
                        });
                EffecrSelectScreen callbackScreen = new EffecrSelectScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 经验点
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.EXP_POINT.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                        .setParentScreen(this)
                        .addWidget(new StringInputScreen.Widget()
                                .setName("count")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_exp_point").setShadow(true))
                                .setRegex(EnumRegex.INTEGER.getRegex())
                                .setDefaultValue("1")
                                .setValidator((input) -> {
                                    if (StringUtils.toInt(input.getValue()) <= 0) {
                                        return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                    }
                                    return null;
                                })
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("probability")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                .setDefaultValue("1")
                        )
                        .setInvisible(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setCallback(input -> {
                            int count = StringUtils.toInt(input.getValue("count"));
                            BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, EnumRewardType.EXP_POINT), EnumRewardType.EXP_POINT, p));
                            RewardConfigManager.saveRewardOption();

                        });
                StringInputScreen callbackScreen = new StringInputScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.ECONOMY.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                        .setParentScreen(this)
                        .addWidget(new StringInputScreen.Widget()
                                .setName("amount")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_economy_point").setShadow(true))
                                .setRegex(EnumRegex.DECIMAL.getRegex())
                                .setDefaultValue("1")
                                // due to regex can only input +- double number, no need to validate it
                                .setValidator((input) -> null)
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("probability")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                .setDefaultValue("1")
                        )
                        .setInvisible(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setCallback(input -> {
                            double amount = StringUtils.toDouble(input.getValue("amount"));
                            BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(amount, EnumRewardType.ECONOMY), EnumRewardType.ECONOMY, p));
                            RewardConfigManager.saveRewardOption();
                        });
                StringInputScreen callbackScreen = new StringInputScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 经验等级
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.EXP_LEVEL.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                        .setParentScreen(this)
                        .addWidget(new StringInputScreen.Widget()
                                .setName("level")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_exp_level").setShadow(true))
                                .setRegex(EnumRegex.INTEGER.getRegex())
                                .setDefaultValue("1")
                                .setValidator((input) -> {
                                    if (StringUtils.toInt(input.getValue()) <= 0) {
                                        return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                    }
                                    return null;
                                })
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("probability")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                .setDefaultValue("1")
                        )
                        .setInvisible(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setCallback(input -> {
                            int count = StringUtils.toInt(input.getValue("level"));
                            BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, EnumRewardType.EXP_LEVEL), EnumRewardType.EXP_LEVEL, p));
                            RewardConfigManager.saveRewardOption();
                        });
                StringInputScreen callbackScreen = new StringInputScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 补签卡
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.SIGN_IN_CARD.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                        .setParentScreen(this)
                        .addWidget(new StringInputScreen.Widget()
                                .setName("card")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_sign_in_card").setShadow(true))
                                .setRegex(EnumRegex.INTEGER.getRegex())
                                .setDefaultValue("1")
                                .setValidator((input) -> {
                                    if (StringUtils.toInt(input.getValue()) <= 0) {
                                        return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                    }
                                    return null;
                                })
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("probability")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                .setDefaultValue("1")
                        )
                        .setInvisible(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setCallback(input -> {
                            int count = StringUtils.toInt(input.getValue("card"));
                            BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(count, EnumRewardType.SIGN_IN_CARD), EnumRewardType.SIGN_IN_CARD, p));
                            RewardConfigManager.saveRewardOption();
                        });
                StringInputScreen callbackScreen = new StringInputScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 进度
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.ADVANCEMENT.getCode()).equalsIgnoreCase(selectedString)) {
                AdvancementSelectScreen.Args screenArgs = new AdvancementSelectScreen.Args()
                        .setParentScreen(this)
                        .setShouldClose(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setOnDataReceived(input -> {
                            if (input != null && StringUtils.isNotNullOrEmpty(input.toString()) && StringUtils.isNotNullOrEmpty(key[0])) {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key[0], input);
                                RewardConfigManager.saveRewardOption();
                            }
                        });
                AdvancementSelectScreen callbackScreen = new AdvancementSelectScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 消息
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.MESSAGE.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                        .setParentScreen(this)
                        .addWidget(new StringInputScreen.Widget()
                                .setName("message")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_message").setShadow(true))
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("probability")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                .setDefaultValue("1")
                        )
                        .setInvisible(() -> StringUtils.isNullOrEmptyEx(key[0]))
                        .setCallback(input -> {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            Component component = Component.literal(input.getValue("message"));
                            BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(component, EnumRewardType.MESSAGE), EnumRewardType.MESSAGE, p));
                            RewardConfigManager.saveRewardOption();
                        });
                StringInputScreen callbackScreen = new StringInputScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 指令
            else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.COMMAND.getCode()).equalsIgnoreCase(selectedString)) {
                StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                        .setParentScreen(this)
                        .addWidget(new StringInputScreen.Widget()
                                .setName("command")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_command").setShadow(true))
                                .setValidator((input) -> {
                                    if (!input.getValue().startsWith("/")) {
                                        return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                    }
                                    return null;
                                })
                        )
                        .addWidget(new StringInputScreen.Widget()
                                .setName("probability")
                                .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                .setDefaultValue("1")
                        )
                        .setInvisible(() -> StringUtils.isNullOrEmpty(key[0]))
                        .setCallback(input -> {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                            RewardConfigManager.addReward(rule, key[0], new Reward(RewardManager.serializeReward(input.getValue("command"), EnumRewardType.COMMAND), EnumRewardType.COMMAND, p));
                            RewardConfigManager.saveRewardOption();
                        });
                StringInputScreen callbackScreen = new StringInputScreen(screenArgs);
                if (rule == EnumRewardRule.CDK_REWARD) {
                    Minecraft.getInstance().setScreen(this.getCdkRuleKeyInputScreen(callbackScreen, rule, key));
                } else if (rule != EnumRewardRule.BASE_REWARD) {
                    Minecraft.getInstance().setScreen(this.getRuleKeyInputScreen(callbackScreen, rule, key));
                } else {
                    key[0] = "base";
                    Minecraft.getInstance().setScreen(callbackScreen);
                }
            }
            // 实现其他奖励类型
        }
        // 奖励按钮
        else if (popupOption.getId().startsWith("奖励按钮:")) {
            String id = popupOption.getId().replace("奖励按钮:", "");
            if (id.startsWith("标题")) {
                String key = id.substring(3);
                // 编辑
                if (Component.translatableClient(EnumI18nType.OPTION, "edit").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        if (rule == EnumRewardRule.CDK_REWARD) {
                            String[] split = key.split("\\|");
                            if (split.length != 4 && split.length != 3 && split.length != 2)
                                split = new String[]{"", DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1)), "-1", "1"};

                            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                                    .setParentScreen(this)
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("key")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_rule_key_" + rule.getCode()).setShadow(true))
                                            .setRegex(EnumRegex.WORD.getRegex())
                                            .setDefaultValue(split[0])
                                            .setValidator((input) -> {
                                                if (!RewardConfigManager.validateKeyName(rule, input.getValue())) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "reward_rule_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("valid")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_valid_until").setShadow(true))
                                            .setDefaultValue(split[1])
                                            .setValidator((input) -> {
                                                if (DateUtils.format(input.getValue()) == null) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "valid_until_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("num")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_num").setShadow(true))
                                            .setRegex(EnumRegex.POSITIVE_INTEGER.getRegex())
                                            .setDefaultValue(split[3])
                                            .setValidator((input) -> {
                                                if (StringUtils.toInt(input.getValue()) <= 0) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "num_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .setCallback(input -> {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateKeyName(rule, key, String.format("%s|%s|-1|%d"
                                                , input.getValue("key")
                                                , input.getValue("valid")
                                                , StringUtils.toInt(input.getValue("num"), 1))
                                        );
                                        RewardConfigManager.saveRewardOption();
                                    });
                            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                        } else {
                            String validator = rule == EnumRewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
                            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                                    .setParentScreen(this)
                                    .addWidget(new StringInputScreen.Widget()
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_rule_key_" + rule.getCode()).setShadow(true))
                                            .setRegex(validator)
                                            .setDefaultValue(key)
                                            .setValidator((input) -> {
                                                if (!RewardConfigManager.validateKeyName(rule, input.getValue())) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "reward_rule_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .setCallback(input -> {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateKeyName(rule, key, input.getFirstValue());
                                        RewardConfigManager.saveRewardOption();
                                    });
                            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                        }
                    }
                }
                // 复制
                else if (Component.translatableClient(EnumI18nType.OPTION, "copy").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCopy();
                    }
                }
                // 裁剪
                else if (Component.translatableClient(EnumI18nType.OPTION, "cut").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCut();
                    }
                }
                // 粘贴
                else if (Component.translatableClient(EnumI18nType.OPTION, "paste").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handlePaste();
                    }
                }
                // 清空
                else if (Component.translatableClient(EnumI18nType.OPTION, "clear").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                        if (keyManager.onlyCtrlPressed()) {
                            RewardConfigManager.addUndoRewardOption(rule);
                            RewardConfigManager.clearRedoList();
                            RewardConfigManager.clearKey(rule, key);
                            RewardConfigManager.saveRewardOption();
                        }
                    }
                }
                // 删除
                else if (Component.translatableClient(EnumI18nType.OPTION, "delete").toString().equalsIgnoreCase(selectedString)) {
                    if (ClientConfig.KEY_OPTION_DELETE.get().stream().anyMatch(keyManager::isKeyAndMousePressed)) {
                        editHandler.handleDelete();
                    }
                }
                // 添加物品
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.ITEM.getCode()).equalsIgnoreCase(selectedString)) {
                    ItemSelectScreen.Args screenArgs = new ItemSelectScreen.Args()
                            .setParentScreen(this)
                            .setOnDataReceived(input -> {
                                if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardConfigManager.addReward(rule, key, input);
                                    RewardConfigManager.saveRewardOption();
                                }
                            });
                    Minecraft.getInstance().setScreen(new ItemSelectScreen(screenArgs));
                }
                // 药水效果
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.EFFECT.getCode()).equalsIgnoreCase(selectedString)) {
                    EffecrSelectScreen.Args screenArgs = new EffecrSelectScreen.Args()
                            .setParentScreen(this)
                            .setOnDataReceived(input -> {
                                if (input != null && ((EffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardConfigManager.addReward(rule, key, input);
                                    RewardConfigManager.saveRewardOption();
                                }
                            });
                    Minecraft.getInstance().setScreen(new EffecrSelectScreen(screenArgs));
                }
                // 经验点
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.EXP_POINT.getCode()).equalsIgnoreCase(selectedString)) {
                    StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                            .setParentScreen(this)
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("point")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_exp_point").setShadow(true))
                                    .setRegex(EnumRegex.INTEGER.getRegex())
                                    .setDefaultValue("1")
                                    .setValidator((input) -> {
                                        if (StringUtils.toInt(input.getValue()) <= 0) {
                                            return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                        }
                                        return null;
                                    })
                            )
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("probability")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                    .setDefaultValue("1")
                            )
                            .setCallback(input -> {
                                int count = StringUtils.toInt(input.getValue("point"));
                                BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, EnumRewardType.EXP_POINT), EnumRewardType.EXP_POINT, p));
                                RewardConfigManager.saveRewardOption();
                            });
                    Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                }
                // 经验等级
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.EXP_LEVEL.getCode()).equalsIgnoreCase(selectedString)) {
                    StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                            .setParentScreen(this)
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("level")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_exp_level").setShadow(true))
                                    .setRegex(EnumRegex.INTEGER.getRegex())
                                    .setDefaultValue("1")
                                    .setValidator((input) -> {
                                        if (StringUtils.toInt(input.getValue()) <= 0) {
                                            return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                        }
                                        return null;
                                    })
                            )
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("probability")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                    .setDefaultValue("1")
                            )
                            .setCallback(input -> {
                                int count = StringUtils.toInt(input.getValue("level"));
                                BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, EnumRewardType.EXP_LEVEL), EnumRewardType.EXP_LEVEL, p));
                                RewardConfigManager.saveRewardOption();
                            });
                    Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                }
                // 补签卡
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.SIGN_IN_CARD.getCode()).equalsIgnoreCase(selectedString)) {
                    StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                            .setParentScreen(this)
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("card")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_sign_in_card").setShadow(true))
                                    .setRegex(EnumRegex.INTEGER.getRegex())
                                    .setDefaultValue("1")
                                    .setValidator((input) -> {
                                        if (StringUtils.toInt(input.getValue()) <= 0) {
                                            return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                        }
                                        return null;
                                    })
                            )
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("probability")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                    .setDefaultValue("1")
                            )
                            .setCallback(input -> {
                                int count = StringUtils.toInt(input.getValue("card"));
                                BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(count, EnumRewardType.SIGN_IN_CARD), EnumRewardType.SIGN_IN_CARD, p));
                                RewardConfigManager.saveRewardOption();
                            });
                    Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                }
                // 进度
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.ADVANCEMENT.getCode()).equalsIgnoreCase(selectedString)) {
                    AdvancementSelectScreen.Args screenArgs = new AdvancementSelectScreen.Args()
                            .setParentScreen(this)
                            .setOnDataReceived(input -> {
                                if (input != null && StringUtils.isNotNullOrEmpty(((ResourceLocation) RewardManager.deserializeReward(input)).toString())) {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardConfigManager.addReward(rule, key, input);
                                    RewardConfigManager.saveRewardOption();
                                }
                            });
                    Minecraft.getInstance().setScreen(new AdvancementSelectScreen(screenArgs));

                }
                // 消息
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.MESSAGE.getCode()).equalsIgnoreCase(selectedString)) {
                    StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                            .setParentScreen(this)
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("message")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_message").setShadow(true))
                            )
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("probability")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                    .setDefaultValue("1")
                            )
                            .setCallback(input -> {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                Component component = Component.literal(input.getValue("message"));
                                BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(component, EnumRewardType.MESSAGE), EnumRewardType.MESSAGE, p));
                                RewardConfigManager.saveRewardOption();
                            });
                    Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                }
                // 指令
                else if (I18nUtils.getTranslationClient(EnumI18nType.WORD, "reward_type_" + EnumRewardType.COMMAND.getCode()).equalsIgnoreCase(selectedString)) {
                    StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                            .setParentScreen(this)
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("command")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_command").setShadow(true))
                                    .setValidator((input) -> {
                                        if (!input.getValue().startsWith("/")) {
                                            return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                        }
                                        return null;
                                    })
                            )
                            .addWidget(new StringInputScreen.Widget()
                                    .setName("probability")
                                    .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                    .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                    .setDefaultValue("1")
                            )
                            .setCallback(input -> {
                                RewardConfigManager.addUndoRewardOption(rule);
                                RewardConfigManager.clearRedoList();
                                BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                RewardConfigManager.addReward(rule, key, new Reward(RewardManager.serializeReward(input.getValue("command"), EnumRewardType.COMMAND), EnumRewardType.COMMAND, p));
                                RewardConfigManager.saveRewardOption();
                            });
                    Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
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
                if (Component.translatableClient(EnumI18nType.OPTION, "edit").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        Reward reward = RewardConfigManager.getReward(rule, key, Integer.parseInt(index)).clone();
                        if (reward.getType() == EnumRewardType.ITEM) {
                            ItemSelectScreen.Args screenArgs = new ItemSelectScreen.Args()
                                    .setParentScreen(this)
                                    .setDefaultItem(reward)
                                    .setOnDataReceived(input -> {
                                        if (input != null && ((ItemStack) RewardManager.deserializeReward(input)).getItem() != Items.AIR) {
                                            RewardConfigManager.addUndoRewardOption(rule);
                                            RewardConfigManager.clearRedoList();
                                            RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), input);
                                            RewardConfigManager.saveRewardOption();
                                        }
                                    });
                            Minecraft.getInstance().setScreen(new ItemSelectScreen(screenArgs));
                        }
                        // 药水效果
                        else if (reward.getType() == EnumRewardType.EFFECT) {
                            EffecrSelectScreen.Args screenArgs = new EffecrSelectScreen.Args()
                                    .setParentScreen(this)
                                    .setDefaultEffect(reward)
                                    .setOnDataReceived(input -> {
                                        if (input != null && ((EffectInstance) RewardManager.deserializeReward(input)).getDuration() > 0) {
                                            RewardConfigManager.addUndoRewardOption(rule);
                                            RewardConfigManager.clearRedoList();
                                            RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), input);
                                            RewardConfigManager.saveRewardOption();
                                        }
                                    });
                            Minecraft.getInstance().setScreen(new EffecrSelectScreen(screenArgs));
                        }
                        // 经验点
                        else if (reward.getType() == EnumRewardType.EXP_POINT) {
                            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                                    .setParentScreen(this)
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("point")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_exp_point").setShadow(true))
                                            .setRegex(EnumRegex.INTEGER.getRegex())
                                            .setDefaultValue(String.valueOf((Integer) RewardManager.deserializeReward(reward)))
                                            .setValidator((input) -> {
                                                if (StringUtils.toInt(input.getValue()) <= 0) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("probability")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                            .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                            .setDefaultValue(StringUtils.toFixedEx(reward.getProbability(), 5))
                                    )
                                    .setCallback(input -> {
                                        int count = StringUtils.toInt(input.getValue("point"));
                                        BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, EnumRewardType.EXP_POINT), EnumRewardType.EXP_POINT, p));
                                        RewardConfigManager.saveRewardOption();
                                    });
                            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                        }
                        // 经验等级
                        else if (reward.getType() == EnumRewardType.EXP_LEVEL) {
                            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                                    .setParentScreen(this)
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("level")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_exp_level").setShadow(true))
                                            .setRegex(EnumRegex.INTEGER.getRegex())
                                            .setDefaultValue(String.valueOf((Integer) RewardManager.deserializeReward(reward)))
                                            .setValidator((input) -> {
                                                if (StringUtils.toInt(input.getValue()) <= 0) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("probability")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                            .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                            .setDefaultValue(StringUtils.toFixedEx(reward.getProbability(), 5))
                                    )
                                    .setCallback(input -> {
                                        int count = StringUtils.toInt(input.getValue("level"));
                                        BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, EnumRewardType.EXP_LEVEL), EnumRewardType.EXP_LEVEL, p));
                                        RewardConfigManager.saveRewardOption();
                                    });
                            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                        }
                        // 补签卡
                        else if (reward.getType() == EnumRewardType.SIGN_IN_CARD) {
                            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                                    .setParentScreen(this)
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("card")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_sign_in_card").setShadow(true))
                                            .setRegex(EnumRegex.INTEGER.getRegex())
                                            .setDefaultValue(String.valueOf((Integer) RewardManager.deserializeReward(reward)))
                                            .setValidator((input) -> {
                                                if (StringUtils.toInt(input.getValue()) <= 0) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("probability")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                            .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                            .setDefaultValue(StringUtils.toFixedEx(reward.getProbability(), 5))
                                    )
                                    .setCallback(input -> {
                                        int count = StringUtils.toInt(input.getValue("card"));
                                        BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(count, EnumRewardType.SIGN_IN_CARD), EnumRewardType.SIGN_IN_CARD, p));
                                        RewardConfigManager.saveRewardOption();
                                    });
                            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                        }
                        // 进度
                        else if (reward.getType() == EnumRewardType.ADVANCEMENT) {
                            AdvancementSelectScreen.Args screenArgs = new AdvancementSelectScreen.Args()
                                    .setParentScreen(this)
                                    .setDefaultAdvancement(reward)
                                    .setOnDataReceived(input -> {
                                        if (input != null && StringUtils.isNotNullOrEmpty(((ResourceLocation) RewardManager.deserializeReward(input)).toString()) && StringUtils.isNotNullOrEmpty(key)) {
                                            RewardConfigManager.addUndoRewardOption(rule);
                                            RewardConfigManager.clearRedoList();
                                            RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), input);
                                            RewardConfigManager.saveRewardOption();
                                        }
                                    });
                            Minecraft.getInstance().setScreen(new AdvancementSelectScreen(screenArgs));
                        }
                        // 消息
                        else if (reward.getType() == EnumRewardType.MESSAGE) {
                            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                                    .setParentScreen(this)
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("message")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_message").setShadow(true))
                                            .setDefaultValue(RewardManager.deserializeReward(reward).toString())
                                    )
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("probability")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                            .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                            .setDefaultValue(StringUtils.toFixedEx(reward.getProbability(), 5))
                                    )
                                    .setCallback(input -> {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        Component textToComponent = Component.literal(input.getValue("message"));
                                        BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(textToComponent, EnumRewardType.MESSAGE), EnumRewardType.MESSAGE, p));
                                        RewardConfigManager.saveRewardOption();
                                    });
                            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                        }
                        // 指令
                        else if (reward.getType() == EnumRewardType.COMMAND) {
                            StringInputScreen.Args screenArgs = new StringInputScreen.Args()
                                    .setParentScreen(this)
                                    .addWidget(new StringInputScreen.Widget()
                                            .setName("command")
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_command").setShadow(true))
                                            .setDefaultValue(RewardManager.deserializeReward(reward))
                                            .setValidator((input) -> {
                                                if (!input.getValue().startsWith("/")) {
                                                    return Component.translatableClient(EnumI18nType.TIPS, "enter_value_s_error", input.getValue()).toString();
                                                }
                                                return null;
                                            })
                                    )
                                    .addWidget(new StringInputScreen.Widget()
                                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                                            .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                                            .setDefaultValue(StringUtils.toFixedEx(reward.getProbability(), 5))
                                    )
                                    .setCallback(input -> {
                                        RewardConfigManager.addUndoRewardOption(rule);
                                        RewardConfigManager.clearRedoList();
                                        BigDecimal p = StringUtils.toBigDecimal(input.getValue("probability"), BigDecimal.ONE);
                                        RewardConfigManager.updateReward(rule, key, Integer.parseInt(index), new Reward(RewardManager.serializeReward(input.getValue("command"), EnumRewardType.COMMAND), EnumRewardType.COMMAND, p));
                                        RewardConfigManager.saveRewardOption();
                                    });
                            Minecraft.getInstance().setScreen(new StringInputScreen(screenArgs));
                        }
                    }
                } else if (Component.translatableClient(EnumI18nType.OPTION, "copy").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCopy();
                    }
                } else if (Component.translatableClient(EnumI18nType.OPTION, "cut").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handleCut();
                    }
                } else if (Component.translatableClient(EnumI18nType.OPTION, "paste").toString().equalsIgnoreCase(selectedString)) {
                    if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
                        editHandler.handlePaste();
                    }
                } else if (Component.translatableClient(EnumI18nType.OPTION, "delete").toString().equalsIgnoreCase(selectedString)) {
                    if (ClientConfig.KEY_OPTION_DELETE.get().stream().anyMatch(keyManager::isKeyAndMousePressed)) {
                        editHandler.handleDelete();
                    }
                }
            }
            args.setLayout(true);
            args.setConsumed(true);
        }
    }

    /**
     * 生成操作按钮的自定义渲染函数
     *
     * @param content 按钮内容
     */
    private Consumer<OperationButton.RenderContext> generateCustomRenderFunction(String content) {
        return context -> {
            int realX = (int) context.button.getRealX();
            int realY = (int) context.button.getRealY();
            double realWidth = context.button.getRealWidth();
            double realHeight = context.button.getRealHeight();
            int realX2 = (int) (context.button.getRealX() + realWidth);
            int realY2 = (int) (context.button.getRealY() + realHeight);
            if (this.currOpButton == context.button.getOperation()) {
                AbstractGui.fill(context.matrixStack, realX + 1, realY, realX2 - 1, realY2, 0x44ACACAC);
            }
            if (context.button.isHovered()) {
                AbstractGui.fill(context.matrixStack, realX, realY, realX2, realY2, 0x99ACACAC);
            }
            AbstractGuiUtils.drawLimitedText(context.matrixStack, super.font, Component.translatableClient(EnumI18nType.WORD, content).toString(), realX + 4, (int) (realY + (realHeight - super.font.lineHeight) / 2), (int) (realWidth - 22), 0xFFEBD4B1);
        };
    }

    @Override
    public void updateLayout() {
        this.leftBarWidth = SakuraSignIn.isRewardOptionBarOpened() ? 100 : 20;
        this.lineItemCount = (super.width - leftBarWidth - leftMargin - rightMargin - rightBarWidth) / (itemIconSize + itemRightMargin);
        // 重置奖励面板坐标
        OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode()).setX(leftBarWidth).setY(0).setWidth(super.width - leftBarWidth - rightBarWidth).setHeight(super.height);
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
        private final RewardOptionScreen screen;

        private EnumRewardRule rule;
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
            rule = EnumRewardRule.valueOf(buttonType.toString());

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
                    // 兑换码
                    if (rule == EnumRewardRule.CDK_REWARD) {
                        StringInputScreen.Args args = new StringInputScreen.Args()
                                .setParentScreen(screen)
                                .addWidget(new StringInputScreen.Widget()
                                        .setName("key")
                                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_rule_key_" + rule.getCode()).setShadow(true))
                                        .setRegex(EnumRegex.WORD.getRegex())
                                        .setDefaultValue(RewardConfigManager.getCdkRewardKey(RewardClipboardManager.deSerializeRewardList().getKey()))
                                        .setValidator((input) -> {
                                            if (!RewardConfigManager.validateKeyName(rule, input.getValue())) {
                                                return Component.translatableClient(EnumI18nType.TIPS, "reward_rule_s_error", input.getValue()).toString();
                                            }
                                            return null;
                                        })
                                )
                                .addWidget(new StringInputScreen.Widget()
                                        .setName("valid")
                                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_valid_until").setShadow(true))
                                        .setDefaultValue(DateUtils.toString(DateUtils.addMonth(DateUtils.getClientDate(), 1)))
                                        .setValidator((input) -> {
                                            if (DateUtils.format(input.getValue()) == null) {
                                                return Component.translatableClient(EnumI18nType.TIPS, "valid_until_s_error", input.getValue()).toString();
                                            }
                                            return null;
                                        })
                                )
                                .addWidget(new StringInputScreen.Widget()
                                        .setName("num")
                                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_num").setShadow(true))
                                        .setRegex(EnumRegex.POSITIVE_INTEGER.getRegex())
                                        .setDefaultValue(RewardConfigManager.getCdkRewardNum(RewardClipboardManager.deSerializeRewardList().getKey()) + "")
                                        .setValidator((input) -> {
                                            if (StringUtils.toInt(input.getValue()) <= 0) {
                                                return Component.translatableClient(EnumI18nType.TIPS, "num_s_error", input.getValue()).toString();
                                            }
                                            return null;
                                        })
                                )
                                .setCallback(input -> {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                                    RewardConfigManager.addKeyName(rule, String.format("%s|%s|-1|%d"
                                            , input.getValue("key")
                                            , input.getValue("valid")
                                            , StringUtils.toInt(input.getValue("num"), 1)), rewardList
                                    );
                                    RewardConfigManager.saveRewardOption();
                                });
                        Minecraft.getInstance().setScreen(new StringInputScreen(args));
                    }
                    // 其他
                    else if (rule != EnumRewardRule.BASE_REWARD) {
                        String validator = rule == EnumRewardRule.RANDOM_REWARD ? "(0?1(\\.0{0,10})?|0(\\.\\d{0,10})?)?" : "[\\d +~/:.T-]*";
                        StringInputScreen.Args args = new StringInputScreen.Args()
                                .setParentScreen(screen)
                                .addWidget(new StringInputScreen.Widget()
                                        .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_rule_key_" + rule.getCode()).setShadow(true))
                                        .setRegex(validator)
                                        .setDefaultValue(RewardClipboardManager.deSerializeRewardList().getKey())
                                        .setValidator((input) -> {
                                            if (!RewardConfigManager.validateKeyName(rule, input.getValue())) {
                                                return Component.translatableClient(EnumI18nType.TIPS, "reward_rule_s_error", input.getValue()).toString();
                                            }
                                            return null;
                                        })
                                )
                                .setCallback(input -> {
                                    RewardConfigManager.addUndoRewardOption(rule);
                                    RewardConfigManager.clearRedoList();
                                    RewardList rewardList = RewardClipboardManager.deSerializeRewardList().toRewardList();
                                    RewardConfigManager.addKeyName(rule, input.getValue(0), rewardList);
                                    RewardConfigManager.saveRewardOption();
                                });
                        Minecraft.getInstance().setScreen(new StringInputScreen(args));
                    }
                    // 基础奖励
                    else {
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
            // 基础奖励标题
            else if (currRewardButton.startsWith("标题") && EnumRewardRule.BASE_REWARD.equals(rule)) {
                // region 整点没用的
                screen.deleteBaseCount++;
                if (screen.deleteBaseCount < 5) {
                    NotificationManager.get().addNotification(NotificationManager.Notification
                            .ofComponentWithBlack(Component.translatableClient(EnumI18nType.MESSAGE, "delete_base_title_" + screen.deleteBaseCount))
                            .setBgArgb(0x99FFFF55));
                } else if (screen.deleteBaseCount < 8) {
                    NotificationManager.get().addNotification(NotificationManager.Notification
                            .ofComponentWithBlack(Component.translatableClient(EnumI18nType.MESSAGE, "delete_base_title_" + screen.deleteBaseCount))
                            .setBgArgb(0x99FF5555));
                } else if (screen.deleteBaseCount == 8) {
                    long currentTimeMillis = System.currentTimeMillis();
                    for (int i = 12; i > 0; i--) {
                        int a = (255 - i * 8) & 0xFF;
                        int r = (0xFF5555 >> 16) & 0xFF;
                        int g = (0xFF5555 >> 8) & 0xFF;
                        int b = 0xFF5555 & 0xFF;
                        NotificationManager.get().addNotification(NotificationManager.Notification
                                .ofComponentWithBlack(Component.translatableClient(EnumI18nType.MESSAGE, "delete_base_title_8", i * 5))
                                .setScheduledTime(currentTimeMillis + (12 - i) * 5 * 1000L)
                                .setBgArgb((a << 24) | (r << 16) | (g << 8) | b)
                        );
                        if (i == 1) {
                            for (int j = 4; j > 0; j--) {
                                NotificationManager.get().addNotification(NotificationManager.Notification
                                        .ofComponentWithBlack(Component.translatableClient(EnumI18nType.MESSAGE, "delete_base_title_8", j))
                                        .setScheduledTime(currentTimeMillis + (60 - j) * 1000L)
                                        .setBgArgb(((255 - j) << 24) | (r << 16) | (g << 8) | b)
                                );
                            }
                        }
                    }
                    NotificationManager.get().addNotification(NotificationManager.Notification
                            .ofComponentWithBlack(Component.translatableClient(EnumI18nType.MESSAGE, "delete_base_title_9"))
                            .setScheduledTime(currentTimeMillis + 63 * 1000L)
                    );
                }
                // endregion 整点没用的
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
        super(Component.translatableClient(EnumI18nType.TITLE, "reward_option_title").toTextComponent());
    }

    @Override
    protected void init_() {
        this.leftBarTitleHeight = 5 * 2 + super.font.lineHeight;
        // 初始化材质及材质坐标信息
        SakuraUtils.loadThemeTexture();
        OP_BUTTONS.put(OperationButtonType.REWARD_PANEL.getCode(), new OperationButton(OperationButtonType.REWARD_PANEL.getCode(), context -> {
        })
                .setTransparentCheck(false));
        OP_BUTTONS.put(OperationButtonType.OPEN.getCode(), new OperationButton(OperationButtonType.OPEN.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(new Coordinate().setX(4).setY((super.height - 16) / 2.0).setWidth(16).setHeight(16))
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getArrowUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getArrowHoverUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getArrowTapUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EnumI18nType.TIPS, "open_sidebar").toString()));
        OP_BUTTONS.put(OperationButtonType.CLOSE.getCode(), new OperationButton(OperationButtonType.CLOSE.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(new Coordinate().setX(80).setY((5 * 2 + super.font.lineHeight - 16) / 2.0).setWidth(16).setHeight(16))
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getArrowUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getArrowHoverUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getArrowTapUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setFlipHorizontal(true)
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EnumI18nType.TIPS, "close_sidebar").toString()));
        OP_BUTTONS.put(OperationButtonType.BASE_REWARD.getCode()
                , new OperationButton(OperationButtonType.BASE_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.BASE_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CONTINUOUS_REWARD.getCode()
                , new OperationButton(OperationButtonType.CONTINUOUS_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.CONTINUOUS_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1)).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CYCLE_REWARD.getCode()
                , new OperationButton(OperationButtonType.CYCLE_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.CYCLE_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 2).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.YEAR_REWARD.getCode()
                , new OperationButton(OperationButtonType.YEAR_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.YEAR_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 3).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.MONTH_REWARD.getCode()
                , new OperationButton(OperationButtonType.MONTH_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.MONTH_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 4).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.WEEK_REWARD.getCode()
                , new OperationButton(OperationButtonType.WEEK_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.WEEK_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 5).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.DATE_TIME_REWARD.getCode()
                , new OperationButton(OperationButtonType.DATE_TIME_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.DATE_TIME_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 6).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CUMULATIVE_REWARD.getCode()
                , new OperationButton(OperationButtonType.CUMULATIVE_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.CUMULATIVE_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 7).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.RANDOM_REWARD.getCode()
                , new OperationButton(OperationButtonType.RANDOM_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.RANDOM_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 8).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.CDK_REWARD.getCode()
                , new OperationButton(OperationButtonType.CDK_REWARD.getCode(), this.generateCustomRenderFunction(SakuraUtils.getRewardRuleI18nKeyName(EnumRewardRule.CDK_REWARD)))
                        .setX(0).setY(this.leftBarTitleHeight + (this.leftBarTitleHeight - 1) * 9).setWidth(100).setHeight(this.leftBarTitleHeight - 2));
        OP_BUTTONS.put(OperationButtonType.OFFSET_Y.getCode(), new OperationButton(OperationButtonType.OFFSET_Y.getCode(), context -> {
            AbstractGuiUtils.drawString(context.matrixStack, super.font, "OY:", super.width - rightBarWidth + 1, super.height - font.lineHeight * 2 - 2, 0xFFACACAC);
            AbstractGuiUtils.drawLimitedText(context.matrixStack, super.font, String.valueOf((int) yOffset), super.width - rightBarWidth + 1, super.height - font.lineHeight - 2, rightBarWidth, 0xFFACACAC);
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
                .setTooltip(Component.translatableClient(EnumI18nType.TIPS, "download_reward_config").toString()));
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
                .setTooltip(Component.translatableClient(EnumI18nType.TIPS, "open_config_folder").toString()));
        OP_BUTTONS.put(OperationButtonType.SORT.getCode(), new OperationButton(OperationButtonType.SORT.getCode(), SakuraSignIn.getThemeTexture())
                .setCoordinate(SakuraSignIn.getThemeTextureCoordinate().getSortUV())
                .setNormal(SakuraSignIn.getThemeTextureCoordinate().getSortUV()).setHover(SakuraSignIn.getThemeTextureCoordinate().getSortUV()).setTap(SakuraSignIn.getThemeTextureCoordinate().getSortUV())
                .setTextureWidth(SakuraSignIn.getThemeTextureCoordinate().getTotalWidth())
                .setTextureHeight(SakuraSignIn.getThemeTextureCoordinate().getTotalHeight())
                .setX(super.width - rightBarWidth + 1).setY(82).setWidth(18).setHeight(18)
                .setHoverFgColor(0xAA808080).setTapFgColor(0xAAA0A0A0)
                .setTransparentCheck(false)
                .setTooltip(Component.translatableClient(EnumI18nType.TIPS, "reward_rule_sort").toString()));
        this.updateLayout();

        tips = Text.translatable(EnumI18nType.TIPS, "reward_option_screen_tips");
    }

    @Override
    public void render_(MatrixStack matrixStack, float partialTicks) {
        this.ms = matrixStack;
        // 绘制缩放背景纹理
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
            tips.setMatrixStack(matrixStack).setFont(super.font);
            int textHeight = AbstractGuiUtils.multilineTextHeight(tips);
            int textWidth = AbstractGuiUtils.multilineTextWidth(tips);
            x = this.leftBarWidth + ((super.width - this.leftBarWidth - this.rightBarWidth) - textWidth) / 2.0f;
            y = (super.height - (textHeight + 4)) / 2.0f;
            AbstractGuiUtils.drawString(tips, x, y);
        }
        // 绘制奖励项目
        else {
            this.renderRewardList(matrixStack);
        }

        // 绘制左侧边栏列表背景
        AbstractGui.fill(matrixStack, 0, 0, leftBarWidth, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(matrixStack, 0, 0, leftBarWidth, super.height, 1, 0xFF000000);
        // 绘制左侧边栏列表标题
        if (SakuraSignIn.isRewardOptionBarOpened()) {
            AbstractGui.drawString(matrixStack, super.font, Component.translatableClient(EnumI18nType.TITLE, "reward_rule_type").toString(), 4, 5, 0xFFACACAC);
            AbstractGui.fill(matrixStack, 0, leftBarTitleHeight, leftBarWidth, leftBarTitleHeight - 1, 0xAA000000);
        }
        // 绘制右侧边栏列表背景
        AbstractGui.fill(matrixStack, super.width - rightBarWidth, 0, super.width, super.height, 0xAA000000);
        AbstractGuiUtils.fillOutLine(matrixStack, super.width - rightBarWidth, 0, rightBarWidth, super.height, 1, 0xFF000000);

        // 渲染操作按钮
        for (Integer op : OP_BUTTONS.keySet()) {
            OperationButton button = OP_BUTTONS.get(op);
            // 展开类按钮仅在关闭时绘制
            if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.OPEN.getCode()))) {
                if (!SakuraSignIn.isRewardOptionBarOpened()) {
                    button.render(matrixStack, keyManager);
                }
            }
            // 收起类按钮仅在展开时绘制
            else if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.CLOSE.getCode()))) {
                if (SakuraSignIn.isRewardOptionBarOpened()) {
                    button.render(matrixStack, keyManager);
                }
            }
            // 绘制其他按钮
            else {
                if (op == OperationButtonType.OFFSET_Y.getCode()) {
                    button.setTooltip(Component.translatableClient(EnumI18nType.TIPS, "y_offset", StringUtils.toFixedEx(this.yOffset, 1)).toString());
                } else if (op == OperationButtonType.REWARD_PANEL.getCode()) {
                    // 绘制选中边框
                    if ("panel".equals(this.currRewardButton)) {
                        AbstractGuiUtils.fillOutLine(matrixStack,
                                (int) button.getRealX() - 1,
                                (int) button.getRealY(),
                                (int) button.getRealWidth() + 2,
                                (int) button.getRealHeight(),
                                1,
                                0x88FFF13B);
                    }
                }
                button.render(matrixStack, keyManager);
            }
        }
        // 渲染操作按钮 提示
        for (Integer op : OP_BUTTONS.keySet()) {
            OperationButton button = OP_BUTTONS.get(op);
            // 展开类按钮仅在关闭时绘制
            if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.OPEN.getCode()))) {
                if (!SakuraSignIn.isRewardOptionBarOpened()) {
                    button.renderPopup(matrixStack, keyManager);
                }
            }
            // 收起类按钮仅在展开时绘制
            else if (String.valueOf(op).startsWith(String.valueOf(OperationButtonType.CLOSE.getCode()))) {
                if (SakuraSignIn.isRewardOptionBarOpened()) {
                    button.renderPopup(matrixStack, keyManager);
                }
            }
            // 绘制其他按钮
            else {
                if (op == OperationButtonType.OFFSET_Y.getCode()) {
                    button.setTooltip(Component.translatableClient(EnumI18nType.TIPS, "y_offset", StringUtils.toFixedEx(this.yOffset, 1)).toString());
                }
                // 帮助按钮
                else if (op == OperationButtonType.HELP.getCode()) {
                    if (keyManager.onlyShiftPressed()) {
                        button.setTooltip(Component.translatableClient(EnumI18nType.TIPS, "help_button_shift").toString());
                    } else {
                        button.setTooltip(Component.translatableClient(EnumI18nType.TIPS, "help_button").toString());
                    }
                } else if (op == OperationButtonType.UPLOAD.getCode()) {
                    ClientPlayerEntity player = Minecraft.getInstance().player;
                    if (player != null && player.hasPermissions(ServerConfig.PERMISSION_EDIT_REWARD.get())) {
                        button.setTooltip(Component.translatableClient(EnumI18nType.TIPS, "upload_reward_config").toString())
                                .setHoverFgColor(0xAA808080).setTapFgColor(0xAAA0A0A0);
                    } else {
                        button.setTooltip(Text.translatable(EnumI18nType.TIPS, "upload_reward_config_no_permission").setColorArgb(0xFFFF0000))
                                .setHoverFgColor(0xAA808080).setTapFgColor(0xAA808080);
                    }
                }
                button.renderPopup(matrixStack, keyManager);
            }
        }

    }

    /**
     * 窗口关闭时
     */
    @Override
    public void removed_() {
    }

    @Override
    public void mouseClicked_(MouseClickedHandleArgs args) {
        this.yOffsetOld = this.yOffset;
        this.handledRewardButton = false;
        if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT || args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
            OP_BUTTONS.forEach((key, value) -> {
                if (value.isHovered()) {
                    value.setPressed(true);
                }
            });
            REWARD_BUTTONS.forEach((key, value) -> {
                if (value.isHovered()) {
                    value.setPressed(true);
                }
            });
        }
    }

    @Override
    public void mouseReleased_(MouseReleasedHandleArgs args) {
        if (!keyManager.isMouseMoved()) {
            if (args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT || args.getButton() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                // 控制按钮
                OP_BUTTONS.forEach((key, value) -> {
                    // 忽略奖励配置列表面板, 后置处理
                    if (key != OperationButtonType.REWARD_PANEL.getCode()) {
                        if (value.isHovered() && value.isPressed()) {
                            this.handleOperation(args, value);
                            if (args.isConsumed()) {
                                this.currRewardButton = null;
                            }
                        }
                        value.setPressed(false);
                    }
                });
                // 奖励按钮
                if (!args.isConsumed()) {
                    REWARD_BUTTONS.forEach((key, value) -> {
                        if (value.isHovered() && value.isPressed()) {
                            this.handleRewardOption(args, key, value);
                        }
                        value.setPressed(false);
                    });
                }
                // 奖励配置列表面板
                if (!args.isConsumed()) {
                    OperationButton rewardPanel = OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode());
                    if (rewardPanel.isHovered() && rewardPanel.isPressed()) {
                        this.handleOperation(args, rewardPanel);
                        rewardPanel.setPressed(false);
                    }
                }
            }
        }
    }

    @Override
    public void mouseMoved_() {
        OP_BUTTONS.forEach((key, value) -> {
            if (SakuraSignIn.isRewardOptionBarOpened()) {
                // 若为开启状态则隐藏开启按钮及其附属按钮
                if (!String.valueOf(key).startsWith(String.valueOf(OperationButtonType.OPEN.getCode()))) {
                    value.setHovered(value.isMouseOverEx(keyManager.getMouseX(), keyManager.getMouseY()));
                } else {
                    value.setHovered(false);
                }
            } else {
                // 若为关闭状态则隐藏关闭按钮及其附属按钮
                if (!String.valueOf(key).startsWith(String.valueOf(OperationButtonType.CLOSE.getCode()))) {
                    value.setHovered(value.isMouseOverEx(keyManager.getMouseX(), keyManager.getMouseY()));
                } else {
                    value.setHovered(false);
                }
            }
            // 是否按下并拖动奖励面板
            if (OperationButtonType.REWARD_PANEL.getCode() == key) {
                if (value.isPressed() && keyManager.isMouseDragged()) {
                    this.setYOffset(this.yOffsetOld + (keyManager.getMouseY() - keyManager.getMouseDownY()));
                }
            }
        });
        REWARD_BUTTONS.forEach((key, value) -> value.setHovered(value.isMouseOverEx(keyManager.getMouseX(), keyManager.getMouseY())));
    }

    @Override
    public void mouseScrolled_(MouseScoredHandleArgs args) {
        if (!popupOption.addScrollOffset(args.getDelta())) {
            if (OP_BUTTONS.get(OperationButtonType.REWARD_PANEL.getCode()).isHovered()) {
                this.setYOffset(yOffset + args.getDelta());
            }
        }
    }

    @Override
    public void keyPressed_(KeyPressedHandleArgs args) {
        if (keyManager.isKeyPressed(GLFWKey.GLFW_KEY_ESCAPE)) {
            args.setConsumed(true);
            this.onClose();
        }
    }

    @Override
    public void keyReleased_(KeyReleasedHandleArgs args) {

        // Ctrl + C
        if (ClientConfig.KEY_OPTION_COPY.get().stream().anyMatch(keyManager::isKeyPressed)) {
            args.setConsumed(editHandler.handleCopy());
        }
        // Ctrl + V
        else if (ClientConfig.KEY_OPTION_PASTE.get().stream().anyMatch(keyManager::isKeyPressed)) {
            args.setConsumed(editHandler.handlePaste());
        }
        // Ctrl + X
        else if (ClientConfig.KEY_OPTION_CUT.get().stream().anyMatch(keyManager::isKeyPressed)) {
            args.setConsumed(editHandler.handleCut());
        }
        // Ctrl + Y / DELETE
        else if (ClientConfig.KEY_OPTION_DELETE.get().stream().anyMatch(keyManager::isKeyPressed)) {
            args.setConsumed(editHandler.handleDelete());
        }
        // Ctrl + Z
        else if (ClientConfig.KEY_OPTION_UNDO.get().stream().anyMatch(keyManager::isKeyPressed)) {
            args.setConsumed(editHandler.handleUndo());
        }
        // Ctrl + Shift + Z
        else if (ClientConfig.KEY_OPTION_REDO.get().stream().anyMatch(keyManager::isKeyPressed)) {
            args.setConsumed(editHandler.handleRedo());
        }

    }

    @Override
    void onClose_() {
    }

    /**
     * 窗口打开时是否暂停游戏
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
