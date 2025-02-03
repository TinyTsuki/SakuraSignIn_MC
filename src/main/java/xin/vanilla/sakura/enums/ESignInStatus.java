package xin.vanilla.sakura.enums;

import lombok.Getter;

@Getter
@SuppressWarnings("unused")
public enum ESignInStatus {
    NO_ACTION(-2, "不可操作"),
    CAN_REPAIR(-1, "可补签"),
    NOT_SIGNED_IN(0, "未签到"),
    SIGNED_IN(1, "已签到"),
    REWARDED(2, "已领取");

    private final int code;
    private final String description;

    ESignInStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ESignInStatus valueOf(int code) {
        for (ESignInStatus status : ESignInStatus.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
