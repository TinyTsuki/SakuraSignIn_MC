package xin.vanilla.sakura.screen.component;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.SakuraSignIn;
import xin.vanilla.sakura.enums.EnumI18nType;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.GLFWKey;
import xin.vanilla.sakura.util.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;

@Getter
@Setter
@OnlyIn(Dist.CLIENT)
@Accessors(chain = true)
public class InventoryButton extends Widget {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 按钮是否被按下
     */
    private boolean pressed;
    /**
     * 当前按下的按键
     */
    private int keyCode = -1, modifiers = -1;
    /**
     * 鼠标按下时坐标
     */
    private int mouseButton = -1, mouseClickX = -1, mouseClickY = -1;
    /**
     * 鼠标是否拖动
     */
    private boolean mouseDrag = false;
    /**
     * 按钮坐标
     */
    private int x_, y_;
    /**
     * 屏幕宽高
     */
    private int screenWidth = 427, screenHeight = 240;
    /**
     * 按钮的UV坐标
     */
    private double u0, v0, uWidth, vHeight, totalWidth, totalHeight;
    /**
     * 按钮点击事件
     */
    private Consumer<InventoryButton> onClick;
    /**
     * 当鼠标拖动结束
     */
    private Consumer<Coordinate> onDragEnd;

    public InventoryButton(int x, int y, int width, int height, String title) {
        super(x, y, width, height, Component.literal(title).toTextComponent());
        this.x_ = x;
        this.y_ = y;
    }

    public InventoryButton setUV(Coordinate coordinate, int totalWidth, int totalHeight) {
        return setUV(coordinate.getU0(), coordinate.getV0(), coordinate.getUWidth(), coordinate.getVHeight(), totalWidth, totalHeight);
    }

    public InventoryButton setUV(double u0, double v0, double uWidth, double vHeight, int totalWidth, int totalHeight) {
        this.u0 = u0;
        this.v0 = v0;
        this.uWidth = uWidth;
        this.vHeight = vHeight;
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;
        return this;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
    }

    public void render_(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        // 无法直接监听鼠标移动事件, 直接在绘制时调用
        this.mouseMoved(mouseX, mouseY);
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            this.screenWidth = screen.width;
            this.screenHeight = screen.height;
        }
        // 绘制自定义纹理
        AbstractGuiUtils.bindTexture(SakuraSignIn.getThemeTexture());
        int offset = this.isHovered && !this.mouseDrag ? 1 : 0;
        AbstractGuiUtils.renderByDepth(matrixStack, AbstractGuiUtils.EDepth.TOOLTIP, (stack) ->
                AbstractGuiUtils.blit(stack, this.x - offset, this.y - offset, this.width + offset * 2, this.height + offset * 2, (int) this.u0, (int) this.v0, (int) this.uWidth, (int) this.vHeight, (int) totalWidth, (int) totalHeight)
        );
        if (this.mouseDrag) {
            Text text;
            if (this.modifiers == GLFWKey.GLFW_MOD_ALT) {
                text = Text.literal(String.format("X: %s\nY: %s"
                        , StringUtils.toPercent((this.x - 2.0d) / (screenWidth - this.width - 2.0d * 2))
                        , StringUtils.toPercent((this.y - 2.0d) / (screenHeight - this.height - 2.0d * 2))));
            } else {
                text = Text.literal(String.format("X: %d\nY: %d", this.x, this.y));
            }
            AbstractGuiUtils.drawPopupMessage(text.setMatrixStack(matrixStack), this.x + (AbstractGuiUtils.multilineTextWidth(text) - this.width) / 2, this.y + this.height / 2, screenWidth, screenHeight);
        } else if (this.isHovered) {
            if (this.modifiers == GLFWKey.GLFW_MOD_SHIFT) {
                AbstractGuiUtils.drawPopupMessage(Text.translatable(EnumI18nType.TIPS, "drag_inventory_button").setMatrixStack(matrixStack), mouseX, mouseY, screenWidth, screenHeight);
            } else {
                AbstractGuiUtils.drawPopupMessage(Text.fromTextComponent(this.getMessage().copy()).setMatrixStack(matrixStack), mouseX, mouseY, screenWidth, screenHeight);
            }
        }
    }

    public boolean mouseClicked_(double mouseX, double mouseY, int button) {
        this.pressed = this.isMouseOver(mouseX, mouseY);
        this.mouseButton = button;
        this.mouseClickX = (int) mouseX;
        this.mouseClickY = (int) mouseY;
        return this.pressed;
    }

    public boolean mouseReleased_(double mouseX, double mouseY, int button) {
        boolean flag = false;
        this.isHovered = this.isMouseOver(mouseX, mouseY);
        if (this.pressed && this.mouseDrag) {
            if (this.modifiers == GLFWKey.GLFW_MOD_ALT) {
                Screen screen = Minecraft.getInstance().screen;
                if (screen != null) {
                    this.onDragEnd.accept(new Coordinate().setX((this.x - 2.0d) / (screen.width - this.width - 2.0d * 2)).setY((this.y - 2.0d) / (screen.height - this.height - 2.0d * 2)));
                    flag = true;
                }
            } else {
                this.onDragEnd.accept(new Coordinate().setX(this.x).setY(this.y));
                flag = true;
            }
            this.x_ = this.x;
            this.y_ = this.y;
        } else if (this.pressed && this.isHovered && button == GLFWKey.GLFW_MOUSE_BUTTON_LEFT) {
            onClick.accept(this);
            flag = true;
        }
        this.pressed = false;
        this.mouseDrag = false;
        this.mouseButton = -1;
        this.mouseClickX = -1;
        this.mouseClickY = -1;
        this.keyCode = -1;
        this.modifiers = -1;
        return flag;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.isHovered = this.isMouseOver(mouseX, mouseY);
        super.setFocused(true);
        if (this.pressed) {
            if (((this.keyCode == GLFWKey.GLFW_KEY_LEFT_CONTROL || this.keyCode == GLFWKey.GLFW_KEY_RIGHT_CONTROL) && this.modifiers == GLFWKey.GLFW_MOD_CONTROL)
                    || ((this.keyCode == GLFWKey.GLFW_KEY_LEFT_ALT || this.keyCode == GLFWKey.GLFW_KEY_RIGHT_ALT) && this.modifiers == GLFWKey.GLFW_MOD_ALT)
                    || this.mouseButton == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT) {
                this.mouseDrag = true;
                this.x = (int) getValidX(this.x_ + (mouseX - this.mouseClickX), this.width);
                this.y = (int) getValidY(this.y_ + (mouseY - this.mouseClickY), this.height);
            }
            // 若拖动过程中松开键盘按键则恢复原位
            else {
                this.mouseDrag = false;
                this.x = (int) getValidX(this.x_, this.width);
                this.y = (int) getValidY(this.y_, this.height);
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    public boolean keyPressed_(int keyCode, int scanCode, int modifiers) {
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        return false;
    }

    public boolean keyReleased_(int keyCode, int scanCode, int modifiers) {
        this.keyCode = -1;
        this.modifiers = -1;
        return false;
    }

    /**
     * 获取有效的坐标X
     */
    public static double getValidX(double x, int width) {
        int screenWidth = 427;
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screenWidth = screen.width;
        }
        return Math.min(screenWidth - 2 - width, Math.max(2, x));
    }

    /**
     * 获取有效的坐标Y
     */
    public static double getValidY(double y, int height) {
        int screenHeight = 240;
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screenHeight = screen.height;
        }
        return Math.min(screenHeight - 2 - height, Math.max(2, y));
    }
}
