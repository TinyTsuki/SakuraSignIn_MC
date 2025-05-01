package xin.vanilla.sakura.screen;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.StringList;
import xin.vanilla.sakura.enums.EI18nType;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.rewards.impl.EffectRewardParser;
import xin.vanilla.sakura.screen.component.KeyEventManager;
import xin.vanilla.sakura.screen.component.OperationButton;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.util.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static xin.vanilla.sakura.config.RewardConfigManager.GSON;

public class EffecrSelectScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private final KeyEventManager keyManager = new KeyEventManager();

    private static final Component TITLE = Component.literal("ItemSelectScreen");

    private final List<Effect> allEffectList = Registry.MOB_EFFECT.stream().collect(Collectors.toList());
    private final List<Effect> playerEffectList = this.getPlayerEffectList();
    // 每页显示行数
    private final int maxLine = 5;

    /**
     * 父级 Screen
     */
    private final Screen previousScreen;
    /**
     * 输入数据回调1
     */
    private final Consumer<Reward> onDataReceived1;
    /**
     * 输入数据回调2
     */
    private final Function<Reward, String> onDataReceived2;
    /**
     * 是否要显示该界面, 若为false则直接关闭当前界面并返回到调用者的 Screen
     */
    private final Supplier<Boolean> shouldClose;
    /**
     * 输入框
     */
    private TextFieldWidget inputField;
    /**
     * 输入框文本
     */
    private String inputFieldText = "";
    /**
     * 搜索结果
     */
    private final List<Effect> effectList = new ArrayList<>();
    /**
     * 操作按钮
     */
    private final Map<Integer, OperationButton> OP_BUTTONS = new HashMap<>();
    /**
     * 药水效果按钮
     */
    private final List<OperationButton> EFFECT_BUTTONS = new ArrayList<>();
    /**
     * 当前选择的药水效果
     */
    private Reward currentEffect = new Reward(new EffectInstance(Effects.LUCK), ERewardType.EFFECT);
    /**
     * 奖励概率
     */
    private BigDecimal probability = BigDecimal.ONE;
    /**
     * 背包模式
     */
    private boolean playerMode = false;

    private int bgX;
    private int bgY;
    private final double margin = 3;
    private double effectBgX = this.bgX + margin;
    private double effectBgY = this.bgY + 20;

    // region 滚动条相关

    /**
     * 当前滚动偏移量
     */
    @Getter
    private int scrollOffset = 0;
    // 鼠标按下时的X坐标
    private double mouseDownX = -1;
    // 鼠标按下时的Y坐标
    private double mouseDownY = -1;

    // Y坐标偏移
    private double scrollOffsetOld;
    private double outScrollX;
    private double outScrollY;
    private int outScrollWidth = 5;
    private int outScrollHeight;
    private double inScrollHeight;
    private double inScrollY;

    // endregion 滚动条相关

    /**
     * 操作按钮类型
     */
    @Getter
    enum OperationButtonType {
        TYPE(1),
        EFFECT(2),
        DURATION(3),
        AMPLIFIER(4),
        SLIDER(5),
        PROBABILITY(6),
        ;

        final int code;

        OperationButtonType(int code) {
            this.code = code;
        }

        static OperationButtonType valueOf(int code) {
            return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
        }
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived, @NonNull Reward defaultEffect, Supplier<Boolean> shouldClose) {
        super(TITLE.toTextComponent());
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentEffect = defaultEffect;
        this.probability = defaultEffect.getProbability();
        this.shouldClose = shouldClose;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived, @NonNull Reward defaultEffect, Supplier<Boolean> shouldClose) {
        super(TITLE.toTextComponent());
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentEffect = defaultEffect;
        this.probability = defaultEffect.getProbability();
        this.shouldClose = shouldClose;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived, @NonNull Reward defaultEffect) {
        super(TITLE.toTextComponent());
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentEffect = defaultEffect;
        this.probability = defaultEffect.getProbability();
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived, @NonNull Reward defaultEffect) {
        super(TITLE.toTextComponent());
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentEffect = defaultEffect;
        this.probability = defaultEffect.getProbability();
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived) {
        super(TITLE.toTextComponent());
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived) {
        super(TITLE.toTextComponent());
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.shouldClose = null;
    }

    @Override
    protected void init() {
        if (this.shouldClose != null && Boolean.TRUE.equals(this.shouldClose.get()))
            Minecraft.getInstance().setScreen(previousScreen);
        this.updateSearchResults();
        this.updateLayout();
        // 创建文本输入框
        this.inputField = AbstractGuiUtils.newTextFieldWidget(this.font, bgX, bgY, 112, 15, Component.empty());
        this.inputField.setValue(this.inputFieldText);
        this.addButton(this.inputField);
        // 创建提交按钮
        this.addButton(AbstractGuiUtils.newButton((int) (this.bgX + 56 + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (56 - this.margin * 2), 20
                , Component.translatableClient(EI18nType.OPTION, "submit"), button -> {
                    if (this.currentEffect == null) {
                        // 关闭当前屏幕并返回到调用者的 Screen
                        Minecraft.getInstance().setScreen(previousScreen);
                    } else {
                        // 获取选择的数据，并执行回调
                        EffectInstance effectInstance = RewardManager.deserializeReward(this.currentEffect);
                        if (onDataReceived1 != null) {
                            onDataReceived1.accept(new Reward(effectInstance, ERewardType.EFFECT, this.probability));
                            Minecraft.getInstance().setScreen(previousScreen);
                        } else if (onDataReceived2 != null) {
                            String result = onDataReceived2.apply(new Reward(effectInstance, ERewardType.EFFECT, this.probability));
                            if (StringUtils.isNotNullOrEmpty(result)) {
                                // this.errorText = Text.literal(result).setColor(0xFFFF0000);
                            } else {
                                Minecraft.getInstance().setScreen(previousScreen);
                            }
                        }
                    }
                }));
        // 创建取消按钮
        this.addButton(AbstractGuiUtils.newButton((int) (this.bgX + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (56 - this.margin * 2), 20
                , Component.translatableClient(EI18nType.OPTION, "cancel")
                , button -> Minecraft.getInstance().setScreen(previousScreen)));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
        keyManager.refresh(mouseX, mouseY);
        // 绘制背景
        this.renderBackground(matrixStack);
        AbstractGuiUtils.fill(matrixStack, (int) (this.bgX - this.margin), (int) (this.bgY - this.margin), (int) (112 + this.margin * 2), (int) (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + 20 + margin * 2 + 5), 0xCCC6C6C6, 2);
        AbstractGuiUtils.fillOutLine(matrixStack, (int) (this.effectBgX - this.margin), (int) (this.effectBgY - this.margin), 104, (int) ((AbstractGuiUtils.ITEM_ICON_SIZE + this.margin) * this.maxLine + this.margin), 1, 0xFF000000, 1);
        super.render(matrixStack, mouseX, mouseY, delta);
        // 保存输入框的文本, 防止窗口重绘时输入框内容丢失
        this.inputFieldText = this.inputField.getValue();

        this.renderButton(matrixStack);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        keyManager.mouseScrolled(delta, mouseX, mouseY);
        this.setScrollOffset(this.getScrollOffset() - delta);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        keyManager.mouseClicked(button, mouseX, mouseY);
        AtomicBoolean flag = new AtomicBoolean(false);
        if (button == GLFWKey.GLFW_MOUSE_BUTTON_4) {
            Minecraft.getInstance().setScreen(previousScreen);
            flag.set(true);
        } else if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT || button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
            OP_BUTTONS.forEach((key, value) -> {
                if (value.isHovered()) {
                    value.setPressed(true);
                    // 若是滑块
                    if (key == OperationButtonType.SLIDER.getCode()) {
                        this.scrollOffsetOld = this.getScrollOffset();
                        this.mouseDownX = mouseX;
                        this.mouseDownY = mouseY;
                    }
                }
            });
            // 药水效果按钮
            EFFECT_BUTTONS.forEach(bt -> bt.setPressed(bt.isHovered()));
        }
        return flag.get() ? flag.get() : super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        keyManager.refresh(mouseX, mouseY);
        AtomicBoolean flag = new AtomicBoolean(false);
        AtomicBoolean updateSearchResults = new AtomicBoolean(false);
        if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT || button == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
            // 控制按钮
            OP_BUTTONS.forEach((key, value) -> {
                if (value.isHovered() && value.isPressed()) {
                    this.handleOperation(value, button, flag, updateSearchResults);
                }
                value.setPressed(false);
            });
            // 药水效果按钮
            EFFECT_BUTTONS.forEach(bt -> {
                if (bt.isHovered() && bt.isPressed()) {
                    this.handleEffect(bt, button, flag);
                }
                bt.setPressed(false);
            });
            this.mouseDownX = -1;
            this.mouseDownY = -1;
            if (updateSearchResults.get()) {
                this.updateSearchResults();
            }
        }
        keyManager.mouseReleased(button, mouseX, mouseY);
        return flag.get() ? flag.get() : super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyManager.mouseMoved(mouseX, mouseY);
        // 控制按钮
        OP_BUTTONS.forEach((key, value) -> {
            value.setHovered(value.isMouseOverEx(mouseX, mouseY));
            if (key == OperationButtonType.SLIDER.getCode()) {
                if (value.isPressed() && this.mouseDownX != -1 && this.mouseDownY != -1) {
                    // 一个像素对应多少滚动偏移量
                    double scale = Math.ceil((double) effectList.size() - maxLine) / (this.outScrollHeight - 2);
                    this.setScrollOffset(this.scrollOffsetOld + (mouseY - this.mouseDownY) * scale);
                }
            }
        });
        // 药水效果按钮
        EFFECT_BUTTONS.forEach(bt -> bt.setHovered(bt.isMouseOverEx(mouseX, mouseY)));
        super.mouseMoved(mouseX, mouseY);
    }

    /**
     * 重写键盘事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        keyManager.keyPressed(keyCode);
        if (keyCode == GLFWKey.GLFW_KEY_ESCAPE || (keyCode == GLFWKey.GLFW_KEY_BACKSPACE && !this.inputField.isFocused())) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        } else if ((keyCode == GLFWKey.GLFW_KEY_ENTER || keyCode == GLFWKey.GLFW_KEY_KP_ENTER) && this.inputField.isFocused()) {
            this.updateSearchResults();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        keyManager.keyReleased(keyCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<Effect> getPlayerEffectList() {
        List<Effect> result = new ArrayList<>();
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            result.addAll(player.getActiveEffectsMap().keySet());
        }
        return result;
    }

    private void updateLayout() {
        this.bgX = this.width / 2 - 56;
        this.bgY = this.height / 2 - 63;
        this.effectBgX = this.bgX + margin;
        this.effectBgY = this.bgY + 20;

        // 初始化操作按钮
        this.OP_BUTTONS.put(OperationButtonType.TYPE.getCode(), new OperationButton(OperationButtonType.TYPE.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(this.playerMode ? Items.CHEST : Items.COMPASS);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.translatable(EI18nType.TIPS, (this.playerMode ? "effect_select_list_player_mode" : "effect_select_list_all_mode"), (this.playerMode ? playerEffectList.size() : allEffectList.size()));
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.EFFECT.getCode(), new OperationButton(OperationButtonType.EFFECT.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            AbstractGuiUtils.drawEffectIcon(context.matrixStack, this.font, RewardManager.deserializeReward(this.currentEffect), SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), (int) context.button.getX() + 2, (int) context.button.getY() + 2, AbstractGuiUtils.ITEM_ICON_SIZE, AbstractGuiUtils.ITEM_ICON_SIZE, false);
            context.button.setTooltip(Text.fromTextComponent(((EffectInstance) RewardManager.deserializeReward(this.currentEffect)).getEffect().getDisplayName().copy()));
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.DURATION.getCode(), new OperationButton(OperationButtonType.DURATION.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.CLOCK);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.translatable(EI18nType.TIPS, "set_duration_s", ((EffectInstance) RewardManager.deserializeReward(this.currentEffect)).getDuration());
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 2).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.AMPLIFIER.getCode(), new OperationButton(OperationButtonType.AMPLIFIER.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.ANVIL);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.translatable(EI18nType.TIPS, "set_amplifier_s", StringUtils.intToRoman(((EffectInstance) RewardManager.deserializeReward(this.currentEffect)).getAmplifier() + 1));
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 3).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.PROBABILITY.getCode(), new OperationButton(OperationButtonType.PROBABILITY.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            AbstractGuiUtils.drawEffectIcon(context.matrixStack, super.font, new EffectInstance(Effects.LUCK), (int) context.button.getX() + 2, (int) context.button.getY() + 2, false);
            Text text = Text.translatable(EI18nType.TIPS, "set_probability_f", this.probability.multiply(new BigDecimal(100)).floatValue());
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 4).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));

        // 滚动条
        this.OP_BUTTONS.put(OperationButtonType.SLIDER.getCode(), new OperationButton(OperationButtonType.SLIDER.getCode(), context -> {
            // 背景宽高
            double bgWidth = 104;
            double bgHeight = (AbstractGuiUtils.ITEM_ICON_SIZE + margin) * maxLine - margin;
            // 绘制滚动条
            this.outScrollX = effectBgX + bgWidth;
            this.outScrollY = effectBgY - this.margin + 1;
            this.outScrollWidth = 5;
            this.outScrollHeight = (int) (bgHeight + this.margin + 1);
            // 滚动条百分比
            double inScrollWidthScale = effectList.size() > maxLine ? (double) maxLine / effectList.size() : 1;
            // 多出来的行数
            double outLine = Math.max(effectList.size() - maxLine, 0);
            // 多出来的每行所占的空余条长度
            double outCellHeight = outLine == 0 ? 0 : (1 - inScrollWidthScale) * (outScrollHeight - 2) / outLine;
            // 滚动条上边距长度
            double inScrollTopHeight = this.getScrollOffset() * outCellHeight;
            // 滚动条高度
            this.inScrollHeight = Math.max(2, (outScrollHeight - 2) * inScrollWidthScale);
            this.inScrollY = outScrollY + inScrollTopHeight + 1;
            // 绘制滚动条外层背景
            AbstractGuiUtils.fill(context.matrixStack, (int) this.outScrollX, (int) this.outScrollY, this.outScrollWidth, this.outScrollHeight, 0xCC232323);
            // 绘制滚动条滑块
            int color = context.button.isHovered() ? 0xCCFFFFFF : 0xCC8B8B8B;
            AbstractGuiUtils.fill(context.matrixStack, (int) this.outScrollX, (int) Math.ceil(this.inScrollY), this.outScrollWidth, (int) this.inScrollHeight, color);
            context.button.setX(this.outScrollX).setY(this.outScrollY).setWidth(this.outScrollWidth).setHeight(this.outScrollHeight);
        }));

        // 效果列表
        this.EFFECT_BUTTONS.clear();
        for (int i = 0; i < maxLine; i++) {
            EFFECT_BUTTONS.add(new OperationButton(i, context -> {
                int i1 = context.button.getOperation();
                int index = (effectList.size() > maxLine ? this.getScrollOffset() : 0) + i1;
                if (index >= 0 && index < effectList.size()) {
                    Effect effect = effectList.get(index);
                    // 效果图标在弹出层中的 x 位置
                    double effectX = effectBgX;
                    // 效果图标在弹出层中的 y 位置
                    double effectY = effectBgY + i1 * (AbstractGuiUtils.ITEM_ICON_SIZE + margin);
                    // 绘制背景
                    int bgColor;
                    if (context.button.isHovered() || effect == ((EffectInstance) RewardManager.deserializeReward(this.currentEffect)).getEffect()) {
                        bgColor = 0xEE7CAB7C;
                    } else {
                        bgColor = 0xEE707070;
                    }
                    context.button.setX(effectX - 1).setY(effectY - 1).setWidth(100).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 2)
                            .setId(EffectRewardParser.getId(effect));

                    AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), bgColor);
                    AbstractGuiUtils.drawLimitedText(Text.literal(EffectRewardParser.getDisplayName(effect)).setMatrixStack(context.matrixStack).setFont(this.font), context.button.getX() + AbstractGuiUtils.ITEM_ICON_SIZE + this.margin * 2, context.button.getY() + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 - this.font.lineHeight) / 2.0, (int) context.button.getWidth() - AbstractGuiUtils.ITEM_ICON_SIZE - 4);
                    AbstractGuiUtils.drawEffectIcon(context.matrixStack, this.font, new EffectInstance(effect), SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), (int) (context.button.getX() + this.margin), (int) context.button.getY(), AbstractGuiUtils.ITEM_ICON_SIZE, AbstractGuiUtils.ITEM_ICON_SIZE, false);
                } else {
                    context.button.setX(0).setY(0).setWidth(0).setHeight(0).setId("");
                }
            }));
        }
    }

    /**
     * 更新搜索结果
     */
    private void updateSearchResults() {
        String s = this.inputField == null ? null : this.inputField.getValue();
        this.effectList.clear();
        if (StringUtils.isNotNullOrEmpty(s)) {
            this.effectList.addAll(this.allEffectList.stream().filter(effect -> EffectRewardParser.getDisplayName(effect).contains(s)).collect(Collectors.toList()));
        } else {
            this.effectList.addAll(new ArrayList<>(this.playerMode ? this.playerEffectList : this.allEffectList));
        }
        this.setScrollOffset(0);
    }

    private void setScrollOffset(double offset) {
        this.scrollOffset = (int) Math.max(Math.min(offset, effectList.size() - maxLine), 0);
    }

    /**
     * 绘制按钮
     */
    private void renderButton(MatrixStack matrixStack) {
        for (OperationButton button : OP_BUTTONS.values()) button.render(matrixStack, keyManager);
        for (OperationButton button : EFFECT_BUTTONS) button.render(matrixStack, keyManager);
        for (OperationButton button : OP_BUTTONS.values())
            button.renderPopup(matrixStack, this.font, keyManager);
        for (OperationButton button : EFFECT_BUTTONS)
            button.renderPopup(matrixStack, this.font, keyManager);
    }

    private void handleEffect(OperationButton bt, int button, AtomicBoolean flag) {
        if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            if (StringUtils.isNotNullOrEmpty(bt.getId())) {
                EffectInstance effectInstance = RewardManager.deserializeReward(this.currentEffect);
                this.currentEffect = new Reward(EffectRewardParser.getEffectInstance(bt.getId(), effectInstance.getDuration(), effectInstance.getAmplifier()), ERewardType.EFFECT, this.probability);
                LOGGER.debug("Select effect: {}", EffectRewardParser.getDisplayName(effectInstance));
                flag.set(true);
            }
        }
    }

    private void handleOperation(OperationButton bt, int button, AtomicBoolean flag, AtomicBoolean updateSearchResults) {
        if (bt.getOperation() == OperationButtonType.TYPE.getCode()) {
            this.playerMode = !this.playerMode;
            updateSearchResults.set(true);
            flag.set(true);
        }
        // 编辑效果Json
        else if (bt.getOperation() == OperationButtonType.EFFECT.getCode()) {
            String effectRewardJsonString = GSON.toJson(this.currentEffect.getContent());
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.translatable(EI18nType.TIPS, "enter_effect_json").setShadow(true)
                    , Text.translatable(EI18nType.TIPS, "enter_something")
                    , ""
                    , effectRewardJsonString
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    EffectInstance instance;
                    String json = input.get(0);
                    try {
                        JsonObject jsonObject = GSON.fromJson(json, JsonObject.class);
                        instance = RewardManager.deserializeReward(new Reward(jsonObject, ERewardType.EFFECT, this.probability));
                    } catch (Exception e) {
                        LOGGER.error("Invalid Json: {}", json);
                        instance = null;
                    }
                    if (instance != null) {
                        this.currentEffect = new Reward(instance, ERewardType.EFFECT, this.probability);
                    } else {
                        result.add(Component.translatableClient(EI18nType.TIPS, "effect_json_s_error", json).toString());
                    }
                }
                return result;
            }));
        }
        // 编辑持续时间
        else if (bt.getOperation() == OperationButtonType.DURATION.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.translatable(EI18nType.TIPS, "enter_effect_duration").setShadow(true)
                    , Text.translatable(EI18nType.TIPS, "enter_something")
                    , "\\d{0,4}"
                    , String.valueOf(((EffectInstance) RewardManager.deserializeReward(this.currentEffect)).getDuration())
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    int duration = StringUtils.toInt(input.get(0));
                    if (duration > 0 && duration <= 60 * 60 * 24 * 30) {
                        EffectInstance effectInstance = RewardManager.deserializeReward(this.currentEffect);
                        this.currentEffect = new Reward(new EffectInstance(effectInstance.getEffect(), duration, effectInstance.getAmplifier()), ERewardType.EFFECT, this.probability);
                    } else {
                        result.add(Component.translatableClient(EI18nType.TIPS, "effect_duration_s_error", input.get(0)).toString());
                    }
                }
                return result;
            }));
        }
        // 编辑效果等级
        else if (bt.getOperation() == OperationButtonType.AMPLIFIER.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.translatable(EI18nType.TIPS, "enter_effect_amplifier").setShadow(true)
                    , Text.translatable(EI18nType.TIPS, "enter_something")
                    , ""
                    , String.valueOf(((EffectInstance) RewardManager.deserializeReward(this.currentEffect)).getAmplifier() + 1)
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    int amplifier = StringUtils.toInt(input.get(0));
                    if (amplifier > 0 && amplifier <= 100) {
                        EffectInstance effectInstance = RewardManager.deserializeReward(this.currentEffect);
                        this.currentEffect = new Reward(new EffectInstance(effectInstance.getEffect(), effectInstance.getDuration(), amplifier - 1), ERewardType.EFFECT, this.probability);
                    } else {
                        result.add(Component.translatableClient(EI18nType.TIPS, "effect_amplifier_s_error", input.get(0)).toString());
                    }
                }
                return result;
            }));
        }
        // 编辑概率
        else if (bt.getOperation() == OperationButtonType.PROBABILITY.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.translatable(EI18nType.TIPS, "enter_reward_probability").setShadow(true)
                    , Text.translatable(EI18nType.TIPS, "enter_something")
                    , "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?"
                    , StringUtils.toFixedEx(this.probability, 5)
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    BigDecimal p = StringUtils.toBigDecimal(input.get(0));
                    if (p.compareTo(BigDecimal.ZERO) > 0 && p.compareTo(BigDecimal.ONE) <= 0) {
                        this.probability = p;
                    } else {
                        result.add(Component.translatableClient(EI18nType.TIPS, "reward_probability_s_error", input.get(0)).toString());
                    }
                }
                return result;
            }));
        }
    }
}
