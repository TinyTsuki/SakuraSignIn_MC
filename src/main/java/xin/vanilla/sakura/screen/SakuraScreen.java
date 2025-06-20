package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import xin.vanilla.sakura.screen.component.KeyEventManager;
import xin.vanilla.sakura.screen.component.MouseCursor;
import xin.vanilla.sakura.screen.component.PopupOption;
import xin.vanilla.sakura.util.Component;
import xin.vanilla.sakura.util.SakuraUtils;

import javax.annotation.ParametersAreNonnullByDefault;

public abstract class SakuraScreen extends Screen {

    // region init

    /**
     * 父级 Screen
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    private Screen previousScreen;

    @Getter
    private long renderCount = 0;

    /**
     * 键盘与鼠标事件管理器
     */
    protected final KeyEventManager keyManager = new KeyEventManager();

    /**
     * 鼠标光标
     */
    protected MouseCursor cursor;
    /**
     * 弹出层选项
     */
    protected PopupOption popupOption;


    // endregion init

    protected SakuraScreen(ITextComponent textComponent) {
        super(textComponent);
    }

    protected SakuraScreen(Component component) {
        super(component.toTextComponent(SakuraUtils.getClientLanguage()));
    }


    @Override
    protected void init() {
        this.cursor = MouseCursor.init();
        this.popupOption = PopupOption.init(super.font);

        init_();

        super.init();
    }

    abstract void init_();

    abstract void updateLayout();


    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderCount++;
        this.keyManager.refresh(mouseX, mouseY);

        this.render_(matrixStack, partialTicks);

        // 绘制弹出选项
        this.popupOption.render(matrixStack, keyManager);
        // 绘制鼠标光标
        this.cursor.draw(matrixStack, mouseX, mouseY);
    }

    abstract void render_(MatrixStack matrixStack, float partialTicks);


    @Override
    public void removed() {
        this.cursor.removed();

        this.removed_();

        super.removed();
    }

    abstract void removed_();


    @Data
    @Accessors(chain = true)
    public static class MouseClickedHandleArgs {
        private boolean consumed = false;
        private boolean layout = false;
        double mouseX;
        double mouseY;
        private int button;

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.cursor.mouseClicked(mouseX, mouseY, button);
        this.keyManager.mouseClicked(button, mouseX, mouseY);

        MouseClickedHandleArgs args = new MouseClickedHandleArgs()
                .setMouseX(mouseX)
                .setMouseY(mouseY)
                .setButton(button);

        if (!this.popupOption.isHovered()) {
            // 清空弹出选项
            this.popupOption.clear();

            mouseClicked_(args);
        }

        if (args.isLayout()) this.updateLayout();
        return args.isConsumed() || super.mouseClicked(mouseX, mouseY, button);
    }

    abstract void mouseClicked_(MouseClickedHandleArgs args);


    @Data
    @Accessors(chain = true)
    public static class MouseReleasedHandleArgs {
        private boolean consumed = false;
        private boolean layout = false;
        double mouseX;
        double mouseY;
        private int button;

    }

    /**
     * 检测鼠标松开事件
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.cursor.mouseReleased(mouseX, mouseY, button);
        this.keyManager.refresh(mouseX, mouseY);
        MouseReleasedHandleArgs args = new MouseReleasedHandleArgs()
                .setMouseX(mouseX)
                .setMouseY(mouseY)
                .setButton(button);

        if (!this.keyManager.isMouseMoved()) {
            if (this.popupOption.isHovered()) {
                this.handlePopupOption(args);
                this.popupOption.clear();
            } else {
                this.mouseReleased_(args);
            }
        } else {
            this.mouseReleased_(args);
        }

        this.keyManager.mouseReleased(button, mouseX, mouseY);
        if (args.isLayout()) this.updateLayout();
        return args.isConsumed() || super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 处理弹出选项
     */
    abstract void handlePopupOption(MouseReleasedHandleArgs args);

    abstract void mouseReleased_(MouseReleasedHandleArgs args);


    /**
     * 鼠标移动事件
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.keyManager.mouseMoved(mouseX, mouseY);

        this.mouseMoved_();

        super.mouseMoved(mouseX, mouseY);
    }

    abstract void mouseMoved_();


    @Data
    @Accessors(chain = true)
    public static class MouseScoredHandleArgs {
        private boolean consumed = false;
        private boolean layout = false;
        double mouseX;
        double mouseY;
        private double delta;

    }

    /**
     * 鼠标滚动事件
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.cursor.mouseScrolled(mouseX, mouseY, delta);
        this.keyManager.mouseScrolled(delta, mouseX, mouseY);
        MouseScoredHandleArgs args = new MouseScoredHandleArgs()
                .setMouseX(mouseX)
                .setMouseY(mouseY)
                .setDelta(delta);

        this.mouseScrolled_(args);

        if (args.isLayout()) this.updateLayout();
        return args.isConsumed() || super.mouseScrolled(mouseX, mouseY, delta);
    }

    abstract void mouseScrolled_(MouseScoredHandleArgs args);


    @Data
    @Accessors(chain = true)
    public static class KeyPressedHandleArgs {
        private boolean consumed = false;
        private boolean layout = false;
        private int key;
        private int scan;
        private int modifiers;

    }

    /**
     * 键盘按下事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.keyManager.keyPressed(keyCode);
        KeyPressedHandleArgs args = new KeyPressedHandleArgs()
                .setKey(keyCode)
                .setScan(scanCode)
                .setModifiers(modifiers);

        this.keyPressed_(args);

        if (args.isLayout()) this.updateLayout();
        return args.isConsumed() || super.keyPressed(keyCode, scanCode, modifiers);
    }

    abstract void keyPressed_(KeyPressedHandleArgs args);


    @Data
    @Accessors(chain = true)
    public static class KeyReleasedHandleArgs {
        private boolean consumed = false;
        private boolean layout = false;
        private int key;
        private int scan;
        private int modifiers;

    }

    /**
     * 键盘松开事件
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        KeyReleasedHandleArgs args = new KeyReleasedHandleArgs()
                .setKey(keyCode)
                .setScan(scanCode)
                .setModifiers(modifiers);

        this.keyReleased_(args);

        this.keyManager.keyReleased(keyCode);
        if (args.isLayout()) this.updateLayout();
        return args.isConsumed() || super.keyReleased(keyCode, scanCode, modifiers);
    }

    abstract void keyReleased_(KeyReleasedHandleArgs args);

    @Override
    public void onClose() {
        super.onClose();

        this.onClose_();

        if (this.previousScreen != null) {
            Minecraft.getInstance().setScreen(this.previousScreen);
        }
    }

    abstract void onClose_();
}
