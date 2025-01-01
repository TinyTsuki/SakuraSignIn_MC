package xin.vanilla.mc.screen.component;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.mc.util.AbstractGuiUtils;
import xin.vanilla.mc.util.CollectionUtils;
import xin.vanilla.mc.util.StringUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 弹出层选项框
 */
@OnlyIn(Dist.CLIENT)
@Accessors(chain = true)
public class PopupOption {
    private final int topPadding = 2;
    private final int bottomPadding = 2;
    private final int leftPadding = 5;
    private final int rightPadding = 5;
    private final int margin = 2;
    private final List<Text> optionList = new ArrayList<>();
    @Getter
    private final List<Text> renderList = new ArrayList<>();
    private final Map<Integer, Integer> relationMap = new HashMap<>();
    @Setter
    private int radius = 2;
    /**
     * 标识
     */
    @Getter
    private String id;
    private FontRenderer font;
    private int width = -leftPadding - rightPadding;
    private int height = -topPadding - bottomPadding;
    private int screenWidth;
    private int screenHeight;
    private int x;
    private int y;
    private int adjustedX = -1;
    private int adjustedY = -1;
    private int maxWidth = -1;
    private int maxHeight = -1;
    // 当前选中项
    private int selectedIndex = -1;
    /**
     * 滚动偏移量
     */
    private int scrollOffset;
    /**
     * 最大行数，如果超过最大行数，则滚动
     */
    private int maxLines;
    /**
     * 提示文字
     */
    private final Map<Integer, Text> tipsMap = new HashMap<>();
    /**
     * 提示文字是否仅按下按键时显示
     */
    @Setter
    private int tipsKeyCode = -1, tipsModifiers = -1;

    /**
     * 渲染前回调
     */
    @Setter
    private Consumer<PopupOption> beforeRender = null;
    /**
     * 渲染后回调
     */
    @Setter
    private Consumer<PopupOption> afterRender = null;

    private PopupOption(FontRenderer font) {
        this.font = font;
    }

    private PopupOption setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    private PopupOption calculateSize() {
        assert Minecraft.getInstance().screen != null;
        this.screenWidth = Minecraft.getInstance().screen.width;
        this.screenHeight = Minecraft.getInstance().screen.height;
        // 计算弹出层的宽度和高度
        if (this.maxWidth <= 0) this.maxWidth = this.screenWidth - this.margin * 2;
        if (this.maxHeight <= 0) this.maxHeight = this.screenHeight - this.margin * 2;
        this.width = Math.min(AbstractGuiUtils.getTextWidth(this.font, renderList) + this.leftPadding + this.rightPadding, this.maxWidth);
        this.height = Math.min(AbstractGuiUtils.getTextHeight(this.font, renderList) + renderList.size() - 1 + this.topPadding + this.bottomPadding, this.maxHeight);
        // 计算弹出层的最大行数
        this.maxLines = ((this.height - this.topPadding - this.bottomPadding) + 1) / (this.font.lineHeight + 1);
        // 初始化调整后的坐标
        this.adjustedX = this.x + this.margin;
        this.adjustedY = this.y + this.margin;
        // 检查底部空间是否充足
        boolean hasBottomSpace = this.adjustedY + this.height + this.margin <= this.screenHeight;
        // 检查右侧空间是否充足
        boolean hasRightSpace = this.adjustedX + this.width + this.margin <= this.screenWidth;
        // 如果底部空间不足
        if (!hasBottomSpace) {
            // 如果顶部空间不足，调整到鼠标下方
            this.adjustedY = this.y - this.height - this.margin + 1;
        }
        // 如果右侧空间不足
        if (!hasRightSpace) {
            this.adjustedX = this.x - this.width - this.margin + 1;
        }
        // 如果调整后仍然超出屏幕范围，强制限制在屏幕内
        this.adjustedX = Math.max(this.margin, Math.min(this.adjustedX, this.screenWidth - this.width - this.margin));
        this.adjustedY = Math.max(this.margin, Math.min(this.adjustedY, this.screenHeight - this.height - this.margin));
        return this;
    }

    public static PopupOption init(FontRenderer font) {
        return new PopupOption(font);
    }

    public static PopupOption init(FontRenderer font, int x, int y) {
        return new PopupOption(font).setPosition(x, y).calculateSize();
    }

    public PopupOption addOption(@NonNull Text text) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The addOption method must be called after the clear/init method and before the resize method.");
        List<Text> renderList = Arrays.stream(StringUtils.replaceLine(text.getContent()).split("\n")).map(s -> text.copy().setText(s).setHoverText(s)).collect(Collectors.toList());
        for (int i = 0; i < renderList.size(); i++) {
            this.relationMap.put(this.renderList.size() + i, optionList.size());
        }
        this.optionList.add(text);
        this.renderList.addAll(renderList);
        return this;
    }

    public PopupOption addOption(@NonNull String... text) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The addOption method must be called after the clear/init method and before the resize method.");
        for (String s : text) {
            Text literal = Text.literal(s);
            List<Text> renderList = Arrays.stream(StringUtils.replaceLine(s).split("\n")).map(Text::literal).collect(Collectors.toList());
            for (int i = 0; i < renderList.size(); i++) {
                this.relationMap.put(this.renderList.size() + i, optionList.size());
            }
            this.optionList.add(literal);
            this.renderList.addAll(renderList);
        }
        return this;
    }

    public PopupOption addTips(@NonNull Text text, int index) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The addTips method must be called after the clear/init method and before the resize method.");
        tipsMap.put(index, text);
        return this;
    }

    public PopupOption addTips(@NonNull String text, int index) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The addTips method must be called after the clear/init method and before the resize method.");
        tipsMap.put(index, Text.literal(text));
        return this;
    }

    public PopupOption addTips(@NonNull Text text) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The addTips method must be called after the clear/init method and before the resize method.");
        int index = tipsMap.keySet().stream().max(Integer::compareTo).orElse(-1);
        index++;
        tipsMap.put(index, text);
        return this;
    }

    public PopupOption addTips(@NonNull String... text) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The addTips method must be called after the clear/init method and before the resize method.");
        int index = tipsMap.keySet().stream().max(Integer::compareTo).orElse(-1);
        index++;
        for (int i = 0; i < text.length; i++) {
            tipsMap.put(index + i, Text.literal(text[i]));
        }
        return this;
    }

    public PopupOption setMaxWidth(int maxWidth) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The setMaxWidth method must be called before the resize method.");
        this.maxWidth = maxWidth;
        return this;
    }

    public PopupOption setMaxHeight(int maxHeight) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The setMaxHeight method must be called before the resize method.");
        this.maxHeight = maxHeight;
        return this;
    }

    public PopupOption setMaxLines(int maxLines) {
        if (this.x >= 0 || this.y >= 0)
            throw new RuntimeException("The setMaxLines method must be called before the resize method.");
        this.maxHeight = maxLines * (this.font.lineHeight + 1) + this.topPadding + this.bottomPadding - 1;
        return this;
    }

    /**
     * 重新计算位置并准备渲染
     *
     * @param font 字体
     * @param x    横坐标
     * @param y    纵坐标
     * @param id   标识
     */
    public PopupOption build(FontRenderer font, double x, double y, String id) {
        if (CollectionUtils.isNullOrEmpty(this.optionList))
            throw new RuntimeException("The build method must be called after the addOption method.");
        this.font = font;
        this.id = id;
        return this.setPosition((int) x, (int) y).calculateSize();
    }

    public void clear() {
        this.optionList.clear();
        this.renderList.clear();
        this.tipsMap.clear();
        this.width = -topPadding * 2;
        this.height = -topPadding * 2;
        this.screenWidth = 0;
        this.screenHeight = 0;
        this.x = -1;
        this.y = -1;
        this.adjustedX = -1;
        this.adjustedY = -1;
        this.maxWidth = -1;
        this.maxHeight = -1;
        this.selectedIndex = -1;
        this.scrollOffset = 0;
        this.maxLines = 0;
        this.tipsKeyCode = -1;
        this.tipsModifiers = -1;
        this.beforeRender = null;
        this.afterRender = null;
    }

    public boolean isHovered() {
        return CollectionUtils.isNotNullOrEmpty(optionList) && this.relationMap.getOrDefault(selectedIndex, -1) >= 0;
    }

    public int getSelectedIndex() {
        return CollectionUtils.isNullOrEmpty(optionList) ? -1 : this.relationMap.getOrDefault(selectedIndex, -1);
    }

    @NonNull
    public String getSelectedString() {
        return (CollectionUtils.isNotNullOrEmpty(optionList)
                && this.getSelectedIndex() >= 0
                && this.getSelectedIndex() < this.optionList.size()
                && this.relationMap.getOrDefault(selectedIndex, -1) >= 0)
                ? this.optionList.get(this.relationMap.get(selectedIndex)).getContent() : "";
    }

    /**
     * 添加滚动偏移量
     *
     * @param delta 滚动偏移量
     * @return 是否成功添加滚动偏移量
     */
    public boolean addScrollOffset(double delta) {
        boolean result = false;
        // 主题选择器
        if (this.isHovered()) {
            // 选项过多时滚动单位合理增大
            int scrollUnit;
            // 当 renderList.size() 远大于 maxLines 时，滚动单位为 maxLines - 1
            if (renderList.size() / maxLines > 25) {
                scrollUnit = Math.max(1, maxLines - 1);
            }
            // 动态调整滚动单位，滚动速度随选项增多逐渐增加
            else {
                scrollUnit = Math.min(Math.max(1, (int) Math.ceil(Math.sqrt(renderList.size() / (double) maxLines))), maxLines - 1);
            }
            if (delta > 0) {
                scrollOffset = Math.max(scrollOffset - scrollUnit, 0);
                result = true;
            } else if (delta < 0) {
                scrollOffset = Math.min(scrollOffset + scrollUnit, renderList.size() - maxLines);
                result = true;
            }
        }
        return result;
    }

    public void render(double mouseX, double mouseY) {
        this.render(mouseX, mouseY, -1, -1);
    }

    public void render(double mouseX, double mouseY, int keyCode, int modifiers) {
        if (this.beforeRender != null) this.beforeRender.accept(this);
        if (CollectionUtils.isNullOrEmpty(optionList)) return;
        if (Minecraft.getInstance().screen == null) return;

        // 检测鼠标是否在弹出层内
        selectedIndex = -1;
        if (this.adjustedY >= 0 && this.width >= 0) {
            int relativeY = (int) (mouseY - this.adjustedY - this.topPadding);
            if (this.adjustedX < mouseX && mouseX < this.adjustedX + this.width - 1) {
                if (relativeY >= 0 && relativeY <= height) {
                    int lines = 0;
                    int index = -1;
                    for (int i = 0; i < (this.maxLines > 0 ? this.maxLines : renderList.size()); i++) {
                        if (scrollOffset + i >= renderList.size()) break;
                        int curLines = StringUtils.getLineCount(renderList.get(scrollOffset + i).getContent());
                        if (relativeY >= lines * (font.lineHeight + 1) && relativeY < (lines + curLines) * (font.lineHeight + 1) - 1 && relativeY < this.height - this.topPadding - this.bottomPadding) {
                            index = scrollOffset + i;
                        }
                        lines += curLines;
                    }
                    if (index < renderList.size()) {
                        selectedIndex = index;
                    }
                }
            }
        }

        AbstractGuiUtils.setDepth(AbstractGuiUtils.EDepth.TOOLTIP);
        AbstractGuiUtils.fill(adjustedX, adjustedY, width, height, 0x88000000, radius);
        AbstractGuiUtils.fillOutLine(adjustedX, adjustedY, width, height, 1, 0xFF000000, radius);
        int lineOffset = 0;
        for (int i = 0; i < this.maxLines; i++) {
            int index = i + scrollOffset;
            if (index >= 0 && index < renderList.size()) {
                Text text = renderList.get(index);
                if (selectedIndex == index) {
                    AbstractGuiUtils.fill(adjustedX + 1, adjustedY + topPadding + (lineOffset * (this.font.lineHeight + 1)), width - 2, this.font.lineHeight * StringUtils.getLineCount(text.getContent()), 0x88ACACAC);
                }
                if (maxWidth > 0) {
                    AbstractGuiUtils.drawLimitedText(text.setFont(this.font), adjustedX + leftPadding, adjustedY + topPadding + (i * (this.font.lineHeight + 1)), maxWidth, AbstractGuiUtils.EllipsisPosition.MIDDLE);
                } else {
                    AbstractGuiUtils.drawString(text.setFont(this.font), adjustedX + leftPadding, adjustedY + topPadding + (i * (this.font.lineHeight + 1)));
                }
                lineOffset += StringUtils.getLineCount(text.getContent());
            }
        }
        AbstractGuiUtils.resetDepth();
        // 绘制提示
        if (this.tipsKeyCode == -1 || (this.tipsKeyCode == keyCode && this.tipsModifiers == modifiers)) {
            if (this.getSelectedIndex() >= 0 && !tipsMap.isEmpty()) {
                Text text = tipsMap.getOrDefault(this.getSelectedIndex(), Text.literal(""));
                if (StringUtils.isNullOrEmpty(text.getContent())) {
                    text = tipsMap.getOrDefault(this.getSelectedIndex() - renderList.size(), Text.literal(""));
                }
                if (StringUtils.isNotNullOrEmpty(text.getContent())) {
                    AbstractGuiUtils.drawPopupMessage(text.setFont(this.font), (int) mouseX, (int) mouseY, this.screenWidth, this.screenHeight);
                }
            }
        }

        if (this.afterRender != null) this.afterRender.accept(this);
    }
}
