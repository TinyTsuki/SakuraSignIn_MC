package xin.vanilla.sakura.enums;

import lombok.Getter;

import java.io.Serializable;

/**
 * 奖励类型
 */
@Getter
public enum EnumRewardType implements Serializable {
    NONE(0),
    ITEM(1),
    EFFECT(2),
    EXP_POINT(3),
    EXP_LEVEL(4),
    SIGN_IN_CARD(5),
    ADVANCEMENT(6),
    MESSAGE(7),
    COMMAND(8);

    private final int code;

    EnumRewardType(int code) {
        this.code = code;
    }

    public static EnumRewardType valueOf(int code) {
        for (EnumRewardType type : EnumRewardType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
