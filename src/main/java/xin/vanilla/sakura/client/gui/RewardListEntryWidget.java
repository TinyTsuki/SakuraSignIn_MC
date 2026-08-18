package xin.vanilla.sakura.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
    private boolean drawSelectionOutline = true;
    @Setter
    private Text tooltip = Text.empty();
    @Setter
    private boolean tooltipRequiresShift;
    @Setter
    private Consumer<MouseEvent> releaseHandler;
    @Setter
    private Consumer<MouseDragEvent> dragHandler;
    @Setter
    private Consumer<MouseEvent> longPressHandler;
    @Setter
    private Consumer<MouseDragEvent> longPressDragHandler;
    @Setter
    private Consumer<MouseEvent> longPressReleaseHandler;
    private boolean dragged;
    private boolean pendingDrag;
    private boolean longPressDragging;
    private int pressClickCount = 1;
    private boolean pressDoubleClick;
    private boolean pressClickTracked;
    private final double dragActivationDistance = 4.0;
    private double pressX;
    private double pressY;

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
    public void render(GuiGraphics graphics, float partialTicks) {
        if (!visible() || !visibleInViewport()) {
            return;
        }
        PoseStack stack = graphics.pose();
        if (selected && drawSelectionOutline) {
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
            renderer.accept(new RenderContext(graphics, this));
        }
    }

    public void renderTooltip(GuiGraphics graphics, double mouseX, double mouseY) {
        if (!visibleInViewport() || !mouseInside || tooltip == null || tooltip.content().isEmpty()
                || (tooltipRequiresShift && !Screen.hasShiftDown())) {
            return;
        }
        PoseStack stack = graphics.pose();
        TooltipWidget.drawPopupMessage(stack, FontDrawArgs.ofPopo(
                tooltip.clone().stack(stack).font(Minecraft.getInstance().font)
        ).x(mouseX).y(mouseY), screen.getEffectiveTheme(), screen.season());
    }

    @Override
    protected boolean onMouseClick(MouseEvent event) {
        dragged = false;
        pendingDrag = event.button() == 0;
        longPressDragging = false;
        pressX = event.mouseX();
        pressY = event.mouseY();
        pressClickCount = event.clickCount();
        pressDoubleClick = event.doubleClick();
        pressClickTracked = event.clickTracked();
        return true;
    }

    @Override
    protected boolean onMouseRelease(MouseEvent event, boolean inside) {
        event.clickCount(pressClickCount)
                .doubleClick(pressDoubleClick)
                .clickTracked(pressClickTracked);
        if (longPressDragging) {
            if (longPressReleaseHandler != null) {
                longPressReleaseHandler.accept(event);
            }
            longPressDragging = false;
            dragged = false;
            pendingDrag = false;
            return true;
        }
        boolean activate = inside && !dragged;
        dragged = false;
        pendingDrag = false;
        if (activate && releaseHandler != null) {
            releaseHandler.accept(event);
        }
        return true;
    }

    @Override
    protected boolean onMouseDrag(MouseDragEvent event) {
        if (longPressDragging) {
            if (longPressDragHandler != null) {
                longPressDragHandler.accept(event);
            }
            return true;
        }
        double distance = Math.hypot(event.mouseX() - pressX, event.mouseY() - pressY);
        if (pendingDrag) {
            if (distance <= dragActivationDistance) {
                return true;
            }
            if (canDragReward()) {
                startLongPressDrag(MouseEvent.of(
                        event.mouseX(), event.mouseY(), event.button()));
                if (longPressDragHandler != null) {
                    longPressDragHandler.accept(event);
                }
                return true;
            }
            pendingDrag = false;
        }
        dragged = true;
        if (dragHandler != null) {
            dragHandler.accept(event);
            return true;
        }
        return true;
    }

    @Override
    protected void onLongPress(MouseEvent event) {
        if (pendingDrag && !dragged && event.button() == 0 && canDragReward()) {
            startLongPressDrag(event);
        }
    }

    private boolean canDragReward() {
        return longPressHandler != null;
    }

    /**
     * 奖励条目移动超过阈值即开始拖动，静止按住仍沿用长按触发。
     */
    private void startLongPressDrag(MouseEvent event) {
        pendingDrag = false;
        longPressDragging = true;
        dragged = true;
        if (longPressHandler != null) {
            longPressHandler.accept(event);
        }
    }

    @Override
    protected long genericLongPressThresholdMs() {
        return 250L;
    }

    @Getter
    public static final class RenderContext {
        private final GuiGraphics graphics;
        private final PoseStack stack;
        private final RewardListEntryWidget entry;

        private RenderContext(GuiGraphics graphics, RewardListEntryWidget entry) {
            this.graphics = graphics;
            this.stack = graphics.pose();
            this.entry = entry;
        }
    }
}
