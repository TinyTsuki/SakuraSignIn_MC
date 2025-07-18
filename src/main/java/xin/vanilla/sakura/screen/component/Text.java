package xin.vanilla.sakura.screen.component;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.SakuraUtils;

@Setter
@Accessors(chain = true)
@OnlyIn(Dist.CLIENT)
public class Text {
    /**
     * 矩阵栈
     */
    private MatrixStack matrixStack;
    /**
     * 字体渲染器
     */
    private FontRenderer font;
    /**
     * 是否悬浮(需手动设置状态)
     */
    private boolean hovered;
    /**
     * 文本
     */
    private Component text = Component.empty().clone();
    /**
     * 文本对齐方式(仅多行绘制时)
     */
    private Align align = Align.LEFT;
    /**
     * 鼠标悬浮时文本
     */
    private Component hoverText = Component.empty().clone();
    /**
     * 鼠标悬浮时对齐方式(仅多行绘制时)
     */
    private Align hoverAlign = Align.LEFT;

    /**
     * 文字对齐方向(仅多行绘制时)
     */
    public enum Align {
        LEFT, CENTER, RIGHT
    }

    private Text() {
    }

    private Text(String text) {
        this.text = Component.literal(text);
        this.hoverText = Component.literal(text);
    }

    public Text(Component text) {
        this.text = text;
        this.hoverText = text.clone();
    }

    public static Text literal(String text) {
        return new Text(text);
    }

    public static Text empty() {
        return new Text().setText(Component.empty()).setHoverText(Component.empty());
    }

    public static Text translatable(EnumI18nType type, String key, Object... args) {
        return new Text(Component.translatableClient(type, key, args));
    }

    public Text copy() {
        return new Text()
                .setText(this.text.clone())
                .setHoverText(this.hoverText.clone())
                .setHovered(this.hovered)
                .setAlign(this.align)
                .setHoverAlign(this.hoverAlign)
                .setMatrixStack(this.matrixStack)
                .setFont(this.font);
    }

    public Text copyWithoutChildren() {
        return new Text()
                .setText(this.text.clone().clearChildren().clearArgs())
                .setHoverText(this.hoverText.clone().clearChildren().clearArgs())
                .setHovered(this.hovered)
                .setAlign(this.align)
                .setHoverAlign(this.hoverAlign)
                .setMatrixStack(this.matrixStack)
                .setFont(this.font);
    }

    public MatrixStack getMatrixStack() {
        return matrixStack == null ? new MatrixStack() : this.matrixStack;
    }

    public FontRenderer getFont() {
        return font == null ? Minecraft.getInstance().font : this.font;
    }

    public boolean isEmpty() {
        return (this.text == null || this.text.isEmpty()) && (this.hoverText == null || this.hoverText.isEmpty());
    }

    public boolean isColorEmpty() {
        return this.hovered ? this.hoverText.isColorEmpty() : this.text.isColorEmpty();
    }

    public int getColor() {
        return this.hovered ? this.hoverText.getColor() : this.text.getColor();
    }

    public int getColorArgb() {
        return this.hovered ? this.hoverText.getColorArgb() : this.text.getColorArgb();
    }

    public int getColorRgba() {
        return this.hovered ? this.hoverText.getColorRgba() : this.text.getColorRgba();
    }

    public boolean isBgColorEmpty() {
        return this.hovered ? this.hoverText.isBgColorEmpty() : this.text.isBgColorEmpty();
    }

    public int getBgColor() {
        return this.hovered ? this.hoverText.getBgColor() : this.text.getBgColor();
    }

    public int getBgColorArgb() {
        return this.hovered ? this.hoverText.getBgColorArgb() : this.text.getBgColorArgb();
    }

    public int getBgColorRgba() {
        return this.hovered ? this.hoverText.getBgColorRgba() : this.text.getBgColorRgba();
    }

    public String getContent() {
        return getContent(true);
    }

    /**
     * 获取文本内容, 忽略样式
     *
     * @param ignoreStyle 是否忽略样式
     */
    public String getContent(boolean ignoreStyle) {
        return this.hovered ? this.hoverText.getString(SakuraUtils.getClientLanguage(), ignoreStyle, true) : this.text.getString(SakuraUtils.getClientLanguage(), ignoreStyle, true);
    }

    public boolean isShadow() {
        return this.hovered ? this.hoverText.isShadow() : this.text.isShadow();
    }

    public boolean isBold() {
        return this.hovered ? this.hoverText.isBold() : this.text.isBold();
    }

    public boolean isItalic() {
        return this.hovered ? this.hoverText.isItalic() : this.text.isItalic();
    }

    public boolean isUnderlined() {
        return this.hovered ? this.hoverText.isUnderlined() : this.text.isUnderlined();
    }

    public boolean isStrikethrough() {
        return this.hovered ? this.hoverText.isStrikethrough() : this.text.isStrikethrough();
    }

    public boolean isObfuscated() {
        return this.hovered ? this.hoverText.isObfuscated() : this.text.isObfuscated();
    }

    public Align getAlign() {
        return this.hovered ? this.hoverAlign : this.align;
    }

    public Text setColor(int color) {
        this.text.setColor(color);
        this.hoverText.setColor(color);
        return this;
    }

    public Text setColorArgb(int argb) {
        this.text.setColorArgb(argb);
        this.hoverText.setColorArgb(argb);
        return this;
    }

    public Text setColorRgba(int rgba) {
        this.text.setColorRgba(rgba);
        this.hoverText.setColorRgba(rgba);
        return this;
    }

    public Text setBgColor(int bgColor) {
        this.text.setBgColor(bgColor);
        this.hoverText.setBgColor(bgColor);
        return this;
    }

    public Text setBgColorArgb(int argb) {
        this.text.setBgColorArgb(argb);
        this.hoverText.setBgColorArgb(argb);
        return this;
    }

    public Text setBgColorRgba(int rgba) {
        this.text.setBgColorRgba(rgba);
        this.hoverText.setBgColorRgba(rgba);
        return this;
    }

    public Text setText(String text) {
        this.text.setI18nType(EnumI18nType.PLAIN).setText(text);
        this.hoverText.setI18nType(EnumI18nType.PLAIN).setText(text);
        return this;
    }

    public Text setText(Component text) {
        this.text = text;
        this.hoverText = text.clone();
        return this;
    }

    public Text setHoverText(String text) {
        this.hoverText.setI18nType(EnumI18nType.PLAIN).setText(text);
        return this;
    }

    public Text setHoverText(Component text) {
        this.hoverText = text;
        return this;
    }

    public Text setShadow(boolean shadow) {
        this.text.setShadow(shadow);
        this.hoverText.setShadow(shadow);
        return this;
    }

    public Text setBold(boolean bold) {
        this.text.setBold(bold);
        this.hoverText.setBold(bold);
        return this;
    }

    public Text setItalic(boolean italic) {
        this.text.setItalic(italic);
        this.hoverText.setItalic(italic);
        return this;
    }

    public Text setUnderlined(boolean underlined) {
        this.text.setUnderlined(underlined);
        this.hoverText.setUnderlined(underlined);
        return this;
    }

    public Text setStrikethrough(boolean strikethrough) {
        this.text.setStrikethrough(strikethrough);
        this.hoverText.setStrikethrough(strikethrough);
        return this;
    }

    public Text setObfuscated(boolean obfuscated) {
        this.text.setObfuscated(obfuscated);
        this.hoverText.setObfuscated(obfuscated);
        return this;
    }

    public Text setAlign(Align align) {
        this.align = align;
        this.hoverAlign = align;
        return this;
    }

    public Text withStyle(Text text) {
        this.text.withStyle(text.text);
        this.hoverText.withStyle(text.hoverText);
        return this;
    }

    public Component toComponent() {
        return this.hovered ? this.hoverText : this.text;
    }

    public static int getTextComponentColor(IFormattableTextComponent textComponent) {
        return getTextComponentColor(textComponent, 0xFFFFFF);
    }

    public static int getTextComponentColor(IFormattableTextComponent textComponent, int defaultColor) {
        return textComponent.getStyle().getColor() == null ? defaultColor : textComponent.getStyle().getColor().getValue();
    }

    public static Text fromTextComponent(IFormattableTextComponent component) {
        return Text.literal(component.getString())
                .setColor(getTextComponentColor(component))
                .setBold(component.getStyle().isBold())
                .setItalic(component.getStyle().isItalic())
                .setUnderlined(component.getStyle().isUnderlined())
                .setStrikethrough(component.getStyle().isStrikethrough())
                .setObfuscated(component.getStyle().isObfuscated());
    }
}
