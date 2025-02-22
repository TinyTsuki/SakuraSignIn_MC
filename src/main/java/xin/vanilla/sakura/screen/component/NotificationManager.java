package xin.vanilla.sakura.screen.component;

import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.sakura.screen.coordinate.Coordinate;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.Component;

import java.util.*;

public class NotificationManager {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 显示位置
     */
    public enum EPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
        CENTER
    }

    /**
     * 动画类型
     */
    public enum EAnimation {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT,
        TOP_TO_BOTTOM, BOTTOM_TO_TOP,
        FADE_IN
    }

    @Data
    @Accessors(chain = true)
    public static class Notification {

        // region 样式配置
        /**
         * 内边距
         */
        private double padding = 5;
        /**
         * 外边距
         */
        private double margin = 5;
        /**
         * 背景颜色
         */
        private int bgColor = 0xAADCDCDC;
        /**
         * 边框颜色
         */
        private int borderColor = 0x88000000;
        /**
         * 边框大小
         */
        private int borderSize = 1;
        /**
         * 圆角半径
         */
        private int radius = 3;
        /**
         * 内容
         */
        private Component component;
        // endregion

        // region 时间控制
        /**
         * 计划开始渲染时间
         */
        private long scheduledTime = System.currentTimeMillis();
        /**
         * 实际开始渲染时间
         */
        private long startTime = -1;
        /**
         * 持续显示时间，非总显示时间<br/>
         * 总显示时间需在此基础上加上动画时间*2
         */
        private long durationTime = 5000;
        /**
         * 动画时间
         */
        private long animationTime = 600;
        // endregion

        // region 动态速度参数
        /**
         * 最大速度, 像素/秒
         */
        private double maxSpeed = 120.0;
        /**
         * 加速度, 像素/秒²
         */
        private double acceleration = 400.0;
        /**
         * 减速开始距离, 像素
         */
        private double decelerationDistance = 15.0;
        // endregion

        // 位置控制
        private EPosition position = EPosition.TOP_RIGHT;
        private EAnimation animation = EAnimation.RIGHT_TO_LEFT;

        // 状态管理
        private int index = 0;
        private boolean finished = false;
        private int lastIndex = 0;
        private double lastY = 0;
        private long lastRenderTime = 0;

        // 缓存字段
        private transient double cachedWidth = -1;
        private transient double cachedHeight = -1;
        private transient Text cachedText;

        private Notification(Component component) {
            this.component = component;
            this.updateCachedText();
        }

        public static Notification ofComponentWithBlack(Component component) {
            return new Notification(component.setColor(0xFF000000));
        }

        public static Notification ofComponent(Component component) {
            return new Notification(component);
        }

        /**
         * 更新缓存字段
         * 当组件内容发生变化时，需要调用此方法更新缓存字段
         */
        private void updateCachedText() {
            this.cachedText = new Text(this.getComponent());
            this.cachedWidth = AbstractGuiUtils.multilineTextWidth(this.getCachedText()) + this.getPadding() * 2;
            this.cachedHeight = AbstractGuiUtils.multilineTextHeight(this.getCachedText()) + this.getPadding() * 2;
        }

        /**
         * 渲染通知
         *
         * @param preInfo     上个通知的布局信息(y, width, height)
         * @param screenInfo  屏幕信息(width, height)
         * @param currentTime 当前时间
         */
        @OnlyIn(Dist.CLIENT)
        private void render(Coordinate preInfo, Coordinate screenInfo, long currentTime) {
            if (this.finished) return;
            if (this.startTime < 0) this.startTime = currentTime;
            if (currentTime < this.scheduledTime) return;

            // 计算动画进度
            double progress = this.calculateProgress(currentTime);
            if (progress < 0) {
                this.finished = true;
                return;
            }

            // 位置计算
            Coordinate coordinate = this.calculatePosition(screenInfo, preInfo);

            // 动画应用
            this.applyAnimationEffect(coordinate, progress);

            // 位置过渡动画处理
            this.handlePositionTransition(coordinate, currentTime);

            // 可见性检查
            if (!this.isVisible(coordinate, screenInfo)) {
                return;
            }

            // 实际渲染
            this.doRender(coordinate);

            // 更新布局上下文
            this.updateLayoutContext(coordinate, preInfo, currentTime);
        }

        /**
         * 计算动画进度
         */
        private double calculateProgress(long currentTime) {
            long elapsed = currentTime - this.getStartTime();
            long totalTime = this.getAnimationTime() * 2 + this.getDurationTime();

            if (elapsed > totalTime) return -1;

            if (elapsed < this.getAnimationTime()) {
                return (double) elapsed / this.getAnimationTime();
            } else if (elapsed < this.getAnimationTime() + this.getDurationTime()) {
                return 1.0;
            } else {
                return 1.0 - (double) (elapsed - this.getAnimationTime() - this.getDurationTime()) / this.getAnimationTime();
            }
        }

        /**
         * 计算渲染位置
         *
         * @param screenInfo 屏幕信息
         * @param preInfo    上个通知的布局信息
         * @return 当前通知的布局信息
         */
        private Coordinate calculatePosition(Coordinate screenInfo, Coordinate preInfo) {
            Coordinate info = new Coordinate();
            switch (this.getPosition()) {
                case TOP_LEFT:
                    info.x = this.getMargin();
                    info.y = this.getMargin() + preInfo.getY() + preInfo.getHeight();
                    break;
                case TOP_CENTER:
                    info.x = (screenInfo.getWidth() - this.getCachedWidth()) / 2;
                    info.y = this.getMargin() + preInfo.getY() + preInfo.getHeight();
                    break;
                case TOP_RIGHT:
                    info.x = screenInfo.getWidth() - this.getCachedWidth() - this.getMargin();
                    info.y = this.getMargin() + preInfo.getY() + preInfo.getHeight();
                    break;
                case BOTTOM_LEFT:
                    info.x = this.getMargin();
                    info.y = (preInfo.getY() == 0 ? screenInfo.getHeight() : preInfo.getY()) - this.getMargin() - this.getCachedHeight();
                    break;
                case BOTTOM_CENTER:
                    info.x = (screenInfo.getWidth() - this.getCachedWidth()) / 2;
                    info.y = (preInfo.getY() == 0 ? screenInfo.getHeight() : preInfo.getY()) - this.getMargin() - this.getCachedHeight();
                    break;
                case BOTTOM_RIGHT:
                    info.x = screenInfo.getWidth() - this.getCachedWidth() - this.getMargin();
                    info.y = (preInfo.getY() == 0 ? screenInfo.getHeight() : preInfo.getY()) - this.getMargin() - this.getCachedHeight();
                    break;
                case CENTER:
                    info.x = (screenInfo.getWidth() - this.getCachedWidth()) / 2;
                    info.y = (screenInfo.getHeight() - this.getCachedHeight()) / 2;
                    break;
                default:
                    info.x = this.getMargin();
                    info.y = this.getMargin();
            }
            return info;
        }

        /**
         * 应用动画效果
         *
         * @param coordinate 当前通知的布局信息
         * @param progress   动画进度
         */
        private void applyAnimationEffect(Coordinate coordinate, double progress) {
            switch (this.getAnimation()) {
                case RIGHT_TO_LEFT:
                    coordinate.x += this.getCachedWidth() * (1 - progress);
                    break;
                case LEFT_TO_RIGHT:
                    coordinate.x -= this.getCachedWidth() * (1 - progress);
                    break;
                case TOP_TO_BOTTOM:
                    coordinate.y -= this.getCachedHeight() * (1 - progress);
                    break;
                case BOTTOM_TO_TOP:
                    coordinate.y += this.getCachedHeight() * (1 - progress);
                    break;
                case FADE_IN:
                    // 取得背景颜色的alpha通道
                    int a = this.getBgColor() >>> 24;
                    int alpha = (int) ((a == 0 ? 0xFF : a) * progress);
                    this.setBgColor((this.getBgColor() & 0x00FFFFFF) | (alpha << 24));
                    break;
            }
        }

        /**
         * 处理位置过渡动画（使用缓动函数和速度衰减）
         *
         * @param coordinate  当前坐标信息
         * @param currentTime 当前时间戳(ms)
         */
        private void handlePositionTransition(Coordinate coordinate, long currentTime) {
            if (this.getLastIndex() > 0 && this.getIndex() == 0 && this.getLastY() != coordinate.getY()) {
                final double targetY = coordinate.getY();
                final double currentY = this.getLastY();
                final double deltaY = targetY - currentY;

                // 时间差（second）
                final double deltaTime = (currentTime - this.getLastRenderTime()) / 1000.0;
                if (deltaTime <= 0) return;

                // 使用缓动函数计算速度
                final double distance = Math.abs(deltaY);
                final double direction = Math.signum(deltaY);

                // 当前速度
                double currentSpeed = Math.min(this.getMaxSpeed(), Math.sqrt(2 * this.getAcceleration() * distance));

                // 接近目标时开始减速
                if (distance < this.getDecelerationDistance()) {
                    currentSpeed *= easeOutQuad(distance / this.getDecelerationDistance());
                }

                // 位移
                double movement = currentSpeed * deltaTime * direction;
                double newY = currentY + movement;

                // 防止过冲
                if ((direction > 0 && newY > targetY) || (direction < 0 && newY < targetY)) {
                    newY = targetY;
                }

                // 根据位置类型限制坐标
                switch (this.getPosition()) {
                    case TOP_LEFT:
                    case TOP_CENTER:
                    case TOP_RIGHT:
                        coordinate.y = Math.max(targetY, newY);
                        this.setIndex(1);
                        break;
                    case BOTTOM_LEFT:
                    case BOTTOM_CENTER:
                    case BOTTOM_RIGHT:
                        coordinate.y = Math.min(targetY, newY);
                        this.setIndex(1);
                        break;
                }

                // 到达目标后更新状态
                if (Math.abs(coordinate.getY() - targetY) < 0.1) {
                    coordinate.y = targetY;
                }
            }
        }

        /**
         * 二次缓出函数（用于减速阶段）
         *
         * @param t 标准化进度 [0,1]
         * @return 缓动系数
         */
        private double easeOutQuad(double t) {
            return 1 - (1 - t) * (1 - t);
        }

        /**
         * 判断是否可见
         *
         * @param info       当前通知的布局信息
         * @param screenInfo 屏幕信息
         * @return 是否可见
         */
        private boolean isVisible(Coordinate info, Coordinate screenInfo) {
            return info.getX() + this.getCachedWidth() > 0 &&
                    info.getX() < screenInfo.getWidth() &&
                    info.getY() + this.getCachedHeight() > 0 &&
                    info.getY() < screenInfo.getHeight();
        }

        /**
         * 执行渲染
         *
         * @param coordinate 当前通知的布局信息
         */
        private void doRender(Coordinate coordinate) {
            AbstractGuiUtils.setDepth(AbstractGuiUtils.EDepth.POPUP_TIPS);
            AbstractGuiUtils.fill(
                    (int) coordinate.getX(), (int) coordinate.getY(),
                    (int) this.getCachedWidth(), (int) this.getCachedHeight(),
                    this.getBgColor(), this.getRadius()
            );
            AbstractGuiUtils.fillOutLine(
                    (int) coordinate.getX(), (int) coordinate.getY(),
                    (int) this.getCachedWidth(), (int) this.getCachedHeight(),
                    this.getBorderSize(), this.getBorderColor(), this.getRadius()
            );
            AbstractGuiUtils.drawLimitedText(
                    cachedText,
                    coordinate.getX() + this.getPadding(),
                    coordinate.getY() + this.getPadding(),
                    0, 0,
                    AbstractGuiUtils.EllipsisPosition.MIDDLE
            );
            AbstractGuiUtils.resetDepth();
        }

        /**
         * 更新布局上下文
         *
         * @param coordinate  当前通知的布局信息
         * @param preLayout   上个通知的布局信息
         * @param currentTime 当前时间
         */
        private void updateLayoutContext(Coordinate coordinate, Coordinate preLayout, long currentTime) {
            this.setLastY(coordinate.getY());
            this.setLastIndex(this.getIndex());
            this.setLastRenderTime(currentTime);
            preLayout.setY(coordinate.getY());
            preLayout.setWidth(this.getCachedWidth());
            preLayout.setHeight(this.getCachedHeight());
        }
    }

    private final EnumMap<EPosition, Deque<Notification>> notifications = new EnumMap<>(EPosition.class);
    private static final NotificationManager instance = new NotificationManager();

    /**
     * 获取通知管理器实例
     */
    public static NotificationManager get() {
        return instance;
    }

    /**
     * 添加通知
     */
    public void addNotification(Notification notification) {
        this.notifications.computeIfAbsent(notification.getPosition(), k -> new ArrayDeque<>()).add(notification);
    }

    @OnlyIn(Dist.CLIENT)
    public void render() {
        Minecraft mc = Minecraft.getInstance();
        Coordinate screenInfo = new Coordinate()
                .setWidth(mc.getWindow().getGuiScaledWidth())
                .setHeight(mc.getWindow().getGuiScaledHeight());
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<EPosition, Deque<Notification>> entry : notifications.entrySet()) {
            EPosition pos = entry.getKey();
            Deque<Notification> queue = entry.getValue();

            // 初始化布局上下文
            Coordinate preInfo = new Coordinate().setY(pos.name().startsWith("TOP") ? 0 : screenInfo.getHeight()).setHeight(0);

            int i = 0;
            Iterator<Notification> iter = queue.iterator();
            while (iter.hasNext()) {
                Notification n = iter.next();

                // 状态过滤
                if (n.isFinished() || n.getScheduledTime() > currentTime) {
                    iter.remove();
                    continue;
                }

                // 位置预计算
                Coordinate lastInfo = n.calculatePosition(screenInfo, preInfo);

                // 是否可见
                if (this.shouldSkipRendering(pos, lastInfo, screenInfo)) {
                    break;
                }

                // 执行渲染
                n.setIndex(i++).render(preInfo, screenInfo, currentTime);

                // 更新布局上下文
                preInfo.setY(n.getLastY());
                preInfo.setWidth(n.getCachedWidth());
                preInfo.setHeight(n.getCachedHeight());
            }
        }
    }

    /**
     * 判断是否需要跳过渲染
     *
     * @param pos        位置
     * @param coordinate 布局信息
     * @param screenInfo 屏幕信息
     */
    private boolean shouldSkipRendering(EPosition pos, Coordinate coordinate, Coordinate screenInfo) {
        switch (pos) {
            case TOP_LEFT:
            case TOP_CENTER:
            case TOP_RIGHT:
                return coordinate.getY() + coordinate.getHeight() > screenInfo.getHeight();
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                return coordinate.getY() < 0;
            default:
                return false;
        }
    }
}
