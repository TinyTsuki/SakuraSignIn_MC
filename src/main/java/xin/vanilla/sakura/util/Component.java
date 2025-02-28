package xin.vanilla.sakura.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.EI18nType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Accessors(chain = true)
@NoArgsConstructor
// TODO 优化掉这玩意
public class Component implements Cloneable, Serializable {

    // region 属性定义
    /**
     * 文本
     */
    @Getter
    @Setter
    private String text = "";
    /**
     * i18n类型
     */
    @Getter
    @Setter
    private EI18nType i18nType = EI18nType.PLAIN;

    /**
     * 子组件
     */
    private List<Component> children = new ArrayList<>();

    /**
     * 翻译组件参数
     */
    private List<Component> args = new ArrayList<>();

    /**
     * 原始组件
     */
    @Getter
    @Setter
    private Object original = null;

    // region 样式属性

    /**
     * 语言代码
     */
    @Setter
    private String languageCode;
    /**
     * 文本颜色
     */
    @Getter
    private Integer color = 0xFFFFFFFF;
    /**
     * 文本背景色
     */
    @Getter
    private Integer bgColor = 0xFFFFFFFF;
    /**
     * 是否有阴影
     */
    @Setter
    private Boolean shadow;
    /**
     * 是否粗体
     */
    @Setter
    private Boolean bold;
    /**
     * 是否斜体
     */
    @Setter
    private Boolean italic;
    /**
     * 是否下划线
     */
    @Setter
    private Boolean underlined;
    /**
     * 是否中划线
     */
    @Setter
    private Boolean strikethrough;
    /**
     * 是否混淆
     */
    @Setter
    private Boolean obfuscated;
    /**
     * 点击事件
     */
    @Setter
    @Getter
    private ClickEvent clickEvent;
    /**
     * 悬停事件
     */
    @Setter
    @Getter
    private HoverEvent hoverEvent;

    // endregion 样式属性

    // endregion 属性定义

    public Component(String text) {
        this.text = text;
    }

    public Component(String text, EI18nType i18nType) {
        this.text = text;
        this.i18nType = i18nType;
    }

    /**
     * 设置文本颜色，若为RGB，则转换为ARGB
     * 无法判断全透明的情况，全透明直接设置为null
     *
     * @param color 颜色
     */
    public Component setColor(Integer color) {
        if (color == null || (color >> 24) != 0) {
            this.color = color;
        } else {
            this.color = color | 0xFF000000;
        }
        return this;
    }

    /**
     * 设置文本颜色，若为RGB，则转换为ARGB
     * 无法判断全透明的情况，全透明直接设置为null
     *
     * @param bgColor 颜色
     */
    public Component setBgColor(Integer bgColor) {
        if (bgColor == null || (bgColor >> 24) != 0) {
            this.bgColor = bgColor;
        } else {
            this.bgColor = bgColor | 0xFF000000;
        }
        return this;
    }

    // region NonNull Getter

    /**
     * 获取语言代码
     */
    public @NonNull String getLanguageCode() {
        return this.languageCode == null ? SakuraSignIn.DEFAULT_LANGUAGE : this.languageCode;
    }

    /**
     * 是否有阴影
     */
    public boolean isShadow() {
        return this.shadow != null && this.shadow;
    }

    /**
     * 是否粗体
     */
    public boolean isBold() {
        return this.bold != null && this.bold;
    }

    /**
     * 是否斜体
     */
    public boolean isItalic() {
        return this.italic != null && this.italic;
    }

    /**
     * 是否下划线
     */
    public boolean isUnderlined() {
        return this.underlined != null && this.underlined;
    }

    /**
     * 是否中划线
     */
    public boolean isStrikethrough() {
        return this.strikethrough != null && this.strikethrough;
    }

    /**
     * 是否混淆
     */
    public boolean isObfuscated() {
        return this.obfuscated != null && this.obfuscated;
    }

    // endregion NonNull Getter

    // region 样式元素是否为空(用于父组件样式传递)

    /**
     * 语言代码是否为空
     */
    public boolean isLanguageCodeEmpty() {
        return this.languageCode == null;
    }

    /**
     * 文本颜色是否为空
     */
    public boolean isColorEmpty() {
        return this.color == null;
    }

    /**
     * 文本背景色是否为空
     */
    public boolean isBgColorEmpty() {
        return this.bgColor == null;
    }

    /**
     * 阴影状态是否为空
     */
    public boolean isShadowEmpty() {
        return this.shadow == null;
    }

    /**
     * 粗体状态是否为空
     */
    public boolean isBoldEmpty() {
        return this.bold == null;
    }

    /**
     * 斜体状态是否为空
     */
    public boolean isItalicEmpty() {
        return this.italic == null;
    }

    /**
     * 下划线状态是否为空
     */
    public boolean isUnderlinedEmpty() {
        return this.underlined == null;
    }

    /**
     * 中划线状态是否为空
     */
    public boolean isStrikethroughEmpty() {
        return this.strikethrough == null;
    }

    /**
     * 混淆状态是否为空
     */
    public boolean isObfuscatedEmpty() {
        return this.obfuscated == null;
    }

    // endregion 样式元素是否为空(用于父组件样式传递)

    private Component setChildren(List<Component> children) {
        this.children = children;
        return this;
    }

    private Component setArgs(List<Component> args) {
        this.args = args;
        return this;
    }

    public Component clone() {
        try {
            Component component = (Component) super.clone();
            component.setText(this.text)
                    .setI18nType(this.i18nType)
                    .setLanguageCode(this.languageCode)
                    .setColor(this.color)
                    .setBgColor(this.bgColor)
                    .setShadow(this.shadow)
                    .setBold(this.bold)
                    .setItalic(this.italic)
                    .setUnderlined(this.underlined)
                    .setStrikethrough(this.strikethrough)
                    .setObfuscated(this.obfuscated)
                    .setClickEvent(this.clickEvent)
                    .setHoverEvent(this.hoverEvent);

            if (CollectionUtils.isNotNullOrEmpty(this.getChildren())) {
                List<Component> clonedChildren = new ArrayList<>(this.getChildren().size());
                for (Component child : this.getChildren()) {
                    clonedChildren.add(child != null ? child.clone() : null);
                }
                component.setChildren(clonedChildren);
            } else {
                component.setChildren(null);
            }

            if (CollectionUtils.isNotNullOrEmpty(this.getArgs())) {
                List<Component> clonedArgs = new ArrayList<>(this.getArgs().size());
                for (Component arg : this.getArgs()) {
                    clonedArgs.add(arg != null ? arg.clone() : null);
                }
                component.setArgs(clonedArgs);
            } else {
                component.setArgs(null);
            }

            return component;
        } catch (CloneNotSupportedException e) {
            return empty();
        }
    }

    public Component append(Object... objs) {
        return this.append(this.getChildren().size(), objs);
    }

    public Component append(int index, Object... objs) {
        for (int i = 0; i < objs.length; i++) {
            Object obj = objs[i];
            if (obj instanceof Component) {
                this.getChildren().add(index + i, ((Component) obj).withStyle(this));
            } else {
                this.getChildren().add(index + i, new Component(obj.toString()).withStyle(this));
            }
        }
        return this;
    }

    public Component appendArg(Object... objs) {
        return this.appendArg(this.getArgs().size(), objs);
    }

    public Component appendArg(int index, Object... objs) {
        for (int i = 0; i < objs.length; i++) {
            Object obj = objs[i];
            if (obj instanceof Component) {
                this.getArgs().add(index + i, ((Component) obj).withStyle(this));
            } else {
                this.getArgs().add(index + i, new Component(obj.toString()).withStyle(this));
            }
        }
        return this;
    }

    public List<Component> getChildren() {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        return this.children;
    }

    public List<Component> getArgs() {
        if (this.args == null) {
            this.args = new ArrayList<>();
        }
        return this.args;
    }

    public Component clearChildren() {
        if (CollectionUtils.isNotNullOrEmpty(this.children)) {
            this.children = new ArrayList<>();
        }
        return this;
    }

    public Component clearArgs() {
        if (CollectionUtils.isNotNullOrEmpty(this.args)) {
            this.args = new ArrayList<>();
        }
        return this;
    }

    /**
     * 将另一个组件的样式应用到当前组件
     */
    public Component withStyle(Component component) {
        if (this.isLanguageCodeEmpty() && !component.isLanguageCodeEmpty()) {
            this.setLanguageCode(component.getLanguageCode());
        }
        if ((this.isColorEmpty() || this.getColor() == 0xFFFFFFFF) && !component.isColorEmpty()) {
            this.setColor(component.getColor());
        }
        if ((this.isBgColorEmpty() || this.getBgColor() == 0xFFFFFFFF) && !component.isBgColorEmpty()) {
            this.setBgColor(component.getBgColor());
        }
        if (this.isShadowEmpty() && !component.isShadowEmpty()) {
            this.setShadow(component.isShadow());
        }
        if (this.isBoldEmpty() && !component.isBoldEmpty()) {
            this.setBold(component.isBold());
        }
        if (this.isItalicEmpty() && !component.isItalicEmpty()) {
            this.setItalic(component.isItalic());
        }
        if (this.isUnderlinedEmpty() && !component.isUnderlinedEmpty()) {
            this.setUnderlined(component.isUnderlined());
        }
        if (this.isStrikethroughEmpty() && !component.isStrikethroughEmpty()) {
            this.setStrikethrough(component.isStrikethrough());
        }
        if (this.isObfuscatedEmpty() && !component.isObfuscatedEmpty()) {
            this.setObfuscated(component.isObfuscated());
        }
        if (this.clickEvent == null && component.clickEvent != null) {
            this.clickEvent = component.clickEvent;
        }
        if (this.hoverEvent == null && component.hoverEvent != null) {
            this.hoverEvent = component.hoverEvent;
        }
        return this;
    }

    public Style getStyle() {
        Style style = Style.EMPTY;
        if (!isColorEmpty() && getColor() != 0xFFFFFFFF)
            style = style.withColor(TextColor.fromRgb(getColor()));
        style = style.setUnderlined(this.isUnderlined())
                .setStrikethrough(this.isStrikethrough())
                .setObfuscated(this.isObfuscated())
                .withBold(this.isBold())
                .withItalic(this.isItalic())
                .withClickEvent(this.clickEvent)
                .withHoverEvent(this.hoverEvent);
        return style;
    }

    /**
     * 获取文本
     */
    public String toString() {
        return this.getString(this.getLanguageCode(), false, true);
    }

    /**
     * 获取文本
     *
     * @param igStyle 是否忽略样式
     */
    public String toString(boolean igStyle) {
        return this.getString(this.getLanguageCode(), igStyle, true);
    }

    /**
     * 获取指定语言文本
     *
     * @param languageCode 语言代码
     */
    public String getString(String languageCode) {
        return this.getString(languageCode, false, true);
    }

    /**
     * 获取指定语言文本
     *
     * @param languageCode 语言代码
     * @param igStyle      是否忽略样式
     * @param igColor      是否忽略颜色
     */
    public String getString(String languageCode, boolean igStyle, boolean igColor) {
        StringBuilder result = new StringBuilder();
        String colorStr = isColorEmpty() ? "§f" : StringUtils.argbToMinecraftColorString(getColor());
        igColor = igColor && colorStr.equalsIgnoreCase("§f");
        // 如果颜色值为null则说明为透明，则不显示内容，所以返回空文本
        if (!this.isColorEmpty()) {
            if (!igStyle) {
                if (!igColor) {
                    result.append(colorStr);
                }
                // 添加样式：粗体
                if (isBold()) {
                    result.append("§l");
                }
                // 添加样式：斜体
                if (isItalic()) {
                    result.append("§o");
                }
                // 添加样式：下划线
                if (isUnderlined()) {
                    result.append("§n");
                }
                // 添加样式：中划线
                if (isStrikethrough()) {
                    result.append("§m");
                }
                // 添加样式：混淆
                if (isObfuscated()) {
                    result.append("§k");
                }
            }
            if (this.i18nType == EI18nType.PLAIN) {
                result.append(this.text);
            } else if (i18nType == EI18nType.ORIGINAL) {
                result.append(((net.minecraft.network.chat.Component) this.original).getString());
            } else {
                result.append(I18nUtils.getTranslation(I18nUtils.getKey(this.i18nType, this.text), languageCode));
            }
        }
        boolean finalIgColor = igColor;
        this.getChildren().forEach(component -> result.append(component.getString(languageCode, igStyle, finalIgColor)));
        return StringUtils.format(result.toString(), this.getArgs().stream().map(component -> component.getString(languageCode, igStyle, finalIgColor)).toArray());
    }

    /**
     * 获取文本组件
     */
    public net.minecraft.network.chat.Component toTextComponent() {
        return this.toTextComponent(this.getLanguageCode());
    }

    /**
     * 获取文本组件
     *
     * @param languageCode 语言代码
     */
    public net.minecraft.network.chat.Component toTextComponent(String languageCode) {
        List<MutableComponent> components = new ArrayList<>();
        if (this.i18nType == EI18nType.ORIGINAL) {
            components.add((MutableComponent) this.original);
        } else {
            // 如果颜色值为null则说明为透明，则不显示内容，所以返回空文本组件
            if (!this.isColorEmpty()) {
                if (this.i18nType != EI18nType.PLAIN) {
                    String text = I18nUtils.getTranslation(I18nUtils.getKey(this.i18nType, this.text), languageCode);
                    String[] split = text.split(StringUtils.FORMAT_REGEX, -1);
                    for (String s : split) {
                        components.add(new TextComponent(s).withStyle(this.getStyle()));
                    }
                    Pattern pattern = Pattern.compile(StringUtils.FORMAT_REGEX);
                    Matcher matcher = pattern.matcher(text);
                    int i = 0;
                    while (matcher.find()) {
                        String placeholder = matcher.group();
                        int index = placeholder.contains("$") ? StringUtils.toInt(placeholder.split("\\$")[0].substring(1)) - 1 : -1;
                        if (index == -1) {
                            index = i;
                        }
                        Component formattedArg = new Component(placeholder).withStyle(this);
                        if (index < this.getArgs().size()) {
                            if (this.getArgs().get(index) == null) {
                                formattedArg = new Component();
                            } else {
                                Component argComponent = this.getArgs().get(index);
                                if (argComponent.getI18nType() != EI18nType.PLAIN) {
                                    try {
                                        // 颜色代码传递
                                        String colorCode = split[i].replaceAll("^.*?((?:§[\\da-fA-FKLMNORklmnor])*)$", "$1");
                                        formattedArg = new Component(String.format(placeholder.replaceAll("^%\\d+\\$", "%"), colorCode + argComponent)).withStyle(argComponent);
                                    } catch (Exception e) {
                                        // 颜色传递
                                        if (argComponent.isColorEmpty()) {
                                            argComponent.setColor(this.color);
                                        }
                                        formattedArg = argComponent;
                                    }
                                } else {
                                    // 颜色传递
                                    if (argComponent.isColorEmpty()) {
                                        argComponent.setColor(this.color);
                                    }
                                    formattedArg = argComponent;
                                }
                            }
                        }
                        if (components.size() > i) {
                            components.get(i).append(formattedArg.toTextComponent());
                        }
                        i++;
                    }
                } else {
                    components.add(new TextComponent(this.text).withStyle(this.getStyle()));
                }
            }
        }
        components.addAll(this.getChildren().stream().map(component -> (MutableComponent) component.toTextComponent(languageCode)).toList());
        if (components.isEmpty()) {
            components.add(new TextComponent(""));
        }
        MutableComponent result = components.get(0);
        for (int j = 1; j < components.size(); j++) {
            result.append(components.get(j));
        }
        return result.withStyle(this.getStyle());
    }

    /**
     * 获取翻译文本组件
     */
    public net.minecraft.network.chat.Component toTranslatedTextComponent() {
        MutableComponent result = new TranslatableComponent("");
        if (!this.isColorEmpty() || !this.isBgColorEmpty()) {
            if (this.i18nType != EI18nType.PLAIN) {
                Object[] objects = this.getArgs().stream().map(component -> {
                    if (component.i18nType == EI18nType.PLAIN) {
                        return component.toTextComponent();
                    } else {
                        return component.toTranslatedTextComponent();
                    }
                }).toArray();
                if (CollectionUtils.isNotNullOrEmpty(objects)) {
                    result = new TranslatableComponent(I18nUtils.getKey(this.i18nType, this.text), objects);
                } else {
                    result = new TranslatableComponent(I18nUtils.getKey(this.i18nType, this.text));
                }
            } else {
                result = new TextComponent(this.text).withStyle(this.getStyle());
            }
        }
        for (Component child : this.getChildren()) {
            result.append(child.toTranslatedTextComponent());
        }
        return result;
    }

    /**
     * 获取聊天文本组件
     *
     * @return 格式化颜色后的文本组件
     */
    public net.minecraft.network.chat.Component toChatComponent() {
        return this.toChatComponent(this.getLanguageCode());
    }

    /**
     * 获取聊天文本组件
     *
     * @return 格式化颜色后的文本组件
     */
    public net.minecraft.network.chat.Component toChatComponent(String languageCode) {
        return rewriteColor(this.toTextComponent(languageCode));
    }

    // 😵‍💫
    public static net.minecraft.network.chat.Component rewriteColor(net.minecraft.network.chat.Component component) {
        if (component instanceof MutableComponent) {
            TextColor color = component.getStyle().getColor();
            if (color != null && color.serialize().startsWith("#")) {
                Style style = component.getStyle().withColor(TextColor.parseColor(StringUtils.argbToMinecraftColor(StringUtils.argbToHex(color.serialize())).name().toLowerCase()));
                ((MutableComponent) component).setStyle(style);
            }
        }
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) {
            rewriteColor(sibling);
        }
        return component;
    }

    /**
     * 获取空文本组件
     */
    public static Component empty() {
        return new Component();
    }

    /**
     * 获取原始组件
     */
    public static Component original(Object original) {
        return empty().setOriginal(original).setI18nType(EI18nType.ORIGINAL);
    }

    /**
     * 获取文本组件
     *
     * @param text 文本
     */
    public static Component literal(String text) {
        return new Component().setText(text);
    }

    /**
     * 获取翻译文本组件
     *
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatable(String key, Object... args) {
        return new Component(key, EI18nType.NONE).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param type 翻译类型
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatable(EI18nType type, String key, Object... args) {
        return new Component(key, type).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatableClient(String key, Object... args) {
        return new Component(key, EI18nType.NONE).setLanguageCode(SakuraUtils.getClientLanguage()).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param type 翻译类型
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatableClient(EI18nType type, String key, Object... args) {
        return new Component(key, type).setLanguageCode(SakuraUtils.getClientLanguage()).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param languageCode 语言代码
     * @param type         翻译类型
     * @param key          翻译键
     * @param args         参数
     */
    public static Component translatable(String languageCode, EI18nType type, String key, Object... args) {
        return new Component(key, type).setLanguageCode(languageCode).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param player 玩家
     * @param type   翻译类型
     * @param key    翻译键
     * @param args   参数
     */
    public static Component translatable(ServerPlayer player, EI18nType type, String key, Object... args) {
        return new Component(key, type).setLanguageCode(SakuraUtils.getPlayerLanguage(player)).appendArg(args);
    }

    public static Component deserialize(JsonObject jsonObject) {
        Component result = new Component();
        result.setText(jsonObject.get("text").getAsString());
        result.setI18nType(EI18nType.valueOf(jsonObject.get("i18nType").getAsString()));
        result.setLanguageCode(jsonObject.get("languageCode").getAsString());
        result.setColor(jsonObject.get("color").getAsInt());
        result.setBgColor(jsonObject.get("bgColor").getAsInt());
        result.setShadow(jsonObject.get("shadow").getAsBoolean());
        result.setBold(jsonObject.get("bold").getAsBoolean());
        result.setItalic(jsonObject.get("italic").getAsBoolean());
        result.setUnderlined(jsonObject.get("underlined").getAsBoolean());
        result.setStrikethrough(jsonObject.get("strikethrough").getAsBoolean());
        result.setObfuscated(jsonObject.get("obfuscated").getAsBoolean());
        if (jsonObject.has("clickEvent.action") && jsonObject.has("clickEvent.value")) {
            result.setClickEvent(new ClickEvent(ClickEvent.Action.valueOf(jsonObject.get("clickEvent.action").getAsString()), jsonObject.get("clickEvent.value").getAsString()));
        }
        if (jsonObject.has("hoverEvent")) {
            result.setHoverEvent(HoverEvent.deserialize(jsonObject.get("hoverEvent").getAsJsonObject()));
        }
        for (JsonElement childJson : jsonObject.getAsJsonArray("children")) {
            result.getChildren().add(deserialize((JsonObject) childJson));
        }
        for (JsonElement argJson : jsonObject.getAsJsonArray("args")) {
            result.getArgs().add(deserialize((JsonObject) argJson));
        }
        return result;
    }

    public static JsonObject serialize(Component reward) {
        JsonObject result = new JsonObject();
        result.addProperty("text", reward.getText());
        result.addProperty("i18nType", reward.getI18nType().name());
        result.addProperty("languageCode", reward.getLanguageCode());
        result.addProperty("color", reward.getColor());
        result.addProperty("bgColor", reward.getBgColor());
        result.addProperty("shadow", reward.isShadow());
        result.addProperty("bold", reward.isBold());
        result.addProperty("italic", reward.isItalic());
        result.addProperty("underlined", reward.isUnderlined());
        result.addProperty("strikethrough", reward.isStrikethrough());
        result.addProperty("obfuscated", reward.isObfuscated());
        if (reward.getClickEvent() != null) {
            result.addProperty("clickEvent.action", reward.getClickEvent().getAction().getName());
            result.addProperty("clickEvent.value", reward.getClickEvent().getValue());
        }
        if (reward.getHoverEvent() != null) {
            result.add("hoverEvent", reward.getHoverEvent().serialize());
        }
        JsonArray children = new JsonArray();
        for (Component child : reward.getChildren()) {
            children.add(serialize(child));
        }
        result.add("children", children);
        JsonArray args = new JsonArray();
        for (Component arg : reward.getArgs()) {
            args.add(serialize(arg));
        }
        result.add("args", args);
        return result;
    }

}
