package xin.vanilla.sakura.enums;

import lombok.Getter;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;

import java.io.Serializable;

/**
 * 奖励类型
 */
@Getter
public enum ERewardType implements Serializable, IEnumDescribable {
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

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(SakuraComponent.get(), this);
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
