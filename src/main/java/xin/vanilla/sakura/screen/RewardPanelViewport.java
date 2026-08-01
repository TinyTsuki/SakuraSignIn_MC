package xin.vanilla.sakura.screen;

/**
 * 奖励面板的自由平移边界，保留一屏余量便于手动调整位置。
 */
final class RewardPanelViewport {
    private RewardPanelViewport() {
    }

    static double clampOffset(double offset, double contentHeight, double viewportHeight) {
        double safeContentHeight = Math.max(0, contentHeight);
        double safeViewportHeight = Math.max(0, viewportHeight);
        double minOffset = -(safeContentHeight + safeViewportHeight);
        return Math.max(minOffset, Math.min(offset, safeViewportHeight));
    }
}
