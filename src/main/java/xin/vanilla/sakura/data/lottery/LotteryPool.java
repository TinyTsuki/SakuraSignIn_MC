package xin.vanilla.sakura.data.lottery;

import lombok.Data;
import lombok.NoArgsConstructor;
import xin.vanilla.sakura.reward.RewardList;

import java.util.List;

/**
 * 服务端权威抽奖池，奖励概率在此处作为相对权重。
 */
@Data
@NoArgsConstructor
public class LotteryPool {
    public static final LotteryAnimationStyle DEFAULT_ANIMATION_STYLE = LotteryAnimationStyle.STRIP;
    private String id = "";
    private String displayName = "";
    private String description = "";
    private String voucherExpression = "";
    private LotteryLimitPolicy limitPolicy = LotteryLimitPolicy.DAILY;
    private int maxDraws = 1;
    private int cooldownSeconds;
    private LotteryPreviewMode previewMode = LotteryPreviewMode.ALL;
    private LotteryAnimationStyle animationStyle = DEFAULT_ANIMATION_STYLE;
    @lombok.EqualsAndHashCode.Exclude
    private boolean singleDraw = true;
    @lombok.EqualsAndHashCode.Exclude
    private int batchSize;
    @lombok.EqualsAndHashCode.Exclude
    private List<Integer> drawCounts;
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
    private List<Integer> effectiveCounts() {
        return getDrawCounts();
    }

    public List<Integer> getDrawCounts() {
        if (drawCounts != null) return List.copyOf(drawCounts);
        if (singleDraw) return batchSize > 1 ? List.of(1, batchSize) : List.of(1);
        return batchSize > 1 ? List.of(batchSize) : List.of();
    }

    public void setDrawCounts(List<Integer> counts) {
        drawCounts = List.copyOf(counts);
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

    public LotteryAnimationStyle getAnimationStyle() {
        return animationStyle == null ? DEFAULT_ANIMATION_STYLE : animationStyle;
    }

    public void setAnimationStyle(LotteryAnimationStyle style) {
        animationStyle = style == null ? DEFAULT_ANIMATION_STYLE : style;
    }
}
