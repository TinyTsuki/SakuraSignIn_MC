package xin.vanilla.sakura.enums;

import lombok.Getter;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

@Getter
@SuppressWarnings("unused")
public enum ESignInStatus implements IEnumDescribable {
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

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
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
