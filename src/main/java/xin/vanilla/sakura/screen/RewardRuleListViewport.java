package xin.vanilla.sakura.screen;

/** 左侧规则列表使用普通内容边界，不允许空白区域过度滚动。 */
final class RewardRuleListViewport {
    private RewardRuleListViewport() {
    }

    static double clampOffset(double offset, double contentHeight, double viewportHeight) {
        double overflow = Math.max(0, contentHeight - Math.max(0, viewportHeight));
        return Math.max(-overflow, Math.min(0, offset));
    }
}
