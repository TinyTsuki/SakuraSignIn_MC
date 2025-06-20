package xin.vanilla.sakura.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.screen.component.KeyEventManager;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 示例
 */
@OnlyIn(Dist.CLIENT)
public class SimpleScreen extends Screen {

    // region init
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 父级 Screen
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    private Screen previousScreen;

    private final KeyEventManager keyManager = new KeyEventManager();

    public SimpleScreen() {
        super(new TranslationTextComponent("screen.sakura_sign_in.simple_title"));
    }
    // endregion

    // region 变量定义

    // endregion 变量定义


    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        keyManager.refresh(mouseX, mouseY);
        // 绘制背景
        this.renderBackground(matrixStack);
    }

    /**
     * 检测鼠标点击事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        keyManager.mouseClicked(button, mouseX, mouseY);
        AtomicBoolean consumed = new AtomicBoolean(false);

        return consumed.get() || super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 检测鼠标松开事件
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        keyManager.refresh(mouseX, mouseY);
        AtomicBoolean consumed = new AtomicBoolean(false);

        keyManager.mouseReleased(button, mouseX, mouseY);
        return consumed.get() || super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 鼠标移动事件
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyManager.mouseMoved(mouseX, mouseY);

        super.mouseMoved(mouseX, mouseY);
    }

    /**
     * 鼠标滚动事件
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        keyManager.mouseScrolled(delta, mouseX, mouseY);
        AtomicBoolean consumed = new AtomicBoolean(false);

        return consumed.get() || super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * 键盘按下事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        keyManager.keyPressed(keyCode);
        AtomicBoolean consumed = new AtomicBoolean(false);

        return consumed.get() || super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 键盘松开事件
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        AtomicBoolean consumed = new AtomicBoolean(false);

        keyManager.keyReleased(keyCode);
        return consumed.get() || super.keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * 窗口打开时是否暂停游戏
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
