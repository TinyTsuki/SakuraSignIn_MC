package xin.vanilla.sakura.enums;

import lombok.Getter;

@Getter
@SuppressWarnings("unused")
public enum ESignInType {
    RE_SIGN_IN(0, "补签"),
    SIGN_IN(1, "签到"),
    REWARD(2, "奖励");

    private final int code;
    private final String name;

    ESignInType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ESignInType valueOf(int code) {
        for (ESignInType status : ESignInType.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
