package xin.vanilla.sakura.enums;

import lombok.Getter;

import java.io.Serializable;

/**
 * 奖励类型
 */
@Getter
public enum ERewardType implements Serializable {
    ITEM(1),
    EFFECT(2),
    EXP_POINT(3),
    EXP_LEVEL(4),
    SIGN_IN_CARD(5),
    ADVANCEMENT(6),
    MESSAGE(7),
    COMMAND(8);

    private final int code;

    ERewardType(int code) {
        this.code = code;
    }

    public static ERewardType valueOf(int code) {
        for (ERewardType type : ERewardType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
