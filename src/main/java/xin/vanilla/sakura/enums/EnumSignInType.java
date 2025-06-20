package xin.vanilla.sakura.enums;

import lombok.Getter;

@Getter
@SuppressWarnings("unused")
public enum EnumSignInType {
    RE_SIGN_IN(0, "补签"),
    SIGN_IN(1, "签到"),
    REWARD(2, "奖励");

    private final int code;
    private final String name;

    EnumSignInType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static EnumSignInType valueOf(int code) {
        for (EnumSignInType status : EnumSignInType.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
