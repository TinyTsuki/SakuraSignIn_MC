package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import xin.vanilla.sakura.enums.EnumEllipsisPosition;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.screen.component.TextList;
import xin.vanilla.sakura.util.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 内容输入 Screen
 */
public class StringInputScreen extends Screen {
    private final static Component TITLE = Component.literal("StringInputScreen");

    private final Args args;

    // private final List<Button> inputButton = new ArrayList<>();
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
    /**
     * 执行错误提示
     */
    private Text runningErrorText = Text.empty();

    int layoutHeight = 75, yStart = 0;


    public StringInputScreen(Args args) {
        super(TITLE.toTextComponent());
        Objects.requireNonNull(args);
        args.validate();
        this.args = args;
    }

    @Data
    @Accessors(chain = true)
    public static class InputWidget {
        /**
         * 部件名称
         */
        private String name = "";
        /**
         * 标题
         */
        private Text title;
        /**
         * 提示
         */
        private Text message = Text.translatable(EnumI18nType.TIPS, "enter_something");
        /**
         * 输入内容限制(正则表达式)
         */
        private String regex = "";
        /**
         * 默认值
         */
        private String defaultValue = "";
        /**
         * 是否允许为空
         */
        private boolean allowEmpty;
        /**
         * 输入类型
         */
        private WidgetType type = WidgetType.TEXT;
        /**
         * 文件过滤器
         */
        private String fileFilter = "";
        /**
         * 输入内容验证器
         */
        private Function<Results, String> validator = s -> "";

        public InputWidget setTitle(Text title) {
            this.title = title;
            if (StringUtils.isNullOrEmptyEx(this.name)) {
                this.name = title.getContent();
            }
            return this;
        }
    }

    public enum WidgetType {
        TEXT,
        FILE,
        COLOR,
    }

    @Data
    @Accessors(chain = true)
    public static class Results implements Cloneable {
        private Map<String, String> nameMap = new HashMap<>();
        private Map<Integer, String> indexMap = new HashMap<>();
        private String curName = "";
        private int curIndex = -1;

        private String runningResult;

        public String getValue() {
            if (!StringUtils.isNullOrEmptyEx(this.curName)) {
                return this.nameMap.get(this.curName);
            } else if (this.curIndex >= 0) {
                return this.indexMap.get(this.curIndex);
            } else if (this.nameMap.size() == 1) {
                return this.nameMap.values().iterator().next();
            } else if (this.indexMap.size() == 1) {
                return this.indexMap.values().iterator().next();
            } else {
                return null;
            }
        }

        public String getValue(String name) {
            return this.nameMap.getOrDefault(name, null);
        }

        public String getValue(int index) {
            return this.indexMap.getOrDefault(index, null);
        }

        public String getValue(String name, int index) {
            if (this.nameMap.containsKey(name)) {
                return this.nameMap.get(name);
            } else {
                return this.indexMap.getOrDefault(index, null);
            }
        }

        public String getFirstValue() {
            return this.indexMap.getOrDefault(0, null);
        }

        public String getLastValue() {
            return this.indexMap.getOrDefault(this.indexMap.size() - 1, null);
        }

        public Results setValue(String name, int index, String value) {
            if (!StringUtils.isNullOrEmptyEx(name)) {
                this.nameMap.put(name, value);
            }
            this.indexMap.put(index, value);
            return this;
        }

        public Results setRunningResult(String s) {
            this.runningResult = s;
            return this;
        }

        public Results setRunningResult(Exception e) {
            this.runningResult = e.toString();
            return this;
        }

        public boolean isEmpty() {
            return this.nameMap.isEmpty() && this.indexMap.isEmpty();
        }

        @Override
        public Results clone() {
            try {
                Results clone = (Results) super.clone();
                if (this.nameMap != null) {
                    clone.nameMap = new HashMap<>();
                    clone.nameMap.putAll(this.nameMap);
                }
                if (this.indexMap != null) {
                    clone.indexMap = new HashMap<>();
                    clone.indexMap.putAll(this.indexMap);
                }
                return clone;
            } catch (Exception ignored) {
                return new Results();
            }
        }
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
        private Consumer<Results> callback;
        /**
         * 界面不可见状态, 若为true则直接关闭当前界面并返回到调用者的 Screen
         */
        private Supplier<Boolean> invisible = () -> false;

        public Args addWidget(InputWidget widget) {
            this.getWidgets().add(widget);
            return this;
        }

        public void validate() {
            Objects.requireNonNull(this.getParentScreen());
            Objects.requireNonNull(this.getWidgets());
            Objects.requireNonNull(this.getCallback());
            if (this.getWidgets().isEmpty()) {
                throw new RuntimeException();
            }
        }

    }

    @Override
    protected void init() {
        if (args.invisible != null && Boolean.TRUE.equals(args.invisible.get()))
            Minecraft.getInstance().setScreen(args.getParentScreen());

        this.layoutHeight = args.getWidgets().size() * 45 + 10 + 20;
        this.yStart = (this.height - this.layoutHeight) / 2;

        // 创建文本输入框
        this.inputField.clear();
        for (int i = 0; i < args.getWidgets().size(); i++) {
            InputWidget widget = args.getWidgets().get(i);
            Text text = widget.getMessage();
            TextFieldWidget input;
            if (WidgetType.FILE.equals(widget.getType())) {
                input = AbstractGuiUtils.newTextFieldWidget(this.font, this.width / 2 - 100, this.yStart + 15 + 45 * i, 175, 20
                        , text.toComponent());
            } else if (WidgetType.COLOR.equals(widget.getType())) {
                input = AbstractGuiUtils.newTextFieldWidget(this.font, this.width / 2 - 100, this.yStart + 15 + 45 * i, 175, 20
                        , text.toComponent());
            } else {
                input = AbstractGuiUtils.newTextFieldWidget(this.font, this.width / 2 - 100, this.yStart + 15 + 45 * i, 200, 20
                        , text.toComponent());
            }
            input.setMaxLength(Integer.MAX_VALUE);
            if (StringUtils.isNotNullOrEmpty(widget.getRegex())) {
                input.setFilter(s -> s.matches(widget.getRegex()));
            }
            if (CollectionUtils.isNotNullOrEmpty(this.inputValue)) {
                input.setValue(this.inputValue.get(i));
            } else {
                input.setValue(widget.getDefaultValue());
            }
            this.inputField.add(input);
            this.addButton(input);
        }

        // 创建按钮
        // this.inputButton.clear();
        for (int i = 0; i < args.getWidgets().size(); i++) {
            InputWidget widget = args.getWidgets().get(i);
            Button button;
            if (WidgetType.FILE.equals(widget.getType())) {
                int finalI = i;
                button = AbstractGuiUtils.newButton(this.width / 2 + 78, this.yStart + 15 + 45 * i, 20, 20
                        , Component.literal("..."), bt -> Minecraft.getInstance().execute(() -> {
                            try {
                                String png = SakuraUtils.chooseFileString("", widget.getFileFilter());
                                this.inputField.get(finalI).setValue(png);
                            } catch (Exception ignored) {
                            }
                        }));
            } else if (WidgetType.COLOR.equals(widget.getType())) {
                int finalI = i;
                button = AbstractGuiUtils.newButton(this.width / 2 + 78, this.yStart + 15 + 45 * i, 20, 20
                        , Component.literal("..."), bt -> Minecraft.getInstance().execute(() -> {
                            try {
                                String rgbHex = SakuraUtils.chooseRgbHex("");
                                this.inputField.get(finalI).setValue(rgbHex);
                            } catch (Exception ignored) {
                            }
                        }));
            } else {
                button = AbstractGuiUtils.newButton(-this.width, -this.height, 0, 0, Component.empty(), bt -> {
                });
                button.setAlpha(0x00);
            }
            // this.inputButton.add(button);
            this.addButton(button);
        }

        // 创建提交按钮
        this.submitButton = AbstractGuiUtils.newButton(this.width / 2 + 5, this.yStart + this.layoutHeight - 28, 95, 20, Component.translatableClient(EnumI18nType.OPTION, "cancel"), button -> {
            Results results = new Results();
            for (int i = 0; i < this.inputField.size(); i++) {
                results.setValue(args.getWidgets().get(i).getName(), i, this.inputField.get(i).getValue());
            }
            if (results.isEmpty() || button.getMessage().getString().equals(I18nUtils.getTranslationClient(EnumI18nType.OPTION, "cancel"))) {
                Minecraft.getInstance().setScreen(args.getParentScreen());
            } else {
                this.errorText.clear();
                for (int i = 0; i < args.getWidgets().size(); i++) {
                    InputWidget widget = args.getWidgets().get(i);
                    Results clone = results.clone();
                    clone.setCurIndex(i).setCurName(widget.getName());
                    String s = widget.getValidator().apply(clone);
                    this.errorText.add(Text.literal(StringUtils.isNullOrEmptyEx(s) ? "" : s).setColorArgb(0xFFFF0000));
                }
                if (this.errorText.isEmptyEx()) {
                    try {
                        args.getCallback().accept(results);
                    } catch (Exception e) {
                        results.setRunningResult(e);
                    }
                    if (StringUtils.isNullOrEmptyEx(results.getRunningResult())) {
                        Minecraft.getInstance().setScreen(args.getParentScreen());
                    } else {
                        this.runningErrorText = Text.literal(results.getRunningResult()).setColorArgb(0xFFFF0000);
                    }
                }
            }
        });
        this.addButton(this.submitButton);
        // 创建取消按钮
        this.addButton(AbstractGuiUtils.newButton(this.width / 2 - 100, this.yStart + this.layoutHeight - 28, 95, 20, Component.translatableClient(EnumI18nType.OPTION, "cancel"), button -> {
            Minecraft.getInstance().setScreen(args.getParentScreen());
        }));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
        if (args.invisible != null && Boolean.TRUE.equals(args.invisible.get()))
            Minecraft.getInstance().setScreen(args.getParentScreen());

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
        if (CollectionUtils.isNotNullOrEmpty(this.errorText) && !this.errorText.isEmptyEx()) {
            for (int i = 0; i < this.errorText.size(); i++) {
                Text text = this.errorText.get(i);
                AbstractGuiUtils.drawLimitedText(text, this.width / 2.0f - 100, this.yStart - 7 + 45 * (i + 1), 200, EnumEllipsisPosition.MIDDLE);
            }
        }
        // 绘制执行错误提示
        if (this.runningErrorText != null && !this.runningErrorText.isEmpty()) {
            this.runningErrorText = Text.empty();
            String content = this.runningErrorText.getContent();
            SakuraUtils.popupNotify("Something Error!", content, SakuraUtils.NotifyPopupIconType.error);
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
