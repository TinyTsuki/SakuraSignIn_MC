package xin.vanilla.sakura.enums;

import lombok.Getter;

/**
 * 签到时间冷却方式
 */
@Getter
public enum ETimeCoolingMethod {
    FIXED_TIME(0, "固定时间"),
    FIXED_INTERVAL(1, "固定间隔"),
    MIXED(2, "混合模式");

    private final int code;
    private final String name;

    ETimeCoolingMethod(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ETimeCoolingMethod valueOf(int code) {
        for (ETimeCoolingMethod method : ETimeCoolingMethod.values()) {
            if (method.code == code) {
                return method;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
