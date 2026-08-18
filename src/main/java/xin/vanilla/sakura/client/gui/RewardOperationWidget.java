package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.event.MouseDragEvent;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.widget.BaseWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.screen.coordinate.TextureCoordinate;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.data.GLFWKey;
import xin.vanilla.banira.client.util.TextureUtils;

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
    private ResourceLocation texture;
    @Setter
    private Coordinate normal;
    @Setter
    private Coordinate hover;
    @Setter
    private Coordinate pressed;
    @Setter
    private int textureWidth;
    @Setter
    private int textureHeight;
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

    public RewardOperationWidget(BaniraScreen screen, int operation, ResourceLocation texture) {
        this(screen, operation, (Consumer<RenderContext>) null);
        this.texture = texture;
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
    public void render(PoseStack stack, float partialTicks) {
        if (!visible()) {
            return;
        }
        if (clipBounds != null) {
            AbstractGuiUtils.pushScissor((int) clipBounds.x(), (int) clipBounds.y(),
                    Math.max(1, (int) clipBounds.width()),
                    Math.max(1, (int) clipBounds.height()));
            try {
                renderContent(stack, partialTicks);
            } finally {
                AbstractGuiUtils.popScissor();
            }
            return;
        }
        renderContent(stack, partialTicks);
    }

    private void renderContent(PoseStack stack, float partialTicks) {
        if (renderer != null) {
            renderer.accept(new RenderContext(stack, this));
            return;
        }
        if (texture == null || normal == null) {
            return;
        }

        Coordinate uv = mousePressed && pressed != null ? pressed : mouseInside && hover != null ? hover : normal;
        Coordinate coordinate = new Coordinate()
                .setX(realX()).setY(realY())
                .setWidth(realWidth()).setHeight(realHeight())
                .setU0(uv.getU0()).setV0(uv.getV0())
                .setUWidth(uv.getUWidth()).setVHeight(uv.getVHeight());
        TextureCoordinate textureCoordinate = new TextureCoordinate()
                .setTotalWidth(textureWidth)
                .setTotalHeight(textureHeight);
        if (mouseInside && tremblingAmplitude > 0) {
            TextureAnimationRenderer.drawTrembling(stack, texture, textureCoordinate,
                    coordinate, tremblingAmplitude);
        } else {
            TextureAnimationRenderer.drawRotated(stack, texture, textureCoordinate,
                    coordinate, rotatedAngle, flipHorizontal, flipVertical);
        }

        int tint = mousePressed ? pressedTint : mouseInside ? hoverTint : 0;
        if (tint != 0) {
            AbstractGuiUtils.fill(stack, (int) realX(), (int) realY(),
                    (int) realWidth(), (int) realHeight(), tint);
        }
    }

    public void renderTooltip(PoseStack stack, double mouseX, double mouseY) {
        if (!visible() || !mouseInside || tooltip == null || tooltip.content().isEmpty()) {
            return;
        }
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
        if (!super.isMouseInside(mouseX, mouseY) || !transparentCheck || texture == null) {
            return super.isMouseInside(mouseX, mouseY);
        }
        Coordinate uv = hover != null ? hover : normal;
        if (uv == null || realWidth() <= 0 || realHeight() <= 0) {
            return true;
        }
        int textureX = (int) (uv.getU0() + (mouseX - realX()) * uv.getUWidth() / realWidth());
        int textureY = (int) (uv.getV0() + (mouseY - realY()) * uv.getVHeight() / realHeight());
        NativeImage image = TextureUtils.getTextureImage(texture);
        if (image == null || textureX < 0 || textureY < 0
                || textureX >= image.getWidth() || textureY >= image.getHeight()) {
            return true;
        }
        return ((image.getPixelRGBA(textureX, textureY) >>> 24) & 0xFF) > 0;
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
        private final PoseStack stack;
        private final RewardOperationWidget widget;

        private RenderContext(PoseStack stack, RewardOperationWidget widget) {
            this.stack = stack;
            this.widget = widget;
        }
    }
}
