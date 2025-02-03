package xin.vanilla.sakura.screen;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.config.StringList;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.rewards.impl.EffectRewardParser;
import xin.vanilla.sakura.screen.component.OperationButton;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.CollectionUtils;
import xin.vanilla.sakura.util.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static xin.vanilla.sakura.config.RewardOptionDataManager.GSON;
import static xin.vanilla.sakura.util.I18nUtils.getByZh;

public class EffecrSelectScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private final List<MobEffect> allMobEffectList = ForgeRegistries.MOB_EFFECTS.getValues().stream().toList();
    private final List<MobEffect> playerMobEffectList = this.getPlayerMobEffectList();
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
    private EditBox inputField;
    /**
     * 输入框文本
     */
    private String inputFieldText = "";
    /**
     * 搜索结果
     */
    private final List<MobEffect> mobEffectList = new ArrayList<>();
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
    private Reward currentMobEffect = new Reward(new MobEffectInstance(MobEffects.LUCK), ERewardType.EFFECT);
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
    private double mobEffectBgX = this.bgX + margin;
    private double mobEffectBgY = this.bgY + 20;

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
        MOBEFFECT(2),
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

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived, @NonNull Reward defaultMobEffect, Supplier<Boolean> shouldClose) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentMobEffect = defaultMobEffect;
        this.probability = defaultMobEffect.getProbability();
        this.shouldClose = shouldClose;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived, @NonNull Reward defaultMobEffect, Supplier<Boolean> shouldClose) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentMobEffect = defaultMobEffect;
        this.probability = defaultMobEffect.getProbability();
        this.shouldClose = shouldClose;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived, @NonNull Reward defaultMobEffect) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentMobEffect = defaultMobEffect;
        this.probability = defaultMobEffect.getProbability();
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived, @NonNull Reward defaultMobEffect) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentMobEffect = defaultMobEffect;
        this.probability = defaultMobEffect.getProbability();
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.shouldClose = null;
    }

    public EffecrSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived) {
        super(Component.literal("ItemSelectScreen"));
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
        this.inputField = AbstractGuiUtils.newTextFieldWidget(this.font, bgX, bgY, 112, 15, Component.literal(""));
        this.inputField.setValue(this.inputFieldText);
        this.addRenderableWidget(this.inputField);
        // 创建提交按钮
        this.addRenderableWidget(AbstractGuiUtils.newButton((int) (this.bgX + 56 + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (56 - this.margin * 2), 20
                , AbstractGuiUtils.textToComponent(Text.i18n("提交")), button -> {
                    if (this.currentMobEffect == null) {
                        // 关闭当前屏幕并返回到调用者的 Screen
                        Minecraft.getInstance().setScreen(previousScreen);
                    } else {
                        // 获取选择的数据，并执行回调
                        MobEffectInstance effectInstance = RewardManager.deserializeReward(this.currentMobEffect);
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
        this.addRenderableWidget(AbstractGuiUtils.newButton((int) (this.bgX + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (56 - this.margin * 2), 20
                , AbstractGuiUtils.textToComponent(Text.i18n("取消"))
                , button -> Minecraft.getInstance().setScreen(previousScreen)));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 绘制背景
        this.renderBackground(graphics);
        AbstractGuiUtils.fill(graphics, (int) (this.bgX - this.margin), (int) (this.bgY - this.margin), (int) (112 + this.margin * 2), (int) (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + 20 + margin * 2 + 5), 0xCCC6C6C6, 2);
        AbstractGuiUtils.fillOutLine(graphics, (int) (this.mobEffectBgX - this.margin), (int) (this.mobEffectBgY - this.margin), 104, (int) ((AbstractGuiUtils.ITEM_ICON_SIZE + this.margin) * this.maxLine + this.margin), 1, 0xFF000000, 1);
        super.render(graphics, mouseX, mouseY, delta);
        // 保存输入框的文本, 防止窗口重绘时输入框内容丢失
        this.inputFieldText = this.inputField.getValue();

        this.renderButton(graphics, mouseX, mouseY);
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
                    this.handleMobEffect(bt, button, flag);
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
                    double scale = Math.ceil((double) mobEffectList.size() - maxLine) / (this.outScrollHeight - 2);
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

    private List<MobEffect> getPlayerMobEffectList() {
        List<MobEffect> result = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            result.addAll(player.getActiveEffectsMap().keySet());
        }
        return result;
    }

    private void updateLayout() {
        this.bgX = this.width / 2 - 56;
        this.bgY = this.height / 2 - 63;
        this.mobEffectBgX = this.bgX + margin;
        this.mobEffectBgY = this.bgY + 20;

        // 初始化操作按钮
        this.OP_BUTTONS.put(OperationButtonType.TYPE.getCode(), new OperationButton(OperationButtonType.TYPE.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(this.playerMode ? Items.CHEST : Items.COMPASS);
            context.graphics().renderItem(itemStack, (int) context.button().getX() + 2, (int) context.button().getY() + 2);
            Text text = this.playerMode ? Text.i18n("列出模式\n玩家拥有 (%s)", playerMobEffectList.size()) : Text.i18n("列出模式\n所有效果 (%s)", allMobEffectList.size());
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.MOBEFFECT.getCode(), new OperationButton(OperationButtonType.MOBEFFECT.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            AbstractGuiUtils.drawEffectIcon(context.graphics(), this.font, RewardManager.deserializeReward(this.currentMobEffect), SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), (int) context.button().getX() + 2, (int) context.button().getY() + 2, AbstractGuiUtils.ITEM_ICON_SIZE, AbstractGuiUtils.ITEM_ICON_SIZE, false);
            context.button().setTooltip(AbstractGuiUtils.componentToText(((MobEffectInstance) RewardManager.deserializeReward(this.currentMobEffect)).getEffect().getDisplayName().copy()));
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.DURATION.getCode(), new OperationButton(OperationButtonType.DURATION.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.CLOCK);
            context.graphics().renderItem(itemStack, (int) context.button().getX() + 2, (int) context.button().getY() + 2);
            Text text = Text.i18n("设置持续时间\n当前 %s", ((MobEffectInstance) RewardManager.deserializeReward(this.currentMobEffect)).getDuration());
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 2).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.AMPLIFIER.getCode(), new OperationButton(OperationButtonType.AMPLIFIER.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.ANVIL);
            context.graphics().renderItem(itemStack, (int) context.button().getX() + 2, (int) context.button().getY() + 2);
            Text text = Text.i18n("设置效果等级\n当前 %s", StringUtils.intToRoman(((MobEffectInstance) RewardManager.deserializeReward(this.currentMobEffect)).getAmplifier() + 1));
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 3).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.PROBABILITY.getCode(), new OperationButton(OperationButtonType.PROBABILITY.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            AbstractGuiUtils.drawEffectIcon(context.graphics(), super.font, new MobEffectInstance(MobEffects.LUCK), (int) context.button().getX() + 2, (int) context.button().getY() + 2, false);
            Text text = Text.i18n("设置概率\n当前 %.3f%%", this.probability.multiply(new BigDecimal(100)).floatValue());
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 4).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));

        // 滚动条
        this.OP_BUTTONS.put(OperationButtonType.SLIDER.getCode(), new OperationButton(OperationButtonType.SLIDER.getCode(), context -> {
            // 背景宽高
            double bgWidth = 104;
            double bgHeight = (AbstractGuiUtils.ITEM_ICON_SIZE + margin) * maxLine - margin;
            // 绘制滚动条
            this.outScrollX = mobEffectBgX + bgWidth;
            this.outScrollY = mobEffectBgY - this.margin + 1;
            this.outScrollWidth = 5;
            this.outScrollHeight = (int) (bgHeight + this.margin + 1);
            // 滚动条百分比
            double inScrollWidthScale = mobEffectList.size() > maxLine ? (double) maxLine / mobEffectList.size() : 1;
            // 多出来的行数
            double outLine = Math.max(mobEffectList.size() - maxLine, 0);
            // 多出来的每行所占的空余条长度
            double outCellHeight = outLine == 0 ? 0 : (1 - inScrollWidthScale) * (outScrollHeight - 2) / outLine;
            // 滚动条上边距长度
            double inScrollTopHeight = this.getScrollOffset() * outCellHeight;
            // 滚动条高度
            this.inScrollHeight = Math.max(2, (outScrollHeight - 2) * inScrollWidthScale);
            this.inScrollY = outScrollY + inScrollTopHeight + 1;
            // 绘制滚动条外层背景
            AbstractGuiUtils.fill(context.graphics(), (int) this.outScrollX, (int) this.outScrollY, this.outScrollWidth, this.outScrollHeight, 0xCC232323);
            // 绘制滚动条滑块
            int color = context.button().isHovered() ? 0xCCFFFFFF : 0xCC8B8B8B;
            AbstractGuiUtils.fill(context.graphics(), (int) this.outScrollX, (int) Math.ceil(this.inScrollY), this.outScrollWidth, (int) this.inScrollHeight, color);
            context.button().setX(this.outScrollX).setY(this.outScrollY).setWidth(this.outScrollWidth).setHeight(this.outScrollHeight);
        }));

        // 效果列表
        this.EFFECT_BUTTONS.clear();
        for (int i = 0; i < maxLine; i++) {
            EFFECT_BUTTONS.add(new OperationButton(i, context -> {
                int i1 = context.button().getOperation();
                int index = (mobEffectList.size() > maxLine ? this.getScrollOffset() : 0) + i1;
                if (index >= 0 && index < mobEffectList.size()) {
                    MobEffect mobEffect = mobEffectList.get(index);
                    // 效果图标在弹出层中的 x 位置
                    double mobEffectX = mobEffectBgX;
                    // 效果图标在弹出层中的 y 位置
                    double mobEffectY = mobEffectBgY + i1 * (AbstractGuiUtils.ITEM_ICON_SIZE + margin);
                    // 绘制背景
                    int bgColor;
                    if (context.button().isHovered() || mobEffect == ((MobEffectInstance) RewardManager.deserializeReward(this.currentMobEffect)).getEffect()) {
                        bgColor = 0xEE7CAB7C;
                    } else {
                        bgColor = 0xEE707070;
                    }
                    context.button().setX(mobEffectX - 1).setY(mobEffectY - 1).setWidth(100).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 2)
                            .setId(EffectRewardParser.getId(mobEffect));

                    AbstractGuiUtils.fill(context.graphics(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), bgColor);
                    AbstractGuiUtils.drawLimitedText(Text.literal(EffectRewardParser.getDisplayName(mobEffect)).setGraphics(context.graphics()).setFont(this.font), context.button().getX() + AbstractGuiUtils.ITEM_ICON_SIZE + this.margin * 2, context.button().getY() + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 - this.font.lineHeight) / 2.0, (int) context.button().getWidth() - AbstractGuiUtils.ITEM_ICON_SIZE - 4);
                    AbstractGuiUtils.drawEffectIcon(context.graphics(), this.font, new MobEffectInstance(mobEffect), SakuraSignIn.getThemeTexture(), SakuraSignIn.getThemeTextureCoordinate(), (int) (context.button().getX() + this.margin), (int) context.button().getY(), AbstractGuiUtils.ITEM_ICON_SIZE, AbstractGuiUtils.ITEM_ICON_SIZE, false);
                } else {
                    context.button().setX(0).setY(0).setWidth(0).setHeight(0).setId("");
                }
            }));
        }
    }

    /**
     * 更新搜索结果
     */
    private void updateSearchResults() {
        String s = this.inputField == null ? null : this.inputField.getValue();
        this.mobEffectList.clear();
        if (StringUtils.isNotNullOrEmpty(s)) {
            this.mobEffectList.addAll(this.allMobEffectList.stream().filter(mobEffect -> EffectRewardParser.getDisplayName(mobEffect).contains(s)).toList());
        } else {
            this.mobEffectList.addAll(new ArrayList<>(this.playerMode ? this.playerMobEffectList : this.allMobEffectList));
        }
        this.setScrollOffset(0);
    }

    private void setScrollOffset(double offset) {
        this.scrollOffset = (int) Math.max(Math.min(offset, mobEffectList.size() - maxLine), 0);
    }

    /**
     * 绘制按钮
     */
    private void renderButton(GuiGraphics graphics, int mouseX, int mouseY) {
        for (OperationButton button : OP_BUTTONS.values()) button.render(graphics, mouseX, mouseY);
        for (OperationButton button : EFFECT_BUTTONS) button.render(graphics, mouseX, mouseY);
        for (OperationButton button : OP_BUTTONS.values())
            button.renderPopup(graphics, this.font, mouseX, mouseY);
        for (OperationButton button : EFFECT_BUTTONS)
            button.renderPopup(graphics, this.font, mouseX, mouseY);
    }

    private void handleMobEffect(OperationButton bt, int button, AtomicBoolean flag) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (StringUtils.isNotNullOrEmpty(bt.getId())) {
                MobEffectInstance effectInstance = RewardManager.deserializeReward(this.currentMobEffect);
                this.currentMobEffect = new Reward(EffectRewardParser.getMobEffectInstance(bt.getId(), effectInstance.getDuration(), effectInstance.getAmplifier()), ERewardType.EFFECT, this.probability);
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
        else if (bt.getOperation() == OperationButtonType.MOBEFFECT.getCode()) {
            String effectRewardJsonString = GSON.toJson(this.currentMobEffect.getContent());
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.i18n("请输入效果Json").setShadow(true)
                    , Text.i18n("请输入")
                    , ""
                    , effectRewardJsonString
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    MobEffectInstance instance;
                    String json = input.get(0);
                    try {
                        JsonObject jsonObject = GSON.fromJson(json, JsonObject.class);
                        instance = RewardManager.deserializeReward(new Reward(jsonObject, ERewardType.EFFECT, this.probability));
                    } catch (Exception e) {
                        LOGGER.error("Invalid Json: {}", json);
                        instance = null;
                    }
                    if (instance != null) {
                        this.currentMobEffect = new Reward(instance, ERewardType.EFFECT, this.probability);
                    } else {
                        result.add(getByZh("效果Json[%s]输入有误", json));
                    }
                }
                return result;
            }));
        }
        // 编辑持续时间
        else if (bt.getOperation() == OperationButtonType.DURATION.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.i18n("请输入持续时间").setShadow(true)
                    , Text.i18n("请输入")
                    , "\\d{0,4}"
                    , String.valueOf(((MobEffectInstance) RewardManager.deserializeReward(this.currentMobEffect)).getDuration())
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    int duration = StringUtils.toInt(input.get(0));
                    if (duration > 0 && duration <= 60 * 60 * 24 * 30) {
                        MobEffectInstance effectInstance = RewardManager.deserializeReward(this.currentMobEffect);
                        this.currentMobEffect = new Reward(new MobEffectInstance(effectInstance.getEffect(), duration, effectInstance.getAmplifier()), ERewardType.EFFECT, this.probability);
                    } else {
                        result.add(getByZh("持续时间[%s]输入有误", input.get(0)));
                    }
                }
                return result;
            }));
        }
        // 编辑效果等级
        else if (bt.getOperation() == OperationButtonType.AMPLIFIER.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.i18n("请输入效果等级").setShadow(true)
                    , Text.i18n("请输入")
                    , ""
                    , String.valueOf(((MobEffectInstance) RewardManager.deserializeReward(this.currentMobEffect)).getAmplifier() + 1)
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    int amplifier = StringUtils.toInt(input.get(0));
                    if (amplifier > 0 && amplifier <= 100) {
                        MobEffectInstance effectInstance = RewardManager.deserializeReward(this.currentMobEffect);
                        this.currentMobEffect = new Reward(new MobEffectInstance(effectInstance.getEffect(), effectInstance.getDuration(), amplifier - 1), ERewardType.EFFECT, this.probability);
                    } else {
                        result.add(getByZh("效果等级[%s]输入有误", input.get(0)));
                    }
                }
                return result;
            }));
        }
        // 编辑概率
        else if (bt.getOperation() == OperationButtonType.PROBABILITY.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.i18n("请输入奖励概率").setShadow(true)
                    , Text.i18n("请输入")
                    , "(0?1(\\.0{0,5})?|0(\\.\\d{0,5})?)?"
                    , StringUtils.toFixedEx(this.probability, 5)
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    BigDecimal p = StringUtils.toBigDecimal(input.get(0));
                    if (p.compareTo(BigDecimal.ZERO) > 0 && p.compareTo(BigDecimal.ONE) <= 0) {
                        this.probability = p;
                    } else {
                        result.add(getByZh("奖励概率[%s]输入有误", input.get(0)));
                    }
                }
                return result;
            }));
        }
    }
}
