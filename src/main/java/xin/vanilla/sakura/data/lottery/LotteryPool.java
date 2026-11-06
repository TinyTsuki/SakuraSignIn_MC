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
    private String id = "";
    private String displayName = "";
    private String description = "";
    private String voucherExpression = "";
    private LotteryLimitPolicy limitPolicy = LotteryLimitPolicy.DAILY;
    private int maxDraws = 1;
    private int cooldownSeconds;
    private LotteryPreviewMode previewMode = LotteryPreviewMode.ALL;
    private LotteryAnimationStyle animationStyle;
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

    public void setDrawCounts(List<Integer> counts) {
        drawCounts = counts == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(counts));
    }

    public List<Integer> getDrawCounts() {
        return drawCounts == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(drawCounts));
    }

    public boolean allowsCount(int count) {
        return drawCounts == null ? count == -1 || count >= 1 && count <= 100 : drawCounts.contains(count);
    }
}
