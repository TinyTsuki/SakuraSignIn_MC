package xin.vanilla.mc.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.mc.config.StringList;
import xin.vanilla.mc.screen.component.Text;
import xin.vanilla.mc.screen.component.TextList;
import xin.vanilla.mc.util.AbstractGuiUtils;
import xin.vanilla.mc.util.CollectionUtils;
import xin.vanilla.mc.util.I18nUtils;
import xin.vanilla.mc.util.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 字符串输入 Screen
 */
public class StringInputScreen extends Screen {

    /**
     * 父级 Screen
     */
    private final Screen previousScreen;
    /**
     * 标题
     */
    private final TextList titleText;
    /**
     * 提示
     */
    private final TextList messageText;
    /**
     * 输入数据校验
     */
    private final StringList validator;
    /**
     * 输入数据回调1
     */
    private final Consumer<StringList> onDataReceived1;
    /**
     * 输入数据回调2
     */
    private final Function<StringList, StringList> onDataReceived2;
    /**
     * 是否要显示该界面, 若为false则直接关闭当前界面并返回到调用者的 Screen
     */
    private final Supplier<Boolean> shouldClose;
    /**
     * 输入框
     */
    private final List<EditBox> inputField = new ArrayList<>();
    /**
     * 已输入内容
     */
    private final List<String> inputValue = new ArrayList<>();
    /**
     * 确认按钮
     */
    private Button submitButton;
    /**
     * 输入框默认值
     */
    private final StringList defaultValue;
    /**
     * 输入错误提示
     */
    private final TextList errorText = new TextList();

    int layoutHeight = 75, yStart = 0;


    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, Consumer<StringList> onDataReceived) {
        this(callbackScreen, new TextList(titleText), new TextList(messageText), new StringList(validator), onDataReceived);
    }


    public StringInputScreen(Screen callbackScreen, TextList titleText, TextList messageText, StringList validator, Consumer<StringList> onDataReceived) {
        super(Component.literal("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = new StringList("");
        this.shouldClose = null;
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, String defaultValue, Consumer<StringList> onDataReceived) {
        this(callbackScreen, new TextList(titleText), new TextList(messageText), new StringList(validator), new StringList(defaultValue), onDataReceived);
    }

    public StringInputScreen(Screen callbackScreen, TextList titleText, TextList messageText, StringList validator, StringList defaultValue, Consumer<StringList> onDataReceived) {
        super(Component.literal("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = defaultValue;
        this.shouldClose = null;
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, String defaultValue, Consumer<StringList> onDataReceived, Supplier<Boolean> shouldClose) {
        this(callbackScreen, new TextList(titleText), new TextList(messageText), new StringList(validator), new StringList(defaultValue), onDataReceived, shouldClose);
    }

    public StringInputScreen(Screen callbackScreen, TextList titleText, TextList messageText, StringList validator, StringList defaultValue, Consumer<StringList> onDataReceived, Supplier<Boolean> shouldClose) {
        super(Component.literal("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = defaultValue;
        this.shouldClose = shouldClose;
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, Function<StringList, StringList> onDataReceived) {
        this(callbackScreen, new TextList(titleText), new TextList(messageText), new StringList(validator), onDataReceived);
    }

    public StringInputScreen(Screen callbackScreen, TextList titleText, TextList messageText, StringList validator, Function<StringList, StringList> onDataReceived) {
        super(Component.literal("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = new StringList("");
        this.shouldClose = null;
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, String defaultValue, Function<StringList, StringList> onDataReceived) {
        this(callbackScreen, new TextList(titleText), new TextList(messageText), new StringList(validator), new StringList(defaultValue), onDataReceived);
    }

    public StringInputScreen(Screen callbackScreen, TextList titleText, TextList messageText, StringList validator, StringList defaultValue, Function<StringList, StringList> onDataReceived) {
        super(Component.literal("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = defaultValue;
        this.shouldClose = null;
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, String defaultValue, Function<StringList, StringList> onDataReceived, Supplier<Boolean> shouldClose) {
        this(callbackScreen, new TextList(titleText), new TextList(messageText), new StringList(validator), new StringList(defaultValue), onDataReceived, shouldClose);
    }

    public StringInputScreen(Screen callbackScreen, TextList titleText, TextList messageText, StringList validator, StringList defaultValue, Function<StringList, StringList> onDataReceived, Supplier<Boolean> shouldClose) {
        super(Component.literal("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = defaultValue;
        this.shouldClose = shouldClose;
    }

    @Override
    protected void init() {
        if (this.shouldClose != null && Boolean.TRUE.equals(this.shouldClose.get()))
            Minecraft.getInstance().setScreen(previousScreen);

        this.layoutHeight = this.titleText.size() * 45 + 10 + 20;
        this.yStart = (this.height - this.layoutHeight) / 2;

        // 创建文本输入框
        this.inputField.clear();
        for (int i = 0; i < this.titleText.size(); i++) {
            Text text = this.messageText.get(i);
            EditBox input = AbstractGuiUtils.newTextFieldWidget(this.font, this.width / 2 - 100, this.yStart + 15 + 45 * i, 200, 20
                    , AbstractGuiUtils.textToComponent(text));
            input.setMaxLength(Integer.MAX_VALUE);
            if (CollectionUtils.isNotNullOrEmpty(this.validator)) {
                String regex = this.validator.get(i);
                if (StringUtils.isNotNullOrEmpty(regex)) {
                    input.setFilter(s -> s.matches(regex));
                }
            }
            if (CollectionUtils.isNotNullOrEmpty(this.inputValue)) {
                input.setValue(this.inputValue.get(i));
            } else {
                input.setValue(this.defaultValue.get(i));
            }
            this.inputField.add(input);
            this.addRenderableWidget(input);
        }

        // 创建提交按钮
        this.submitButton = AbstractGuiUtils.newButton(this.width / 2 + 5, this.yStart + this.layoutHeight - 28, 95, 20, Component.literal(I18nUtils.getByZh("取消")), button -> {
            StringList value = new StringList();
            this.inputField.stream().map(EditBox::getValue).forEach(value::add);
            if (CollectionUtils.isNullOrEmpty(value) || button.getMessage().getString().equals(I18nUtils.getByZh("取消"))) {
                // 关闭当前屏幕并返回到调用者的 Screen
                Minecraft.getInstance().setScreen(previousScreen);
            } else {
                // 获取输入的数据，并执行回调
                if (onDataReceived1 != null) {
                    onDataReceived1.accept(value);
                    // 关闭当前屏幕并返回到调用者的 Screen
                    Minecraft.getInstance().setScreen(previousScreen);
                } else if (onDataReceived2 != null) {
                    StringList result = onDataReceived2.apply(value);
                    if (CollectionUtils.isNotNullOrEmpty(result)) {
                        this.errorText.clear();
                        for (String s : result) {
                            this.errorText.add(Text.literal(s).setColor(0xFFFF0000));
                        }
                    } else {
                        // 关闭当前屏幕并返回到调用者的 Screen
                        Minecraft.getInstance().setScreen(previousScreen);
                    }
                }
            }
        });
        this.addRenderableWidget(this.submitButton);
        // 创建取消按钮
        this.addRenderableWidget(AbstractGuiUtils.newButton(this.width / 2 - 100, this.yStart + this.layoutHeight - 28, 95, 20, Component.literal(I18nUtils.getByZh("取消")), button -> {
            // 关闭当前屏幕并返回到调用者的 Screen
            Minecraft.getInstance().setScreen(previousScreen);
        }));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.inputValue.clear();
        this.inputField.forEach(in -> this.inputValue.add(in.getValue()));
        // this.renderBackground(graphics);
        // 绘制背景
        super.render(graphics, mouseX, mouseY, partialTicks);
        // 绘制标题
        for (int i = 0; i < titleText.size(); i++) {
            Text text = titleText.get(i);
            AbstractGuiUtils.drawString(text.setGraphics(graphics), this.width / 2.0f - 100, this.yStart + 4 + 45 * i);
        }
        // 绘制错误提示
        if (CollectionUtils.isNotNullOrEmpty(this.errorText)) {
            for (int i = 0; i < this.errorText.size(); i++) {
                Text text = this.errorText.get(i);
                AbstractGuiUtils.drawLimitedText(text.setGraphics(graphics), this.width / 2.0f - 100, this.yStart - 7 + 45 * (i + 1), 200, AbstractGuiUtils.EllipsisPosition.MIDDLE);
            }
        }
        if (this.inputField.stream().allMatch(in -> StringUtils.isNotNullOrEmpty(in.getValue()))) {
            this.submitButton.setMessage(Component.literal(I18nUtils.getByZh("提交")));
        } else {
            this.submitButton.setMessage(Component.literal(I18nUtils.getByZh("取消")));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    /**
     * 重写键盘事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (keyCode == GLFW.GLFW_KEY_BACKSPACE && this.inputField.stream().noneMatch(EditBox::isFocused))) {
            Minecraft.getInstance().setScreen(previousScreen);
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
}
