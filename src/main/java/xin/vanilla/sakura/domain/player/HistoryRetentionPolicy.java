package xin.vanilla.sakura.domain.player;

/**
 * 过期月度签到详情的处理方式。
 */
public enum HistoryRetentionPolicy {
    STRIP_REWARD_DETAILS,
    DELETE_MONTH_FILE
}
