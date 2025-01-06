package xin.vanilla.mc.screen;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.searchtree.SearchRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.mc.config.ArraySet;
import xin.vanilla.mc.config.StringList;
import xin.vanilla.mc.enums.ERewardType;
import xin.vanilla.mc.rewards.Reward;
import xin.vanilla.mc.rewards.RewardManager;
import xin.vanilla.mc.rewards.impl.ItemRewardParser;
import xin.vanilla.mc.screen.component.OperationButton;
import xin.vanilla.mc.screen.component.Text;
import xin.vanilla.mc.util.AbstractGuiUtils;
import xin.vanilla.mc.util.CollectionUtils;
import xin.vanilla.mc.util.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static xin.vanilla.mc.config.RewardOptionDataManager.GSON;
import static xin.vanilla.mc.util.I18nUtils.getByZh;

public class ItemSelectScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private final NonNullList<ItemStack> allItemList = this.getAllItemList();
    private final List<ItemStack> playerItemList = this.getPlayerItemList();
    // 每行显示数量
    private final int itemPerLine = 9;
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
    private final ArraySet<ItemStack> itemList = new ArraySet<>();
    /**
     * 操作按钮
     */
    private final Map<Integer, OperationButton> OP_BUTTONS = new HashMap<>();
    /**
     * 物品按钮
     */
    private final List<OperationButton> ITEM_BUTTONS = new ArrayList<>();
    /**
     * 显示的标签
     */
    private final Set<TagKey<Item>> visibleTags = new HashSet<>();
    /**
     * 当前选择的物品 ID
     */
    @Getter
    private String selectedItemId = "";
    /**
     * 当前选择的物品
     */
    private Reward currentItem = new Reward(new ItemStack(Items.AIR), ERewardType.ITEM);
    /**
     * 奖励概率
     */
    private BigDecimal probability = BigDecimal.ONE;
    /**
     * 背包模式
     */
    private boolean inventoryMode = false;

    private int bgX;
    private int bgY;
    private final double margin = 3;
    private double itemBgX = this.bgX + margin;
    private double itemBgY = this.bgY + 20;

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
        ITEM(2),
        COUNT(3),
        NBT(4),
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

    public ItemSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived, @NonNull Reward defaultItem, Supplier<Boolean> shouldClose) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentItem = defaultItem;
        this.probability = defaultItem.getProbability();
        this.selectedItemId = ItemRewardParser.getId((ItemStack) RewardManager.deserializeReward(defaultItem));
        this.shouldClose = shouldClose;
    }

    public ItemSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived, @NonNull Reward defaultItem, Supplier<Boolean> shouldClose) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentItem = defaultItem;
        this.probability = defaultItem.getProbability();
        this.selectedItemId = ItemRewardParser.getId((ItemStack) RewardManager.deserializeReward(defaultItem));
        this.shouldClose = shouldClose;
    }

    public ItemSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived, @NonNull Reward defaultItem) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.currentItem = defaultItem;
        this.probability = defaultItem.getProbability();
        this.selectedItemId = ItemRewardParser.getId((ItemStack) RewardManager.deserializeReward(defaultItem));
        this.shouldClose = null;
    }

    public ItemSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived, @NonNull Reward defaultItem) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.currentItem = defaultItem;
        this.probability = defaultItem.getProbability();
        this.selectedItemId = ItemRewardParser.getId((ItemStack) RewardManager.deserializeReward(defaultItem));
        this.shouldClose = null;
    }

    public ItemSelectScreen(@NonNull Screen callbackScreen, @NonNull Consumer<Reward> onDataReceived) {
        super(Component.literal("ItemSelectScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.shouldClose = null;
    }

    public ItemSelectScreen(@NonNull Screen callbackScreen, @NonNull Function<Reward, String> onDataReceived) {
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
        this.inputField = AbstractGuiUtils.newTextFieldWidget(this.font, bgX, bgY, 180, 15, Component.literal(""));
        this.inputField.setValue(this.inputFieldText);
        this.addRenderableWidget(this.inputField);
        // 创建提交按钮
        this.addRenderableWidget(AbstractGuiUtils.newButton((int) (this.bgX + 90 + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (90 - this.margin * 2), 20
                , AbstractGuiUtils.textToComponent(Text.i18n("提交")), button -> {
                    if (this.currentItem == null) {
                        // 关闭当前屏幕并返回到调用者的 Screen
                        Minecraft.getInstance().setScreen(previousScreen);
                    } else {
                        // 获取选择的数据，并执行回调
                        Reward reward = new Reward((ItemStack) RewardManager.deserializeReward(this.currentItem), ERewardType.ITEM, this.probability);
                        if (onDataReceived1 != null) {
                            onDataReceived1.accept(reward);
                            Minecraft.getInstance().setScreen(previousScreen);
                        } else if (onDataReceived2 != null) {
                            String result = onDataReceived2.apply(reward);
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
                , (int) (90 - this.margin * 2), 20
                , AbstractGuiUtils.textToComponent(Text.i18n("取消"))
                , button -> Minecraft.getInstance().setScreen(previousScreen)));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        // 绘制背景
        this.renderBackground(poseStack);
        AbstractGuiUtils.fill(poseStack, (int) (this.bgX - this.margin), (int) (this.bgY - this.margin), (int) (180 + this.margin * 2), (int) (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + 20 + margin * 2 + 5), 0xCCC6C6C6, 2);
        AbstractGuiUtils.fillOutLine(poseStack, (int) (this.itemBgX - this.margin), (int) (this.itemBgY - this.margin), (int) ((AbstractGuiUtils.ITEM_ICON_SIZE + this.margin) * this.itemPerLine + this.margin), (int) ((AbstractGuiUtils.ITEM_ICON_SIZE + this.margin) * this.maxLine + this.margin), 1, 0xFF000000, 1);
        super.render(poseStack, mouseX, mouseY, delta);
        // 保存输入框的文本, 防止窗口重绘时输入框内容丢失
        this.inputFieldText = this.inputField.getValue();

        this.renderButton(poseStack, mouseX, mouseY);
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
            // 物品按钮
            ITEM_BUTTONS.forEach(bt -> bt.setPressed(bt.isHovered()));
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
            // 物品按钮
            ITEM_BUTTONS.forEach(bt -> {
                if (bt.isHovered() && bt.isPressed()) {
                    this.handleItem(bt, button, flag);
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
                    double scale = Math.ceil((double) (itemList.size() - itemPerLine * maxLine) / itemPerLine) / (this.outScrollHeight - 2);
                    this.setScrollOffset(this.scrollOffsetOld + (mouseY - this.mouseDownY) * scale);
                }
            }
        });
        // 物品按钮
        ITEM_BUTTONS.forEach(bt -> bt.setHovered(bt.isMouseOverEx(mouseX, mouseY)));
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
            // this.updateLayout();
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


    private NonNullList<ItemStack> getAllItemList() {
        NonNullList<ItemStack> list = NonNullList.create();
        if (Minecraft.getInstance().player != null) {
            CreativeModeTabs.tryRebuildTabContents(Minecraft.getInstance().player.connection.enabledFeatures(), true);
        }
        list.addAll(CreativeModeTabs.SEARCH.getDisplayItems());
        return list;
    }

    private List<ItemStack> getPlayerItemList() {
        List<ItemStack> result = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            result.addAll(player.getInventory().items);
            result.addAll(player.getInventory().armor);
            result.addAll(player.getInventory().offhand);
            result = result.stream().filter(itemStack -> !itemStack.isEmpty() && itemStack.getItem() != Items.AIR).collect(Collectors.toList());
        }
        return result;
    }

    private void updateLayout() {
        this.bgX = this.width / 2 - 92;
        this.bgY = this.height / 2 - 65;
        this.itemBgX = this.bgX + margin;
        this.itemBgY = this.bgY + 20;

        // 初始化操作按钮
        this.OP_BUTTONS.put(OperationButtonType.TYPE.getCode(), new OperationButton(OperationButtonType.TYPE.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(this.inventoryMode ? Items.CHEST : Items.COMPASS);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button().getX() + 2, (int) context.button().getY() + 2);
            Text text = this.inventoryMode ? Text.i18n("列出模式\n物品栏 (%s)", playerItemList.size()) : Text.i18n("列出模式\n所有物品 (%s)", allItemList.size());
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.ITEM.getCode(), new OperationButton(OperationButtonType.ITEM.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            this.itemRenderer.renderGuiItem(RewardManager.deserializeReward(this.currentItem), (int) context.button().getX() + 2, (int) context.button().getY() + 2);
            context.button().setTooltip(AbstractGuiUtils.componentToText(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getHoverName().copy()));
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.COUNT.getCode(), new OperationButton(OperationButtonType.COUNT.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.WRITABLE_BOOK);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button().getX() + 2, (int) context.button().getY() + 2);
            Text text = Text.i18n("设置数量\n当前 %s", ((ItemStack) RewardManager.deserializeReward(this.currentItem)).getCount());
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 2).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.NBT.getCode(), new OperationButton(OperationButtonType.NBT.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.NAME_TAG);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button().getX() + 2, (int) context.button().getY() + 2);
            Text text = Text.i18n("编辑NBT");
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 3).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.PROBABILITY.getCode(), new OperationButton(OperationButtonType.PROBABILITY.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button().isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), 1, lineColor, 2);
            AbstractGuiUtils.drawEffectIcon(context.poseStack(), super.font, new MobEffectInstance(MobEffects.LUCK), (int) context.button().getX() + 2, (int) context.button().getY() + 2, false);
            Text text = Text.i18n("设置概率\n当前 %.3f%%", this.probability.multiply(new BigDecimal(100)).floatValue());
            context.button().setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 4).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));

        // 滚动条
        this.OP_BUTTONS.put(OperationButtonType.SLIDER.getCode(), new OperationButton(OperationButtonType.SLIDER.getCode(), context -> {
            // 背景宽高
            double bgWidth = (AbstractGuiUtils.ITEM_ICON_SIZE + margin) * itemPerLine;
            double bgHeight = (AbstractGuiUtils.ITEM_ICON_SIZE + margin) * maxLine - margin;
            // 绘制滚动条
            this.outScrollX = itemBgX + bgWidth + 2;
            this.outScrollY = itemBgY - this.margin + 1;
            this.outScrollWidth = 5;
            this.outScrollHeight = (int) (bgHeight + this.margin + 1);
            // 滚动条百分比
            double inScrollWidthScale = itemList.size() > itemPerLine * maxLine ? (double) itemPerLine * maxLine / itemList.size() : 1;
            // 多出来的行数
            double outLine = Math.max((int) Math.ceil((double) (itemList.size() - itemPerLine * maxLine) / itemPerLine), 0);
            // 多出来的每行所占的空余条长度
            double outCellHeight = outLine == 0 ? 0 : (1 - inScrollWidthScale) * (outScrollHeight - 2) / outLine;
            // 滚动条上边距长度
            double inScrollTopHeight = this.getScrollOffset() * outCellHeight;
            // 滚动条高度
            this.inScrollHeight = Math.max(2, (outScrollHeight - 2) * inScrollWidthScale);
            this.inScrollY = outScrollY + inScrollTopHeight + 1;
            // 绘制滚动条外层背景
            AbstractGuiUtils.fill(context.poseStack(), (int) this.outScrollX, (int) this.outScrollY, this.outScrollWidth, this.outScrollHeight, 0xCC232323);
            // 绘制滚动条滑块
            int color = context.button().isHovered() ? 0xCCFFFFFF : 0xCC8B8B8B;
            AbstractGuiUtils.fill(context.poseStack(), (int) this.outScrollX, (int) Math.ceil(this.inScrollY), this.outScrollWidth, (int) this.inScrollHeight, color);
            context.button().setX(this.outScrollX).setY(this.outScrollY).setWidth(this.outScrollWidth).setHeight(this.outScrollHeight);
        }));

        // 物品列表
        this.ITEM_BUTTONS.clear();
        for (int i = 0; i < maxLine; i++) {
            for (int j = 0; j < itemPerLine; j++) {
                ITEM_BUTTONS.add(new OperationButton(itemPerLine * i + j, context -> {
                    int i1 = context.button().getOperation() / itemPerLine;
                    int j1 = context.button().getOperation() % itemPerLine;
                    int index = ((itemList.size() > itemPerLine * maxLine ? this.getScrollOffset() : 0) + i1) * itemPerLine + j1;
                    if (index >= 0 && index < itemList.size()) {
                        ItemStack itemStack = itemList.get(index);
                        // 物品图标在弹出层中的 x 位置
                        double itemX = itemBgX + j1 * (AbstractGuiUtils.ITEM_ICON_SIZE + margin);
                        // 物品图标在弹出层中的 y 位置
                        double itemY = itemBgY + i1 * (AbstractGuiUtils.ITEM_ICON_SIZE + margin);
                        // 绘制背景
                        int bgColor;
                        if (context.button().isHovered() || ItemRewardParser.getId(itemStack).equalsIgnoreCase(this.getSelectedItemId())) {
                            bgColor = 0xEE7CAB7C;
                        } else {
                            bgColor = 0xEE707070;
                        }
                        context.button().setX(itemX - 1).setY(itemY - 1).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 2).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 2)
                                .setId(ItemRewardParser.getId(itemStack));

                        AbstractGuiUtils.fill(context.poseStack(), (int) context.button().getX(), (int) context.button().getY(), (int) context.button().getWidth(), (int) context.button().getHeight(), bgColor);
                        this.itemRenderer.renderGuiItem(itemStack, (int) context.button().getX() + 1, (int) context.button().getY() + 1);
                        // 绘制物品详情悬浮窗
                        context.button().setCustomPopupFunction(() -> {
                            if (context.button().isHovered()) {
                                List<Component> list = itemStack.getTooltipLines(Minecraft.getInstance().player, Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
                                List<Component> list1 = Lists.newArrayList(list);
                                Item item = itemStack.getItem();
                                this.visibleTags.forEach((itemITag) -> {
                                    if (itemStack.is(itemITag)) {
                                        list1.add(1, (Component.literal("#" + itemITag.location())).withStyle(ChatFormatting.DARK_PURPLE));
                                    }
                                });
                                for (CreativeModeTab modeTab : CreativeModeTabs.allTabs()) {
                                    if (modeTab.contains(itemStack)) {
                                        list1.add(1, modeTab.getDisplayName().copy().withStyle(ChatFormatting.BLUE));
                                    }
                                }
                                this.renderTooltip(context.poseStack(), list1, itemStack.getTooltipImage(), (int) context.mouseX(), (int) context.mouseY(), itemStack);
                            }
                        });
                    } else {
                        context.button().setX(0).setY(0).setWidth(0).setHeight(0).setId("");
                    }
                }));
            }
        }
    }

    private List<ItemStack> getItemList() {
        return this.inventoryMode ? this.playerItemList : this.allItemList;
    }

    /**
     * 更新搜索结果
     */
    private void updateSearchResults() {
        String s = this.inputField == null ? null : this.inputField.getValue();
        this.itemList.clear();
        this.visibleTags.clear();
        if (StringUtils.isNotNullOrEmpty(s)) {
            // # 物品标签
            if (s.startsWith("#")) {
                s = s.substring(1);
                this.updateVisibleTags(s);
                this.itemList.addAll(Minecraft.getInstance().getSearchTree(SearchRegistry.CREATIVE_TAGS).search(s.toLowerCase(Locale.ROOT)));
            }
            // $ 描述
            else if (s.startsWith("$")) {
                s = s.substring(1);
                this.itemList.addAll(this.searchByDescription(s));
            } else {
                // @ modId
                if (s.startsWith("@")) s = s.replaceAll("^@(\\S+)", "$1:");
                this.itemList.addAll(Minecraft.getInstance().getSearchTree(SearchRegistry.CREATIVE_NAMES).search(s.toLowerCase(Locale.ROOT)));
            }
        } else {
            this.itemList.addAll(new ArrayList<>(this.getItemList()));
        }
        this.setScrollOffset(0);
    }

    private void updateVisibleTags(String string) {
        int i = string.indexOf(':');
        Predicate<ResourceLocation> predicate;
        if (i == -1) {
            predicate = (resourceLocation) -> resourceLocation.getPath().contains(string);
        } else {
            String modId = string.substring(0, i).trim();
            String itemId = string.substring(i + 1).trim();
            predicate = (resourceLocation) -> resourceLocation.getNamespace().contains(modId) && (StringUtils.isNullOrEmpty(itemId) || resourceLocation.getPath().contains(itemId));
        }
        BuiltInRegistries.ITEM.getTagNames().filter((tagKey) -> {
            return predicate.test(tagKey.location());
        }).forEach(this.visibleTags::add);
    }

    private List<ItemStack> searchByDescription(String keyword) {
        return this.getItemList().stream()
                .filter(item -> item.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.ADVANCED)
                        .stream().anyMatch(component -> component.getString().contains(keyword))
                ).collect(Collectors.toList());
    }

    private void setScrollOffset(double offset) {
        this.scrollOffset = (int) Math.max(Math.min(offset, (int) Math.ceil((double) (itemList.size() - itemPerLine * maxLine) / itemPerLine)), 0);
    }

    /**
     * 绘制按钮
     */
    private void renderButton(PoseStack poseStack, int mouseX, int mouseY) {
        for (OperationButton button : OP_BUTTONS.values()) button.render(poseStack, mouseX, mouseY);
        for (OperationButton button : ITEM_BUTTONS) button.render(poseStack, mouseX, mouseY);
        for (OperationButton button : OP_BUTTONS.values())
            button.renderPopup(poseStack, this.font, mouseX, mouseY);
        for (OperationButton button : ITEM_BUTTONS)
            button.renderPopup(poseStack, this.font, mouseX, mouseY);
    }

    private void handleItem(OperationButton bt, int button, AtomicBoolean flag) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.selectedItemId = bt.getId();
            if (StringUtils.isNotNullOrEmpty(this.selectedItemId)) {
                ItemStack itemStack = ItemRewardParser.getItemStack(selectedItemId);
                itemStack.setCount(1);
                this.currentItem = new Reward(itemStack, ERewardType.ITEM, this.probability);
                LOGGER.debug("Select item: {}", ItemRewardParser.getDisplayName(itemStack));
                flag.set(true);

            }
        }
    }

    private void handleOperation(OperationButton bt, int button, AtomicBoolean flag, AtomicBoolean updateSearchResults) {
        if (bt.getOperation() == OperationButtonType.TYPE.getCode()) {
            this.inventoryMode = !this.inventoryMode;
            updateSearchResults.set(true);
            flag.set(true);
        }
        // 编辑奖励Json
        else if (bt.getOperation() == OperationButtonType.ITEM.getCode()) {
            String itemRewardJsonString = GSON.toJson(this.currentItem.getContent());
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.i18n("请输入物品Json").setShadow(true)
                    , Text.i18n("请输入")
                    , ""
                    , itemRewardJsonString
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    ItemStack itemStack;
                    String json = input.get(0);
                    try {
                        JsonObject jsonObject = GSON.fromJson(json, JsonObject.class);
                        itemStack = RewardManager.deserializeReward(new Reward(jsonObject, ERewardType.ITEM, this.probability));
                    } catch (Exception e) {
                        LOGGER.error("Invalid Json: {}", json);
                        itemStack = null;
                    }
                    if (itemStack != null && itemStack.getItem() != Items.AIR) {
                        this.currentItem = new Reward(itemStack, ERewardType.ITEM, this.probability);
                        this.selectedItemId = ItemRewardParser.getId(itemStack);
                    } else {
                        result.add(getByZh("物品Json[%s]输入有误", json));
                    }
                }
                return result;
            }));
        }
        // 编辑数量
        else if (bt.getOperation() == OperationButtonType.COUNT.getCode()) {
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.i18n("请输入物品数量").setShadow(true)
                    , Text.i18n("请输入")
                    , "\\d{0,4}"
                    , String.valueOf(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getCount())
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    String num = input.get(0);
                    int count = StringUtils.toInt(num);
                    if (count > 0 && count <= 64 * 9 * 5) {
                        ItemStack itemStack = RewardManager.deserializeReward(this.currentItem);
                        itemStack.setCount(count);
                        this.currentItem = new Reward(itemStack, ERewardType.ITEM, this.probability);
                    } else {
                        result.add(getByZh("物品数量[%s]输入有误", num));
                    }
                }
                return result;
            }));
        }
        // 编辑NBT
        else if (bt.getOperation() == OperationButtonType.NBT.getCode()) {
            String itemNbtJsonString = ItemRewardParser.getNbtString(RewardManager.deserializeReward(this.currentItem));
            Minecraft.getInstance().setScreen(new StringInputScreen(this
                    , Text.i18n("请输入物品NBT").setShadow(true)
                    , Text.i18n("请输入")
                    , ""
                    , itemNbtJsonString
                    , input -> {
                StringList result = new StringList();
                if (CollectionUtils.isNotNullOrEmpty(input)) {
                    ItemStack itemStack;
                    String nbt = input.get(0);
                    try {
                        itemStack = ItemRewardParser.getItemStack(ItemRewardParser.getId(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getItem()) + nbt, true);
                        itemStack.setCount(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getCount());
                    } catch (Exception e) {
                        LOGGER.error("Invalid NBT: {}", nbt);
                        itemStack = null;
                    }
                    if (itemStack != null && itemStack.hasTag()) {
                        this.currentItem = new Reward(itemStack, ERewardType.ITEM, this.probability);
                        this.selectedItemId = ItemRewardParser.getId(itemStack);
                    } else {
                        result.add(getByZh("物品NBT[%s]输入有误", nbt));
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
