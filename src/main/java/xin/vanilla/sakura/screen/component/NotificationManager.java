package xin.vanilla.sakura.screen.component;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationManager {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 显示位置
     */
    public enum EPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        BOTTOM_CENTER,
        CENTER
    }

    /**
     * 动画类型
     */
    public enum EAnimation {
        /**
         * 从左到右
         */
        LEFT_TO_RIGHT,
        /**
         * 从右到左
         */
        RIGHT_TO_LEFT,
        /**
         * 从上到下
         */
        TOP_TO_BOTTOM,
        /**
         * 从下到上
         */
        BOTTOM_TO_TOP,
        /**
         * 淡入
         */
        FADE_IN,
    }

    @Data
    @Accessors(chain = true)
    public static class Notification {
        /**
         * 内边距
         */
        private double padding = 5;
        /**
         * 外边距
         */
        private double margin = 5;
        /**
         * 内容
         */
        private Component component;
        /**
         * 背景颜色
         */
        private int bgColor = 0xAADCDCDC;
        /**
         * 描边颜色
         */
        private int borderColor = 0x88000000;
        /**
         * 描边大小
         */
        private int borderSize = 1;
        /**
         * 圆角大小
         */
        private int radius = 3;
        /**
         * 开始时间(ms)
         */
        private long startTime = System.currentTimeMillis();
        /**
         * 显示总时间(ms)
         */
        private long durationTime = 5000;
        /**
         * 动画时间(ms)
         */
        private long animationTime = 600;
        /**
         * 显示位置
         */
        private NotificationManager.EPosition position = NotificationManager.EPosition.TOP_RIGHT;
        /**
         * 动画类型
         */
        private NotificationManager.EAnimation animation = NotificationManager.EAnimation.RIGHT_TO_LEFT;
        /**
         * 当前通知序号
         */
        private int index = 0;
        /**
         * 是否执行完成
         */
        private boolean finished = false;
        /**
         * 上次通知序号
         */
        private int lastIndex = 0;
        /**
         * 上次y坐标
         */
        private double lastY = 0;

        public Notification(Component component) {
            if (component.getColor() == 0xFFFFFFFF) {
                component.setColor(0xFF000000);
            }
            this.component = component;
        }

        @OnlyIn(Dist.CLIENT)
        private void render(double[] preY, double[] preHeight) {
            // 计算动画和时间状态
            long currentTime = System.currentTimeMillis();
            double progress = 0;
            if (currentTime < this.getStartTime()) {
                return;
            } else if (currentTime < this.getStartTime() + this.getDurationTime() + this.getAnimationTime() * 2) {
                long currentAnimationTime;
                if (currentTime - this.getStartTime() < this.getAnimationTime()) {
                    currentAnimationTime = currentTime - this.getStartTime();
                    progress = (double) currentAnimationTime / this.getAnimationTime();
                } else if (currentTime - this.getStartTime() - this.getAnimationTime() < this.getDurationTime()) {
                    progress = 1.0;
                } else {
                    currentAnimationTime = currentTime - this.getStartTime() - this.getAnimationTime() - this.getDurationTime();
                    progress = 1.0 - (double) currentAnimationTime / this.getAnimationTime();
                }
            } else {
                this.setFinished(true);
            }

            Text text = new Text(this.getComponent());
            double width = AbstractGuiUtils.multilineTextWidth(text) + this.getPadding() * 2;
            double height = AbstractGuiUtils.multilineTextHeight(text) + this.getPadding() * 2;
            int screenWidth = Minecraft.getInstance().window.getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().window.getGuiScaledHeight();

            // 计算目标坐标
            double targetX;
            double targetY;
            switch (position) {
                case TOP_LEFT:
                    targetX = this.getMargin();
                    targetY = this.getMargin() + preY[0] + preHeight[0];
                    break;
                case TOP_CENTER:
                    targetX = (screenWidth - width) / 2;
                    targetY = this.getMargin() + preY[0] + preHeight[0];
                    break;
                case TOP_RIGHT:
                    targetX = screenWidth - width - this.getMargin();
                    targetY = this.getMargin() + preY[0] + preHeight[0];
                    break;
                case BOTTOM_LEFT:
                    targetX = this.getMargin();
                    targetY = (preY[0] == 0 ? screenHeight : preY[0]) - this.getMargin() - height;
                    break;
                case BOTTOM_CENTER:
                    targetX = (screenWidth - width) / 2;
                    targetY = (preY[0] == 0 ? screenHeight : preY[0]) - this.getMargin() - height;
                    break;
                case BOTTOM_RIGHT:
                    targetX = screenWidth - width - this.getMargin();
                    targetY = (preY[0] == 0 ? screenHeight : preY[0]) - this.getMargin() - height;
                    break;
                case CENTER:
                    targetX = (screenWidth - width) / 2;
                    targetY = (screenHeight - height) / 2;
                    break;
                default:
                    targetX = this.getMargin();
                    targetY = this.getMargin();
            }

            // 根据动画类型调整坐标
            double x = targetX;
            double y = targetY;
            switch (this.getAnimation()) {
                case RIGHT_TO_LEFT:
                    x += width * (1 - progress);
                    break;
                case LEFT_TO_RIGHT:
                    x -= width * (1 - progress);
                    break;
                case TOP_TO_BOTTOM:
                    y -= height * (1 - progress);
                    break;
                case BOTTOM_TO_TOP:
                    y += height * (1 - progress);
                    break;
                case FADE_IN:
                    int alpha = (int) (0xFF * progress);
                    this.setBgColor((this.getBgColor() & 0x00FFFFFF) | (alpha << 24));
                    break;
                default:
                    break;
            }

            // 若前N位执行完毕，则进行移动至第一位的动画
            if (this.getLastIndex() > 0 && this.getIndex() == 0 && this.getLastY() != y) {
                switch (this.getPosition()) {
                    case TOP_LEFT:
                    case TOP_CENTER:
                    case TOP_RIGHT:
                        this.setIndex(1);
                        y = Math.max(y, this.lastY - 1);
                        break;
                    case BOTTOM_LEFT:
                    case BOTTOM_CENTER:
                    case BOTTOM_RIGHT:
                        this.setIndex(1);
                        y = Math.min(y, this.lastY + 1);
                        break;
                }
            }

            AbstractGuiUtils.setDepth(AbstractGuiUtils.EDepth.POPUP_TIPS);
            // 在计算完的坐标位置绘制消息框背景
            AbstractGuiUtils.fill((int) x, (int) y, (int) width, (int) height, this.getBgColor(), this.getRadius());
            // 绘制消息框描边
            AbstractGuiUtils.fillOutLine((int) x, (int) y, (int) width, (int) height, this.getBorderSize(), this.getBorderColor(), this.getRadius());
            // 绘制消息文字
            AbstractGuiUtils.drawLimitedText(text, x + this.getPadding(), y + this.getPadding(), 0, 0, AbstractGuiUtils.EllipsisPosition.MIDDLE);
            AbstractGuiUtils.resetDepth();

            this.setLastY(y);
            this.setLastIndex(this.getIndex());
            preY[0] = y;
            preHeight[0] = height;
            // LOGGER.debug("Render notification: {}, x:{}, y:{}, i:{}, at:{}, dt:{}", this.getComponent().toString(), x, y, this.getIndex(), this.getCurrentAnimationTick(), this.getCurrentDurationTick());
        }
    }

    @Getter
    private final static NotificationManager instance = new NotificationManager();

    private final Map<EPosition, List<Notification>> notifications = new ConcurrentHashMap<>();

    public void addNotification(Notification notification) {
        notifications.computeIfAbsent(notification.getPosition(), k -> new ArrayList<>()).add(notification);
    }

    @OnlyIn(Dist.CLIENT)
    public void render() {
        for (Map.Entry<EPosition, List<Notification>> entry : notifications.entrySet()) {
            double[] y = new double[]{0};
            double[] h = new double[]{0};
            for (int i = 0; i < entry.getValue().size(); i++) {
                Notification notification = entry.getValue().get(i);
                notification.setIndex(i);
                notification.render(y, h);
            }
        }
        // 移除已经执行完成的通知
        notifications.forEach((k, v) -> v.removeIf(Notification::isFinished));
    }

}
