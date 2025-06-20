package xin.vanilla.sakura.enums;

import lombok.Getter;

/**
 * 签到时间冷却方式
 */
@Getter
public enum EnumTimeCoolingMethod {
    FIXED_TIME(0, "固定时间"),
    FIXED_INTERVAL(1, "固定间隔"),
    MIXED(2, "混合模式");

    private final int code;
    private final String desc;

    EnumTimeCoolingMethod(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static EnumTimeCoolingMethod valueOf(int code) {
        for (EnumTimeCoolingMethod method : EnumTimeCoolingMethod.values()) {
            if (method.code == code) {
                return method;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
