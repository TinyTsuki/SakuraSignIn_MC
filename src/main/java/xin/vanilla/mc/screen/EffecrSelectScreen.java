package xin.vanilla.mc.screen;

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
import net.minecraft.util.text.StringTextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.enums.ERewardType;
import xin.vanilla.mc.rewards.Reward;
import xin.vanilla.mc.rewards.RewardManager;
import xin.vanilla.mc.rewards.impl.EffectRewardParser;
import xin.vanilla.mc.screen.component.OperationButton;
import xin.vanilla.mc.screen.component.Text;
import xin.vanilla.mc.util.AbstractGuiUtils;
import xin.vanilla.mc.util.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static xin.vanilla.mc.config.RewardOptionDataManager.GSON;
import static xin.vanilla.mc.util.I18nUtils.getByZh;

public class EffecrSelectScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

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
    private final Consumer<EffectInstance> onDataReceived1;
    /**
     * 输入数据回调2
     */
    private final Function<EffectInstance, String> onDataReceived2;
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
    private EffectInstance currentEffect = new EffectInstance(Effects.LUCK);
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
        ;

        final int code;

        OperationButtonType(int code) {
            this.code = code;
        }

        static OperationButtonType valueOf(int code) {
            return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
        }
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<EffectInstance> onDataReceived, @NonNull EffectInstance defaultEffect, Supplier<Boolean> shouldClose) {
        super(new StringTextComponent("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentEffect = defaultEffect;
        this.shouldClose = shouldClose;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<EffectInstance, String> onDataReceived, @NonNull EffectInstance defaultEffect, Supplier<Boolean> shouldClose) {
        super(new StringTextComponent("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentEffect = defaultEffect;
        this.shouldClose = shouldClose;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<EffectInstance> onDataReceived, @NonNull EffectInstance defaultEffect) {
        super(new StringTextComponent("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentEffect = defaultEffect;
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<EffectInstance, String> onDataReceived, @NonNull EffectInstance defaultEffect) {
        super(new StringTextComponent("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentEffect = defaultEffect;
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<EffectInstance> onDataReceived) {
        super(new StringTextComponent("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<EffectInstance, String> onDataReceived) {
        super(new StringTextComponent("ItemSelectScreen"));
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
        this.inputField = AbstractGuiUtils.newTextFieldWidget(this.font, bgX, bgY, 112, 15, new StringTextComponent(""));
        this.inputField.setValue(this.inputFieldText);
        this.addButton(this.inputField);
        // 创建提交按钮
        this.addButton(AbstractGuiUtils.newButton((int) (this.bgX + 56 + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (56 - this.margin * 2), 20
                , AbstractGuiUtils.textToComponent(Text.i18n("提交")), button -> {
                    if (this.currentEffect == null) {
                        // 关闭当前屏幕并返回到调用者的 Screen
                        Minecraft.getInstance().setScreen(previousScreen);
                    } else {
                        // 获取选择的数据，并执行回调
                        if (onDataReceived1 != null) {
                            onDataReceived1.accept(this.currentEffect);
                            Minecraft.getInstance().setScreen(previousScreen);
                        } else if (onDataReceived2 != null) {
                            String result = onDataReceived2.apply(this.currentEffect);
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
                , AbstractGuiUtils.textToComponent(Text.i18n("取消"))
                , button -> Minecraft.getInstance().setScreen(previousScreen)));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
        // 绘制背景
        this.renderBackground(matrixStack);
        AbstractGuiUtils.fill(matrixStack, (int) (this.bgX - this.margin), (int) (this.bgY - this.margin), (int) (112 + this.margin * 2), (int) (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + 20 + margin * 2 + 5), 0xCCC6C6C6, 2);
        AbstractGuiUtils.fillOutLine(matrixStack, (int) (this.effectBgX - this.margin), (int) (this.effectBgY - this.margin), 104, (int) ((AbstractGuiUtils.ITEM_ICON_SIZE + this.margin) * this.maxLine + this.margin), 1, 0xFF000000, 1);
        super.render(matrixStack, mouseX, mouseY, delta);
        // 保存输入框的文本, 防止窗口重绘时输入框内容丢失
        this.inputFieldText = this.inputField.getValue();

        this.renderButton(matrixStack, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.setScrollOffset(this.getScrollOffset() - delta);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        AtomicBoolean flag = new AtomicBoolean(false);
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            Minecraft.getInstance().setScreen(previousScreen);
            flag.set(true);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
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
        AtomicBoolean flag = new AtomicBoolean(false);
        AtomicBoolean updateSearchResults = new AtomicBoolean(false);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
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
        return flag.get() ? flag.get() : super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.inputField.isFocused())) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        } else if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.inputField.isFocused()) {
            this.updateSearchResults();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
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
            Text text = this.playerMode ? Text.i18n("列出模式\n玩家拥有 (%s)", playerEffectList.size()) : Text.i18n("列出模式\n所有效果 (%s)", allEffectList.size());
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.EFFECT.getCode(), new OperationButton(OperationButtonType.EFFECT.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            AbstractGuiUtils.drawEffectIcon(context.matrixStack, this.font, this.currentEffect, SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), (int) context.button.getX() + 2, (int) context.button.getY() + 2, AbstractGuiUtils.ITEM_ICON_SIZE, AbstractGuiUtils.ITEM_ICON_SIZE, false);
            context.button.setTooltip(AbstractGuiUtils.componentToText(this.currentEffect.getEffect().getDisplayName().copy()));
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.DURATION.getCode(), new OperationButton(OperationButtonType.DURATION.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.CLOCK);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.i18n("设置持续时间\n当前 %s", this.currentEffect.getDuration());
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 2).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.AMPLIFIER.getCode(), new OperationButton(OperationButtonType.AMPLIFIER.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.ANVIL);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.i18n("设置效果等级\n当前 %s", StringUtils.intToRoman(this.currentEffect.getAmplifier() + 1));
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 3).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));

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
                    if (context.button.isHovered() || effect == this.currentEffect.getEffect()) {
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
    private void renderButton(MatrixStack matrixStack, int mouseX, int mouseY) {
        for (OperationButton button : OP_BUTTONS.values()) button.render(matrixStack, mouseX, mouseY);
        for (OperationButton button : EFFECT_BUTTONS) button.render(matrixStack, mouseX, mouseY);
        for (OperationButton button : OP_BUTTONS.values())
            button.renderPopup(matrixStack, this.font, mouseX, mouseY);
        for (OperationButton button : EFFECT_BUTTONS)
            button.renderPopup(matrixStack, this.font, mouseX, mouseY);
    }

    private void handleEffect(OperationButton bt, int button, AtomicBoolean flag) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (StringUtils.isNotNullOrEmpty(bt.getId())) {
                this.currentEffect = EffectRewardParser.getEffectInstance(bt.getId(), this.currentEffect.getDuration(), this.currentEffect.getAmplifier());
                LOGGER.debug("Select effect: {}", EffectRewardParser.getDisplayName(this.currentEffect));
                flag.set(true);
            }
        }
    }

    private void handleOperation(OperationButton bt, int button, AtomicBoolean flag, AtomicBoolean updateSearchResults) {
        if (bt.getOperation() == OperationButtonType.TYPE.getCode()) {
            this.playerMode = !this.playerMode;
            updateSearchResults.set(true);
            flag.set(true);
        } else if (bt.getOperation() == OperationButtonType.EFFECT.getCode()) {
            String effectRewardJsonString = RewardManager.serializeReward(this.currentEffect, ERewardType.EFFECT).toString();
            Minecraft.getInstance().setScreen(new StringInputScreen(this, Text.i18n("请输入效果Json").setShadow(true), Text.i18n("请输入"), "", effectRewardJsonString, input -> {
                String result = "";
                if (StringUtils.isNotNullOrEmpty(input)) {
                    EffectInstance instance;
                    try {
                        JsonObject jsonObject = GSON.fromJson(input, JsonObject.class);
                        instance = RewardManager.deserializeReward(new Reward(jsonObject, ERewardType.EFFECT));
                    } catch (Exception e) {
                        LOGGER.error("Invalid Json: {}", input);
                        instance = null;
                    }
                    if (instance != null) {
                        this.currentEffect = instance;
                    } else {
                        result = getByZh("效果Json[%s]输入有误", input);
                    }
                }
                return result;
            }));
        } else if (bt.getOperation() == OperationButtonType.DURATION.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this, Text.i18n("请输入持续时间").setShadow(true), Text.i18n("请输入"), "\\d{0,4}", String.valueOf(this.currentEffect.getDuration()), input -> {
                String result = "";
                if (StringUtils.isNotNullOrEmpty(input)) {
                    int duration = StringUtils.toInt(input);
                    if (duration > 0 && duration <= 60 * 60 * 24 * 30) {
                        this.currentEffect = new EffectInstance(this.currentEffect.getEffect(), duration, this.currentEffect.getAmplifier());
                    } else {
                        result = getByZh("持续时间[%s]输入有误", input);
                    }
                }
                return result;
            }));
        } else if (bt.getOperation() == OperationButtonType.AMPLIFIER.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this, Text.i18n("请输入效果等级").setShadow(true), Text.i18n("请输入"), "", String.valueOf(this.currentEffect.getAmplifier() + 1), input -> {
                String result = "";
                if (StringUtils.isNotNullOrEmpty(input)) {
                    int amplifier = StringUtils.toInt(input);
                    if (amplifier > 0 && amplifier <= 100) {
                        this.currentEffect = new EffectInstance(this.currentEffect.getEffect(), this.currentEffect.getDuration(), amplifier - 1);
                    } else {
                        result = getByZh("效果等级[%s]输入有误", input);
                    }
                }
                return result;
            }));
        }
    }
}
