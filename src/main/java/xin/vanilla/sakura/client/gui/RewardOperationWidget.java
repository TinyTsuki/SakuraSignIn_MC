package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.GLFWKey;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.event.MouseDragEvent;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.widget.BaseWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.sakura.screen.coordinate.Coordinate;

import java.util.function.Consumer;

/**
 * 奖励编辑器操作控件，仅保留 Sakura 主题纹理与业务回调。
 */
@Accessors(chain = true)
public final class RewardOperationWidget extends BaseWidget {
    @Getter
    private final int operation;
    private final Consumer<RenderContext> renderer;
    @Setter
    private xin.vanilla.sakura.client.theme.ThemeTexture normal;
    @Setter
    private xin.vanilla.sakura.client.theme.ThemeTexture hover;
    @Setter
    private xin.vanilla.sakura.client.theme.ThemeTexture pressed;
    @Setter
    private boolean flipHorizontal;
    @Setter
    private boolean flipVertical;
    @Setter
    private double rotatedAngle;
    @Setter
    private double tremblingAmplitude;
    @Setter
    private boolean transparentCheck;
    @Setter
    private int hoverTint;
    @Setter
    private int pressedTint;
    @Setter
    private Text tooltip = Text.empty();
    @Setter
    private ScreenCoordinate visualBounds;
    @Setter
    private ScreenCoordinate clipBounds;
    @Setter
    private Consumer<MouseEvent> pressHandler;
    @Setter
    private Consumer<MouseEvent> releaseHandler;
    @Setter
    private Consumer<MouseDragEvent> dragHandler;

    private boolean dragged;
    private double pressX;
    private double pressY;

    private static final double DRAG_ACTIVATION_DISTANCE = 3.0;

    public RewardOperationWidget(BaniraScreen screen, int operation, Consumer<RenderContext> renderer) {
        super(screen, new ScreenCoordinate());
        this.operation = operation;
        this.renderer = renderer;
    }

    public RewardOperationWidget(BaniraScreen screen, int operation, xin.vanilla.sakura.client.theme.ThemeTexture texture) {
        this(screen, operation, (Consumer<RenderContext>) null);
        this.normal = texture;
    }

    public boolean hovered() {
        return mouseInside;
    }

    public boolean pressed() {
        return mousePressed;
    }

    public RewardOperationWidget setBounds(Coordinate coordinate, double baseX, double baseY, double scale) {
        bounds(new ScreenCoordinate(
                baseX + coordinate.getX() * scale,
                baseY + coordinate.getY() * scale,
                coordinate.getWidth() * scale,
                coordinate.getHeight() * scale
        ));
        return this;
    }

    public double realX() {
        return visualBounds != null ? visualBounds.x() : absoluteX();
    }

    public double realY() {
        return visualBounds != null ? visualBounds.y() : absoluteY();
    }

    public double realWidth() {
        return visualBounds != null ? visualBounds.width() : bounds().width();
    }

    public double realHeight() {
        return visualBounds != null ? visualBounds.height() : bounds().height();
    }

    @Override
    public void render(MatrixStack graphics, float partialTicks) {
        if (!visible()) {
            return;
        }
        MatrixStack stack = graphics;
        if (clipBounds != null) {
            AbstractGuiUtils.pushScissor((int) clipBounds.x(), (int) clipBounds.y(),
                    Math.max(1, (int) clipBounds.width()),
                    Math.max(1, (int) clipBounds.height()));
            try {
                renderContent(graphics, partialTicks);
            } finally {
                AbstractGuiUtils.popScissor();
            }
            return;
        }
        renderContent(graphics, partialTicks);
    }

    private void renderContent(MatrixStack graphics, float partialTicks) {
        MatrixStack stack = graphics;
        if (renderer != null) {
            renderer.accept(new RenderContext(graphics, this));
            return;
        }
        if (normal == null) {
            return;
        }

        xin.vanilla.sakura.client.theme.ThemeTexture texture = mousePressed && pressed != null ? pressed : mouseInside && hover != null ? hover : normal;
        Coordinate coordinate = new Coordinate()
                .setX(realX()).setY(realY())
                .setWidth(realWidth()).setHeight(realHeight());
        if (mouseInside && tremblingAmplitude > 0) {
            TextureAnimationRenderer.drawTrembling(stack, texture,
                    coordinate, tremblingAmplitude);
        } else {
            TextureAnimationRenderer.drawRotated(stack, texture,
                    coordinate, rotatedAngle, flipHorizontal, flipVertical);
        }

        int tint = mousePressed ? pressedTint : mouseInside ? hoverTint : 0;
        if (tint != 0) {
            AbstractGuiUtils.fill(stack, (int) realX(), (int) realY(),
                    (int) realWidth(), (int) realHeight(), tint);
        }
    }

    public void renderTooltip(MatrixStack graphics, double mouseX, double mouseY) {
        if (!visible() || !mouseInside || tooltip == null || tooltip.content().isEmpty()) {
            return;
        }
        MatrixStack stack = graphics;
        TooltipWidget.drawPopupMessage(stack, FontDrawArgs.ofPopo(
                tooltip.clone().stack(stack).font(Minecraft.getInstance().font)
        ).x(mouseX).y(mouseY), screen.getEffectiveTheme(), screen.season());
    }

    @Override
    public boolean isMouseInside(double mouseX, double mouseY) {
        if (clipBounds != null && (mouseX < clipBounds.x()
                || mouseX >= clipBounds.x() + clipBounds.width()
                || mouseY < clipBounds.y()
                || mouseY >= clipBounds.y() + clipBounds.height())) {
            return false;
        }
        if (!super.isMouseInside(mouseX, mouseY) || !transparentCheck || normal == null) {
            return super.isMouseInside(mouseX, mouseY);
        }
        xin.vanilla.sakura.client.theme.ThemeTexture texture = hover != null ? hover : normal;
        if (realWidth() <= 0 || realHeight() <= 0) {
            return true;
        }
        int textureX = (int) ((mouseX - realX()) * texture.width() / realWidth());
        int textureY = (int) ((mouseY - realY()) * texture.height() / realHeight());
        return texture.opaqueAt(textureX, textureY);
    }

    @Override
    protected boolean onMouseClick(MouseEvent event) {
        dragged = false;
        pressX = event.mouseX();
        pressY = event.mouseY();
        if (pressHandler != null) {
            pressHandler.accept(event);
        }
        return event.button() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT
                || event.button() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT;
    }

    @Override
    protected boolean onMouseDrag(MouseDragEvent event) {
        if (!dragged && !isDragActivated(event.mouseX() - pressX, event.mouseY() - pressY)) {
            return true;
        }
        dragged = true;
        if (dragHandler != null) {
            dragHandler.accept(event);
            return true;
        }
        return false;
    }

    static boolean isDragActivated(double deltaX, double deltaY) {
        return Math.hypot(deltaX, deltaY) > DRAG_ACTIVATION_DISTANCE;
    }

    @Override
    protected boolean onMouseRelease(MouseEvent event, boolean inside) {
        boolean activate = inside && !dragged;
        dragged = false;
        if (activate && releaseHandler != null) {
            releaseHandler.accept(event);
        }
        return true;
    }

    @Getter
    public static final class RenderContext {
        private final MatrixStack graphics;
        private final MatrixStack stack;
        private final RewardOperationWidget widget;

        private RenderContext(MatrixStack graphics, RewardOperationWidget widget) {
            this.graphics = graphics;
            this.stack = graphics;
            this.widget = widget;
        }
    }
}
