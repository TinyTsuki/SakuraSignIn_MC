package xin.vanilla.sakura.enums;

import lombok.Getter;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

@Getter
@SuppressWarnings("unused")
public enum ESignInType implements IEnumDescribable {
    RE_SIGN_IN(0, "补签"),
    SIGN_IN(1, "签到"),
    REWARD(2, "奖励");

    private final int code;
    private final String name;

    ESignInType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
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
