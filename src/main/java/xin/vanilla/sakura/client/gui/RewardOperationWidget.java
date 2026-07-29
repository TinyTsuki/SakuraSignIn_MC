package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
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
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.GLFWKey;

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
    private int hoverTint;
    @Setter
    private int pressedTint;
    @Setter
    private Text tooltip = Text.empty();
    @Setter
    private Consumer<MouseEvent> pressHandler;
    @Setter
    private Consumer<MouseEvent> releaseHandler;
    @Setter
    private Consumer<MouseDragEvent> dragHandler;

    private boolean dragged;

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

    public double realX() {
        return absoluteX();
    }

    public double realY() {
        return absoluteY();
    }

    public double realWidth() {
        return bounds().width();
    }

    public double realHeight() {
        return bounds().height();
    }

    @Override
    public void render(MatrixStack stack, float partialTicks) {
        if (!visible()) {
            return;
        }
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
        AbstractGuiUtils.renderRotatedTexture(stack, texture, textureCoordinate, coordinate,
                0, 0, 1, 0, flipHorizontal, false);

        int tint = mousePressed ? pressedTint : mouseInside ? hoverTint : 0;
        if (tint != 0) {
            AbstractGuiUtils.fill(stack, (int) realX(), (int) realY(),
                    (int) realWidth(), (int) realHeight(), tint);
        }
    }

    public void renderTooltip(MatrixStack stack, double mouseX, double mouseY) {
        if (!visible() || !mouseInside || tooltip == null || tooltip.content().isEmpty()) {
            return;
        }
        TooltipWidget.drawPopupMessage(stack, FontDrawArgs.ofPopo(
                tooltip.clone().stack(stack).font(Minecraft.getInstance().font)
        ).x(mouseX).y(mouseY));
    }

    @Override
    protected boolean onMouseClick(MouseEvent event) {
        dragged = false;
        if (pressHandler != null) {
            pressHandler.accept(event);
        }
        return event.button() == GLFWKey.GLFW_MOUSE_BUTTON_LEFT
                || event.button() == GLFWKey.GLFW_MOUSE_BUTTON_RIGHT;
    }

    @Override
    protected boolean onMouseDrag(MouseDragEvent event) {
        if (Math.abs(event.dragX()) > 0.01 || Math.abs(event.dragY()) > 0.01) {
            dragged = true;
        }
        if (dragHandler != null) {
            dragHandler.accept(event);
            return true;
        }
        return false;
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
        private final MatrixStack stack;
        private final RewardOperationWidget widget;

        private RenderContext(MatrixStack stack, RewardOperationWidget widget) {
            this.stack = stack;
            this.widget = widget;
        }
    }
}
