package xin.vanilla.sakura.screen.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.sakura.util.AbstractGuiUtils;

/**
 * 自定义的鼠标光标
 */
public class MouseCursor {

    /**
     * 鼠标状态 1 鼠标左键按下 2 鼠标右键按下 4 鼠标中键按下
     */
    private int status = 0;
    /**
     * 鼠标滚动状态
     */
    private int scroll = 0;

    private MouseCursor() {
    }

    /**
     * 初始化鼠标光标
     */
    public static MouseCursor init() {
        // 隐藏鼠标指针
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        return new MouseCursor();
    }

    public void removed() {
        // 恢复鼠标指针
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    /**
     * 绘制鼠标光标
     */
    public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
        int color1 = 0xFF000000;
        int color2 = 0xFF000000;
        int color3 = 0xFF000000;

        if (status == 1 || status == 3 || status == 5 || status == 7) {
            color1 = 0xFF777777;
        }
        if (status == 2 || status == 3 || status == 6 || status == 7) {
            color2 = 0xFF777777;
        }
        if (status == 4 || status == 5 || status == 6 || status == 7) {
            color3 = 0xFF777777;
        }

        AbstractGuiUtils.setDepth(graphics, AbstractGuiUtils.EDepth.MOUSE);
        graphics.fill(mouseX, mouseY + this.scroll, mouseX + 1, mouseY + this.scroll + 1, color3);

        graphics.fill(mouseX - 1, mouseY + 2, mouseX - 1 - 3, mouseY + 2 + 1, color1);
        graphics.fill(mouseX - 1, mouseY + 2, mouseX - 1 - 1, mouseY + 2 + 3, color1);
        graphics.fill(mouseX - 1, mouseY - 1, mouseX - 1 - 3, mouseY - 1 - 1, color1);
        graphics.fill(mouseX - 1, mouseY - 1, mouseX - 1 - 1, mouseY - 1 - 3, color1);

        graphics.fill(mouseX + 2, mouseY + 2, mouseX + 2 + 3, mouseY + 2 + 1, color2);
        graphics.fill(mouseX + 2, mouseY + 2, mouseX + 2 + 1, mouseY + 2 + 3, color2);
        graphics.fill(mouseX + 2, mouseY - 1, mouseX + 2 + 3, mouseY - 1 - 1, color2);
        graphics.fill(mouseX + 2, mouseY - 1, mouseX + 2 + 1, mouseY - 1 - 3, color2);
        AbstractGuiUtils.resetDepth(graphics);
        // 恢复鼠标滚动偏移
        this.scroll = 0;
    }

    /**
     * 鼠标点击事件
     *
     * @param mouseX 鼠标位置X
     * @param mouseY 鼠标位置Y
     * @param button 按下的按钮
     */
    public void mouseClicked(double mouseX, double mouseY, int button) {
        this.updateMouseStatus(button, true);
    }

    /**
     * 鼠标松开事件
     *
     * @param mouseX 鼠标位置X
     * @param mouseY 鼠标位置Y
     * @param button 按下的按钮
     */
    public void mouseReleased(double mouseX, double mouseY, int button) {
        this.updateMouseStatus(button, false);
    }

    /**
     * 鼠标滚动事件
     *
     * @param mouseX 鼠标位置X
     * @param mouseY 鼠标位置Y
     * @param delta  滚动距离
     */
    public void mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll = (int) Math.max(-5, Math.min(5, delta * 2));
    }

    /**
     * 更新鼠标状态
     *
     * @param button  按下的按钮
     * @param pressed 按下或松开
     */
    private void updateMouseStatus(int button, boolean pressed) {
        int op = pressed ? 1 : -1;
        switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT:
                this.status += 1 * op;
                break;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT:
                this.status += 2 * op;
                break;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE:
                this.status += 4 * op;
                break;
        }
        if (this.status < 0) this.status = 0;
        else if (this.status > 7) this.status = 7;
    }
}
