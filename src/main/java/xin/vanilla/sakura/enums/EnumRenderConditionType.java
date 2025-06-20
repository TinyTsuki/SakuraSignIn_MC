package xin.vanilla.sakura.enums;

/**
 * 渲染条件类型
 */
public enum EnumRenderConditionType {
    /**
     * 时间
     */
    TIME,
    /**
     * 时间范围
     */
    TIME_RANGE,
    /**
     * 日期时间
     */
    DATETIME,
    /**
     * 日期时间范围
     */
    DATETIME_RANGE,
    /**
     * 连续签到天数
     */
    CONTINUOUS_SIGN_IN_DAYS,
    /**
     * 连续签到天数范围
     */
    CONTINUOUS_SIGN_IN_DAYS_RANGE,
    /**
     * 累计签到天数
     */
    TOTAL_SIGN_IN_DAYS,
    /**
     * 累计签到天数范围
     */
    TOTAL_SIGN_IN_DAYS_RANGE,
    /**
     * 界面打开时长
     */
    SCREEN_OPENING_DURATION,
    /**
     * 界面打开时长范围
     */
    SCREEN_OPENING_DURATION_RANGE,
    /**
     * 存在自定义参数值
     */
    CONTAINS_PARAM,

}
