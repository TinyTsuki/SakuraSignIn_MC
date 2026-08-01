package xin.vanilla.sakura.enums;

import lombok.Getter;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

/**
 * 签到时间冷却方式
 */
@Getter
public enum ETimeCoolingMethod implements IEnumDescribable {
    FIXED_TIME(0, "固定时间"),
    FIXED_INTERVAL(1, "固定间隔"),
    MIXED(2, "混合模式");

    private final int code;
    private final String name;

    ETimeCoolingMethod(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
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
