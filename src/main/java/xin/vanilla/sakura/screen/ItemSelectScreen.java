package xin.vanilla.sakura.screen;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ITagCollection;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.gui.GuiUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.data.ArraySet;
import xin.vanilla.sakura.data.Reward;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.enums.EnumRegex;
import xin.vanilla.sakura.enums.EnumRewardType;
import xin.vanilla.sakura.rewards.RewardManager;
import xin.vanilla.sakura.rewards.impl.ItemRewardParser;
import xin.vanilla.sakura.screen.component.KeyEventManager;
import xin.vanilla.sakura.screen.component.OperationButton;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.GLFWKey;
import xin.vanilla.sakura.util.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static xin.vanilla.sakura.rewards.RewardConfigManager.GSON;

public class ItemSelectScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private final KeyEventManager keyManager = new KeyEventManager();

    private final Args args;

    private final static Component TITLE = Component.literal("ItemSelectScreen");

    private final NonNullList<ItemStack> allItemList = this.getAllItemList();
    private final List<ItemStack> playerItemList = this.getPlayerItemList();
    // 每行显示数量
    private final int itemPerLine = 9;
    // 每页显示行数
    private final int maxLine = 5;

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
    private final Map<ResourceLocation, ITag<Item>> visibleTags = Maps.newTreeMap();
    /**
     * 当前选择的物品 ID
     */
    @Getter
    private String selectedItemId = "";
    /**
     * 当前选择的物品
     */
    private Reward currentItem = new Reward(new ItemStack(Items.AIR), EnumRewardType.ITEM);
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

    public ItemSelectScreen(Args args) {
        super(TITLE.toTextComponent());
        Objects.requireNonNull(args);
        args.validate();
        this.args = args;
        this.currentItem = args.getDefaultItem();
        this.probability = args.getDefaultItem().getProbability();
        this.selectedItemId = ItemRewardParser.getId((ItemStack) RewardManager.deserializeReward(args.getDefaultItem()));
    }

    @Data
    @Accessors(chain = true)
    public static final class Args {
        /**
         * 父级 Screen
         */
        private Screen parentScreen;
        /**
         * 默认值
         */
        private Reward defaultItem = new Reward(new ItemStack(Items.AIR), EnumRewardType.ITEM);
        /**
         * 输入数据回调
         */
        private Consumer<Reward> onDataReceived1;
        /**
         * 输入数据回调
         */
        private Function<Reward, String> onDataReceived2;
        /**
         * 是否要显示该界面, 若为false则直接关闭当前界面并返回到调用者的 Screen
         */
        private Supplier<Boolean> shouldClose;

        public Args setOnDataReceived(Consumer<Reward> onDataReceived) {
            this.onDataReceived1 = onDataReceived;
            return this;
        }

        public Args setOnDataReceived(Function<Reward, String> onDataReceived) {
            this.onDataReceived2 = onDataReceived;
            return this;
        }

        public void validate() {
            Objects.requireNonNull(this.getParentScreen());
            if (this.getOnDataReceived1() == null)
                Objects.requireNonNull(this.getOnDataReceived2());
            if (this.getOnDataReceived2() == null)
                Objects.requireNonNull(this.getOnDataReceived1());
        }

    }

    @Override
    protected void init() {
        if (args.getShouldClose() != null && Boolean.TRUE.equals(args.getShouldClose().get()))
            Minecraft.getInstance().setScreen(args.getParentScreen());
        this.updateSearchResults();
        this.updateLayout();
        // 创建文本输入框
        this.inputField = AbstractGuiUtils.newTextFieldWidget(this.font, bgX, bgY, 180, 15, Component.empty());
        this.inputField.setValue(this.inputFieldText);
        this.addButton(this.inputField);
        // 创建提交按钮
        this.addButton(AbstractGuiUtils.newButton((int) (this.bgX + 90 + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (90 - this.margin * 2), 20
                , Component.translatableClient(EnumI18nType.OPTION, "submit"), button -> {
                    if (this.currentItem == null) {
                        Minecraft.getInstance().setScreen(args.getParentScreen());
                    } else {
                        // 获取选择的数据，并执行回调
                        Reward reward = new Reward((ItemStack) RewardManager.deserializeReward(this.currentItem), EnumRewardType.ITEM, this.probability);
                        if (args.getOnDataReceived1() != null) {
                            args.getOnDataReceived1().accept(reward);
                            Minecraft.getInstance().setScreen(args.getParentScreen());
                        } else if (args.getOnDataReceived2() != null) {
                            String result = args.getOnDataReceived2().apply(reward);
                            if (StringUtils.isNotNullOrEmpty(result)) {
                                // this.errorText = Text.literal(result).setColorArgb(0xFFFF0000);
                            } else {
                                Minecraft.getInstance().setScreen(args.getParentScreen());
                            }
                        }
                    }
                }));
        // 创建取消按钮
        this.addButton(AbstractGuiUtils.newButton((int) (this.bgX + this.margin), (int) (this.bgY + (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + margin))
                , (int) (90 - this.margin * 2), 20
                , Component.translatableClient(EnumI18nType.OPTION, "cancel")
                , button -> Minecraft.getInstance().setScreen(args.getParentScreen())));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
        keyManager.refresh(mouseX, mouseY);
        // 绘制背景
        this.renderBackground(matrixStack);
        AbstractGuiUtils.fill(matrixStack, (int) (this.bgX - this.margin), (int) (this.bgY - this.margin), (int) (180 + this.margin * 2), (int) (20 + (AbstractGuiUtils.ITEM_ICON_SIZE + 3) * 5 + 20 + margin * 2 + 5), 0xCCC6C6C6, 2);
        AbstractGuiUtils.fillOutLine(matrixStack, (int) (this.itemBgX - this.margin), (int) (this.itemBgY - this.margin), (int) ((AbstractGuiUtils.ITEM_ICON_SIZE + this.margin) * this.itemPerLine + this.margin), (int) ((AbstractGuiUtils.ITEM_ICON_SIZE + this.margin) * this.maxLine + this.margin), 1, 0xFF000000, 1);
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
            Minecraft.getInstance().setScreen(args.getParentScreen());
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
            // 物品按钮
            ITEM_BUTTONS.forEach(bt -> bt.setPressed(bt.isHovered()));
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
                    double scale = Math.ceil((double) (itemList.size() - itemPerLine * maxLine) / itemPerLine) / (this.outScrollHeight - 2);
                    this.setScrollOffset(this.scrollOffsetOld + (mouseY - this.mouseDownY) * scale);
                }
            }
        });
        // 物品按钮
        ITEM_BUTTONS.forEach(bt -> bt.setHovered(bt.isMouseOverEx(mouseX, mouseY)));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        keyManager.keyPressed(keyCode);
        if (keyCode == GLFWKey.GLFW_KEY_ESCAPE || (keyCode == GLFWKey.GLFW_KEY_BACKSPACE && !this.inputField.isFocused())) {
            Minecraft.getInstance().setScreen(args.getParentScreen());
            return true;
        } else if ((keyCode == GLFWKey.GLFW_KEY_ENTER || keyCode == GLFWKey.GLFW_KEY_KP_ENTER) && this.inputField.isFocused()) {
            this.updateSearchResults();
            // this.updateLayout();
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


    private NonNullList<ItemStack> getAllItemList() {
        NonNullList<ItemStack> list = NonNullList.create();
        for (Item item : Registry.ITEM) {
            item.fillItemCategory(ItemGroup.TAB_SEARCH, list);
        }
        return list;
    }

    private List<ItemStack> getPlayerItemList() {
        List<ItemStack> result = new ArrayList<>();
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            result.addAll(player.inventory.items);
            result.addAll(player.inventory.armor);
            result.addAll(player.inventory.offhand);
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
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(this.inventoryMode ? Items.CHEST : Items.COMPASS);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.translatable(EnumI18nType.TIPS, (this.inventoryMode ? "item_select_list_inventory_mode" : "item_select_list_all_mode"), (this.inventoryMode ? playerItemList.size() : allItemList.size()));
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.ITEM.getCode(), new OperationButton(OperationButtonType.ITEM.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            this.itemRenderer.renderGuiItem(RewardManager.deserializeReward(this.currentItem), (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            context.button.setTooltip(Text.fromTextComponent(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getHoverName().copy()));
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.COUNT.getCode(), new OperationButton(OperationButtonType.COUNT.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.WRITABLE_BOOK);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.translatable(EnumI18nType.TIPS, "set_count_s", ((ItemStack) RewardManager.deserializeReward(this.currentItem)).getCount());
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 2).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.NBT.getCode(), new OperationButton(OperationButtonType.NBT.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            ItemStack itemStack = new ItemStack(Items.NAME_TAG);
            this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 2, (int) context.button.getY() + 2);
            Text text = Text.translatable(EnumI18nType.TIPS, "edit_nbt");
            context.button.setTooltip(text);
        }).setX(this.bgX - AbstractGuiUtils.ITEM_ICON_SIZE - 2 - margin - 3).setY(this.bgY + margin + (AbstractGuiUtils.ITEM_ICON_SIZE + 4 + 1) * 3).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 4).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 4));
        this.OP_BUTTONS.put(OperationButtonType.PROBABILITY.getCode(), new OperationButton(OperationButtonType.PROBABILITY.getCode(), context -> {
            // 绘制背景
            int lineColor = context.button.isHovered() ? 0xEEFFFFFF : 0xEE000000;
            AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 0xEE707070, 2);
            AbstractGuiUtils.fillOutLine(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), 1, lineColor, 2);
            AbstractGuiUtils.drawEffectIcon(context.matrixStack, super.font, new EffectInstance(Effects.LUCK), (int) context.button.getX() + 2, (int) context.button.getY() + 2, false);
            Text text = Text.translatable(EnumI18nType.TIPS, "set_probability_f", this.probability.multiply(new BigDecimal(100)).floatValue());
            context.button.setTooltip(text);
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
            AbstractGuiUtils.fill(context.matrixStack, (int) this.outScrollX, (int) this.outScrollY, this.outScrollWidth, this.outScrollHeight, 0xCC232323);
            // 绘制滚动条滑块
            int color = context.button.isHovered() ? 0xCCFFFFFF : 0xCC8B8B8B;
            AbstractGuiUtils.fill(context.matrixStack, (int) this.outScrollX, (int) Math.ceil(this.inScrollY), this.outScrollWidth, (int) this.inScrollHeight, color);
            context.button.setX(this.outScrollX).setY(this.outScrollY).setWidth(this.outScrollWidth).setHeight(this.outScrollHeight);
        }));

        // 物品列表
        this.ITEM_BUTTONS.clear();
        for (int i = 0; i < maxLine; i++) {
            for (int j = 0; j < itemPerLine; j++) {
                ITEM_BUTTONS.add(new OperationButton(itemPerLine * i + j, context -> {
                    int i1 = context.button.getOperation() / itemPerLine;
                    int j1 = context.button.getOperation() % itemPerLine;
                    int index = ((itemList.size() > itemPerLine * maxLine ? this.getScrollOffset() : 0) + i1) * itemPerLine + j1;
                    if (index >= 0 && index < itemList.size()) {
                        ItemStack itemStack = itemList.get(index);
                        // 物品图标在弹出层中的 x 位置
                        double itemX = itemBgX + j1 * (AbstractGuiUtils.ITEM_ICON_SIZE + margin);
                        // 物品图标在弹出层中的 y 位置
                        double itemY = itemBgY + i1 * (AbstractGuiUtils.ITEM_ICON_SIZE + margin);
                        // 绘制背景
                        int bgColor;
                        if (context.button.isHovered() || ItemRewardParser.getId(itemStack).equalsIgnoreCase(this.getSelectedItemId())) {
                            bgColor = 0xEE7CAB7C;
                        } else {
                            bgColor = 0xEE707070;
                        }
                        context.button.setX(itemX - 1).setY(itemY - 1).setWidth(AbstractGuiUtils.ITEM_ICON_SIZE + 2).setHeight(AbstractGuiUtils.ITEM_ICON_SIZE + 2)
                                .setId(ItemRewardParser.getId(itemStack));

                        AbstractGuiUtils.fill(context.matrixStack, (int) context.button.getX(), (int) context.button.getY(), (int) context.button.getWidth(), (int) context.button.getHeight(), bgColor);
                        this.itemRenderer.renderGuiItem(itemStack, (int) context.button.getX() + 1, (int) context.button.getY() + 1);
                        // 绘制物品详情悬浮窗
                        if (context.button.isHovered()) {
                            List<ITextComponent> list = itemStack.getTooltipLines(Minecraft.getInstance().player, Minecraft.getInstance().options.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);
                            List<ITextComponent> list1 = Lists.newArrayList(list);
                            Item item = itemStack.getItem();
                            ItemGroup itemgroup = item.getItemCategory();
                            if (itemgroup == null && item == Items.ENCHANTED_BOOK) {
                                Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(itemStack);
                                if (map.size() == 1) {
                                    Enchantment enchantment = map.keySet().iterator().next();
                                    for (ItemGroup itemGroup1 : ItemGroup.TABS) {
                                        if (itemGroup1.hasEnchantmentCategory(enchantment.category)) {
                                            itemgroup = itemGroup1;
                                            break;
                                        }
                                    }
                                }
                            }
                            this.visibleTags.forEach((resourceLocation, itemITag) -> {
                                if (itemITag.contains(item)) {
                                    list1.add(1, (Component.literal("#" + resourceLocation).setColorArgb(0xFF8A2BE2).toTextComponent()));
                                }

                            });
                            if (itemgroup != null) {
                                list1.add(1, itemgroup.getDisplayName().copy().withStyle(TextFormatting.BLUE));
                            }

                            FontRenderer font = itemStack.getItem().getFontRenderer(itemStack);
                            GuiUtils.preItemToolTip(itemStack);
                            this.renderWrappedToolTip(context.matrixStack, list1, (int) context.keyManager.getMouseX(), (int) context.keyManager.getMouseY(), (font == null ? this.font : font));
                            GuiUtils.postItemToolTip();
                        }
                    } else {
                        context.button.setX(0).setY(0).setWidth(0).setHeight(0).setId("");
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
                this.itemList.addAll(Minecraft.getInstance().getSearchTree(SearchTreeManager.CREATIVE_TAGS).search(s.toLowerCase(Locale.ROOT)));
            }
            // $ 描述
            else if (s.startsWith("$")) {
                s = s.substring(1);
                this.itemList.addAll(this.searchByDescription(s));
            } else {
                // @ modId
                if (s.startsWith("@")) s = s.replaceAll("^@(\\S+)", "$1:");
                this.itemList.addAll(Minecraft.getInstance().getSearchTree(SearchTreeManager.CREATIVE_NAMES).search(s.toLowerCase(Locale.ROOT)));
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

        ITagCollection<Item> itagcollection = ItemTags.getAllTags();
        itagcollection.getAvailableTags().stream().filter(predicate).forEach((resourceLocation) -> this.visibleTags.put(resourceLocation, itagcollection.getTag(resourceLocation)));
    }

    private List<ItemStack> searchByDescription(String keyword) {
        return this.getItemList().stream()
                .filter(item -> item.getTooltipLines(Minecraft.getInstance().player, ITooltipFlag.TooltipFlags.ADVANCED)
                        .stream().anyMatch(component -> component.getString().contains(keyword))
                ).collect(Collectors.toList());
    }

    private void setScrollOffset(double offset) {
        this.scrollOffset = (int) Math.max(Math.min(offset, (int) Math.ceil((double) (itemList.size() - itemPerLine * maxLine) / itemPerLine)), 0);
    }

    /**
     * 绘制按钮
     */
    private void renderButton(MatrixStack matrixStack) {
        for (OperationButton button : OP_BUTTONS.values()) button.render(matrixStack, keyManager);
        for (OperationButton button : ITEM_BUTTONS) button.render(matrixStack, keyManager);
        for (OperationButton button : OP_BUTTONS.values())
            button.renderPopup(matrixStack, this.font, keyManager);
        for (OperationButton button : ITEM_BUTTONS)
            button.renderPopup(matrixStack, this.font, keyManager);
    }

    private void handleItem(OperationButton bt, int button, AtomicBoolean flag) {
        if (button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            this.selectedItemId = bt.getId();
            if (StringUtils.isNotNullOrEmpty(this.selectedItemId)) {
                ItemStack itemStack = ItemRewardParser.getItemStack(selectedItemId);
                itemStack.setCount(1);
                this.currentItem = new Reward(itemStack, EnumRewardType.ITEM, this.probability);
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
            StringInputScreen.Args args = new StringInputScreen.Args()
                    .setParentScreen(this)
                    .addWidget(new StringInputScreen.Widget()
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_item_json").setShadow(true))
                            .setDefaultValue(itemRewardJsonString)
                            .setValidator((input) -> {
                                try {
                                    JsonObject jsonObject = GSON.fromJson(input.getValue(), JsonObject.class);
                                    if (((ItemStack) RewardManager.deserializeReward(new Reward(jsonObject, EnumRewardType.ITEM, this.probability))).getItem() == Items.AIR) {
                                        throw new RuntimeException();
                                    }
                                } catch (Exception e) {
                                    return Component.translatableClient(EnumI18nType.TIPS, "item_json_s_error", input.getValue()).toString();
                                }
                                return null;
                            })
                    )
                    .setCallback(input -> {
                        JsonObject jsonObject = GSON.fromJson(input.getFirstValue(), JsonObject.class);
                        ItemStack itemStack = RewardManager.deserializeReward(new Reward(jsonObject, EnumRewardType.ITEM, this.probability));
                        this.currentItem = new Reward(itemStack, EnumRewardType.ITEM, this.probability);
                        this.selectedItemId = ItemRewardParser.getId(itemStack);
                    });
            Minecraft.getInstance().setScreen(new StringInputScreen(args));
        }
        // 编辑数量
        else if (bt.getOperation() == OperationButtonType.COUNT.getCode()) {
            StringInputScreen.Args args = new StringInputScreen.Args()
                    .setParentScreen(this)
                    .addWidget(new StringInputScreen.Widget()
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_item_count").setShadow(true))
                            .setRegex("\\d{0,4}")
                            .setDefaultValue(String.valueOf(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getCount()))
                            .setValidator((input) -> {
                                int count = StringUtils.toInt(input.getValue());
                                if (count <= 0 || count > 64 * 9 * 5) {
                                    return Component.translatableClient(EnumI18nType.TIPS, "item_count_s_error", input.getValue()).toString();
                                }
                                return null;
                            })
                    )
                    .setCallback(input -> {
                        int count = StringUtils.toInt(input.getFirstValue());
                        ItemStack itemStack = RewardManager.deserializeReward(this.currentItem);
                        itemStack.setCount(count);
                        this.currentItem = new Reward(itemStack, EnumRewardType.ITEM, this.probability);
                    });
            Minecraft.getInstance().setScreen(new StringInputScreen(args));
        }
        // 编辑NBT
        else if (bt.getOperation() == OperationButtonType.NBT.getCode()) {
            String itemNbtJsonString = ItemRewardParser.getNbtString(RewardManager.deserializeReward(this.currentItem));
            StringInputScreen.Args args = new StringInputScreen.Args()
                    .setParentScreen(this)
                    .addWidget(new StringInputScreen.Widget()
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_item_nbt").setShadow(true))
                            .setDefaultValue(itemNbtJsonString)
                            .setValidator((input) -> {
                                try {
                                    if (!ItemRewardParser.getItemStack(ItemRewardParser.getId(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getItem()) + input.getValue(), true).hasTag()) {
                                        throw new RuntimeException();
                                    }
                                } catch (Exception e) {
                                    return Component.translatableClient(EnumI18nType.TIPS, "item_nbt_s_error", input.getValue()).toString();
                                }
                                return null;
                            })
                    )
                    .setCallback(input -> {
                        try {
                            ItemStack itemStack = ItemRewardParser.getItemStack(ItemRewardParser.getId(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getItem()) + input.getFirstValue(), true);
                            itemStack.setCount(((ItemStack) RewardManager.deserializeReward(this.currentItem)).getCount());
                            this.currentItem = new Reward(itemStack, EnumRewardType.ITEM, this.probability);
                            this.selectedItemId = ItemRewardParser.getId(itemStack);
                        } catch (Exception e) {
                            input.setRunningResult(e);
                        }
                    });
            Minecraft.getInstance().setScreen(new StringInputScreen(args));
        }
        // 编辑概率
        else if (bt.getOperation() == OperationButtonType.PROBABILITY.getCode()) {
            StringInputScreen.Args args = new StringInputScreen.Args()
                    .setParentScreen(this)
                    .addWidget(new StringInputScreen.Widget()
                            .setTitle(Text.translatable(EnumI18nType.TIPS, "enter_reward_probability").setShadow(true))
                            .setRegex(EnumRegex.PERCENTAGE_5.getRegex())
                            .setDefaultValue(StringUtils.toFixedEx(this.probability, 5))
                            .setValidator((input) -> {
                                BigDecimal p = StringUtils.toBigDecimal(input.getValue());
                                if (p.compareTo(BigDecimal.ZERO) <= 0 || p.compareTo(BigDecimal.ONE) > 0) {
                                    return Component.translatableClient(EnumI18nType.TIPS, "reward_probability_s_error", input.getValue()).toString();
                                }
                                return null;
                            })
                    )
                    .setCallback(input -> this.probability = StringUtils.toBigDecimal(input.getFirstValue()));
            Minecraft.getInstance().setScreen(new StringInputScreen(args));
        }
    }
}
