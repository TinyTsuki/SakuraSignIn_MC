package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import xin.vanilla.sakura.data.StringList;
import xin.vanilla.sakura.enums.EnumEllipsisPosition;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.screen.component.TextList;
import xin.vanilla.sakura.util.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 字符串输入 Screen
 */
public class StringInputScreen extends Screen {
    private final static Component TITLE = Component.literal("StringInputScreen");

    private final Args args;

    /**
     * 输入框
     */
    private final List<TextFieldWidget> inputField = new ArrayList<>();
    /**
     * 已输入内容
     */
    private final List<String> inputValue = new ArrayList<>();
    /**
     * 确认按钮
     */
    private Button submitButton;
    /**
     * 输入错误提示
     */
    private final TextList errorText = new TextList();

    int layoutHeight = 75, yStart = 0;


    public StringInputScreen(Args args) {
        super(TITLE.toTextComponent());
        assert args != null;
        args.validate();
        this.args = args;
    }

    @Data
    @Accessors(chain = true)
    public static class InputWidget {
        private Text title;
        private Text message = Text.translatable(EnumI18nType.TIPS, "enter_something");
        private String validator = "";
        private String defaultValue = "";
        private double sliderMin;
        private double sliderMax;
        private boolean allowEmpty;
        private WidgetType type = WidgetType.TEXT;
    }

    public enum WidgetType {
        TEXT,
        NUMBER,
        COLOR,
        SLIDER,
    }

    @Data
    @Accessors(chain = true)
    public static final class Args {
        /**
         * 父级 Screen
         */
        private Screen parentScreen;
        /**
         * 输入组件列表
         */
        private List<InputWidget> widgets = new ArrayList<>();
        /**
         * 输入数据回调
         */
        private Consumer<StringList> onDataReceived1;
        /**
         * 输入数据回调
         */
        private Function<StringList, StringList> onDataReceived2;
        /**
         * 是否要显示该界面, 若为false则直接关闭当前界面并返回到调用者的 Screen
         */
        private Supplier<Boolean> shouldClose;

        public Args addWidget(InputWidget widget) {
            this.getWidgets().add(widget);
            return this;
        }

        public Args setOnDataReceived(Consumer<StringList> onDataReceived) {
            this.onDataReceived1 = onDataReceived;
            return this;
        }

        public Args setOnDataReceived(Function<StringList, StringList> onDataReceived) {
            this.onDataReceived2 = onDataReceived;
            return this;
        }

        public void validate() {
            Objects.requireNonNull(this.getParentScreen());
            Objects.requireNonNull(this.getWidgets());
            assert !this.getWidgets().isEmpty();
            if (this.getOnDataReceived1() == null)
                Objects.requireNonNull(this.getOnDataReceived2());
            if (this.getOnDataReceived2() == null)
                Objects.requireNonNull(this.getOnDataReceived1());
        }

    }

    @Override
    protected void init() {
        if (args.shouldClose != null && Boolean.TRUE.equals(args.shouldClose.get()))
            Minecraft.getInstance().setScreen(args.getParentScreen());

        this.layoutHeight = args.getWidgets().size() * 45 + 10 + 20;
        this.yStart = (this.height - this.layoutHeight) / 2;

        // 创建文本输入框
        this.inputField.clear();
        for (int i = 0; i < args.getWidgets().size(); i++) {
            InputWidget widget = args.getWidgets().get(i);
            Text text = widget.getMessage();
            TextFieldWidget input = AbstractGuiUtils.newTextFieldWidget(this.font, this.width / 2 - 100, this.yStart + 15 + 45 * i, 200, 20
                    , text.toComponent());
            input.setMaxLength(Integer.MAX_VALUE);
            if (StringUtils.isNotNullOrEmpty(widget.getValidator())) {
                input.setFilter(s -> s.matches(widget.getValidator()));
            }
            if (CollectionUtils.isNotNullOrEmpty(this.inputValue)) {
                input.setValue(this.inputValue.get(i));
            } else {
                input.setValue(widget.getDefaultValue());
            }
            this.inputField.add(input);
            this.addButton(input);
        }

        // 创建提交按钮
        this.submitButton = AbstractGuiUtils.newButton(this.width / 2 + 5, this.yStart + this.layoutHeight - 28, 95, 20, Component.translatableClient(EnumI18nType.OPTION, "cancel"), button -> {
            StringList value = new StringList();
            this.inputField.stream().map(TextFieldWidget::getValue).forEach(value::add);
            if (CollectionUtils.isNullOrEmpty(value) || button.getMessage().getString().equals(I18nUtils.getTranslationClient(EnumI18nType.OPTION, "cancel"))) {
                Minecraft.getInstance().setScreen(args.getParentScreen());
            } else {
                // 获取输入的数据，并执行回调
                if (args.getOnDataReceived1() != null) {
                    args.getOnDataReceived1().accept(value);
                    Minecraft.getInstance().setScreen(args.getParentScreen());
                } else if (args.getOnDataReceived2() != null) {
                    StringList result = args.getOnDataReceived2().apply(value);
                    if (CollectionUtils.isNotNullOrEmpty(result) && result.stream().anyMatch(StringUtils::isNotNullOrEmpty)) {
                        this.errorText.clear();
                        for (String s : result) {
                            this.errorText.add(Text.literal(s).setColorArgb(0xFFFF0000));
                        }
                    } else {
                        Minecraft.getInstance().setScreen(args.getParentScreen());
                    }
                }
            }
        });
        this.addButton(this.submitButton);
        // 创建取消按钮
        this.addButton(AbstractGuiUtils.newButton(this.width / 2 - 100, this.yStart + this.layoutHeight - 28, 95, 20, Component.translatableClient(EnumI18nType.OPTION, "cancel"), button -> {
            // 关闭当前屏幕并返回到调用者的 Screen
            Minecraft.getInstance().setScreen(args.getParentScreen());
        }));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
        this.inputValue.clear();
        this.inputField.forEach(in -> this.inputValue.add(in.getValue()));
        this.renderBackground(matrixStack);
        // 绘制背景
        super.render(matrixStack, mouseX, mouseY, delta);
        // 绘制标题
        for (int i = 0; i < args.getWidgets().size(); i++) {
            InputWidget widget = args.getWidgets().get(i);
            Text text = widget.getTitle();
            AbstractGuiUtils.drawString(text, this.width / 2.0f - 100, this.yStart + 4 + 45 * i);
        }
        // 绘制错误提示
        if (CollectionUtils.isNotNullOrEmpty(this.errorText)) {
            for (int i = 0; i < this.errorText.size(); i++) {
                Text text = this.errorText.get(i);
                AbstractGuiUtils.drawLimitedText(text, this.width / 2.0f - 100, this.yStart - 7 + 45 * (i + 1), 200, EnumEllipsisPosition.MIDDLE);
            }
        }
        if (this.inputField.stream().allMatch(in -> args.getWidgets().get(this.inputField.indexOf(in)).isAllowEmpty() || StringUtils.isNotNullOrEmpty(in.getValue()))) {
            this.submitButton.setMessage(Component.translatableClient(EnumI18nType.OPTION, "submit").toTextComponent());
        } else {
            this.submitButton.setMessage(Component.translatableClient(EnumI18nType.OPTION, "cancel").toTextComponent());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFWKey.GLFW_MOUSE_BUTTON_4) {
            Minecraft.getInstance().setScreen(args.getParentScreen());
            return true;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFWKey.GLFW_KEY_ESCAPE || (keyCode == GLFWKey.GLFW_KEY_BACKSPACE && this.inputField.stream().noneMatch(Widget::isFocused))) {
            Minecraft.getInstance().setScreen(args.getParentScreen());
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
