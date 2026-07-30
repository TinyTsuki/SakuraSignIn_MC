package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.event.MouseDragEvent;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
import xin.vanilla.banira.client.gui.widget.BaseWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;

import java.util.function.Consumer;

/**
 * 奖励编辑器条目，复用 Banira 的命中、形状和提示绘制能力。
 */
@Accessors(chain = true)
public final class RewardListEntryWidget extends BaseWidget {
    @Getter
    private final int operation;
    private final Consumer<RenderContext> renderer;
    @Setter
    private double baseX;
    @Setter
    private double baseY;
    @Setter
    private int viewportHeight = Integer.MAX_VALUE;
    @Setter
    private boolean selected;
    @Setter
    private int selectedColor = 0xCCFFFFFF;
    @Setter
    private Text tooltip = Text.empty();
    @Setter
    private Consumer<MouseEvent> releaseHandler;
    @Setter
    private Consumer<MouseDragEvent> dragHandler;
    private boolean dragged;

    public RewardListEntryWidget(BaniraScreen screen, int operation, Consumer<RenderContext> renderer) {
        super(screen, new ScreenCoordinate());
        this.operation = operation;
        this.renderer = renderer;
    }

    public double realX() {
        return baseX + bounds().x();
    }

    public double realY() {
        return baseY + bounds().y();
    }

    public double realWidth() {
        return bounds().width();
    }

    public double realHeight() {
        return bounds().height();
    }

    public boolean visibleInViewport() {
        return realY() < viewportHeight && realY() + realHeight() >= 0;
    }

    @Override
    public double absoluteX() {
        return realX();
    }

    @Override
    public double absoluteY() {
        return realY();
    }

    @Override
    public void render(MatrixStack stack, float partialTicks) {
        if (!visible() || !visibleInViewport()) {
            return;
        }
        if (selected) {
            ShapeDrawArgs.RectParams rect = new ShapeDrawArgs.RectParams()
                    .x((float) realX() - 1)
                    .y((float) realY() - 1)
                    .width((float) realWidth() + 2)
                    .height((float) realHeight() + 2)
                    .radius(2)
                    .border(1);
            BaseShapeWidget.drawShape(new ShapeDrawArgs()
                    .stack(stack)
                    .type(ShapeDrawArgs.ShapeType.RECT)
                    .color(selectedColor)
                    .rect(rect));
        }
        if (renderer != null) {
            renderer.accept(new RenderContext(stack, this));
        }
    }

    public void renderTooltip(MatrixStack stack, double mouseX, double mouseY) {
        if (!visibleInViewport() || !mouseInside || tooltip == null || tooltip.content().isEmpty()) {
            return;
        }
        TooltipWidget.drawPopupMessage(stack, FontDrawArgs.ofPopo(
                tooltip.clone().stack(stack).font(Minecraft.getInstance().font)
        ).x(mouseX).y(mouseY), screen.getEffectiveTheme(), screen.season());
    }

    @Override
    protected boolean onMouseClick(MouseEvent event) {
        dragged = false;
        return true;
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

    @Getter
    public static final class RenderContext {
        private final MatrixStack stack;
        private final RewardListEntryWidget entry;

        private RenderContext(MatrixStack stack, RewardListEntryWidget entry) {
            this.stack = stack;
            this.entry = entry;
        }
    }
}
