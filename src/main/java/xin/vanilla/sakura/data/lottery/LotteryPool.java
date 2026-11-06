package xin.vanilla.sakura.data.lottery;

import lombok.Data;
import lombok.NoArgsConstructor;
import xin.vanilla.sakura.reward.RewardList;

/**
 * 服务端权威抽奖池，奖励概率在此处作为相对权重。
 */
@Data
@NoArgsConstructor
public class LotteryPool {
    private String id = "";
    private String displayName = "";
    private String description = "";
    private String voucherExpression = "";
    private LotteryLimitPolicy limitPolicy = LotteryLimitPolicy.DAILY;
    private int maxDraws = 1;
    private int cooldownSeconds;
    private LotteryPreviewMode previewMode = LotteryPreviewMode.ALL;
    private LotteryAnimationStyle animationStyle = LotteryAnimationStyle.STRIP;
    @lombok.EqualsAndHashCode.Exclude
    private boolean singleDraw = true;
    @lombok.EqualsAndHashCode.Exclude
    private int batchSize;
    @lombok.EqualsAndHashCode.Exclude
    private java.util.List<Integer> drawCounts;
    private String startDate = "";
    private String endDate = "";
    private String thumbnailPng = "";
    private String posterPng = "";
    private String artworkItem = "";
    private String artworkTexture = "";
    private RewardList rewards = new RewardList();

    public LotteryPool(String id, String displayName, LotteryLimitPolicy limitPolicy,
                       int maxDraws, int cooldownSeconds, RewardList rewards) {
        this.id = id == null ? "" : id;
        this.displayName = displayName == null ? "" : displayName;
        this.limitPolicy = limitPolicy == null ? LotteryLimitPolicy.DAILY : limitPolicy;
        this.maxDraws = maxDraws;
        this.cooldownSeconds = cooldownSeconds;
        this.rewards = rewards == null ? new RewardList() : rewards;
    }

    public LotteryPool(String id, String displayName, LotteryLimitPolicy limitPolicy,
                       int maxDraws, int cooldownSeconds, LotteryPreviewMode previewMode,
                       RewardList rewards) {
        this(id, displayName, limitPolicy, maxDraws, cooldownSeconds, rewards);
        this.previewMode = previewMode == null ? LotteryPreviewMode.ALL : previewMode;
    }

    @lombok.EqualsAndHashCode.Include
    private java.util.List<Integer> effectiveCounts() {
        return getDrawCounts();
    }

    public java.util.List<Integer> getDrawCounts() {
        if (drawCounts != null) return drawCounts;
        if (singleDraw) return batchSize > 1 ? java.util.List.of(1, batchSize) : java.util.List.of(1);
        return batchSize > 1 ? java.util.List.of(batchSize) : java.util.List.of();
    }

    public void setDrawCounts(java.util.List<Integer> counts) {
        drawCounts = java.util.List.copyOf(counts);
        singleDraw = drawCounts.contains(1);
        batchSize = drawCounts.stream().mapToInt(Integer::intValue).filter(count -> count > 1).max().orElse(0);
    }

    public boolean isSingleDraw() {
        return getDrawCounts().contains(1);
    }

    public int getBatchSize() {
        return drawCounts == null ? batchSize : drawCounts.stream().mapToInt(Integer::intValue)
                .filter(count -> count > 1).max().orElse(0);
    }

    public void setSingleDraw(boolean allowed) {
        drawCounts = null;
        singleDraw = allowed;
    }

    public void setBatchSize(int count) {
        drawCounts = null;
        batchSize = count;
    }

    public boolean allowsCount(int count) {
        return getDrawCounts().contains(count);
    }

    public int defaultDrawCount() {
        return getDrawCounts().stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    public boolean isOpen(java.time.LocalDate day) {
        return (startDate.isEmpty() || !day.isBefore(java.time.LocalDate.parse(startDate)))
                && (endDate.isEmpty() || !day.isAfter(java.time.LocalDate.parse(endDate)));
    }
}
