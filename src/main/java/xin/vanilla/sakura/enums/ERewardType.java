package xin.vanilla.sakura.enums;

import lombok.Getter;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;

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

    public RewardTypeId rewardTypeId() {
        switch (this) {
            case ITEM:
                return SakuraRewardTypes.ITEM;
            case EFFECT:
                return SakuraRewardTypes.EFFECT;
            case EXP_POINT:
                return SakuraRewardTypes.EXPERIENCE_POINT;
            case EXP_LEVEL:
                return SakuraRewardTypes.EXPERIENCE_LEVEL;
            case SIGN_IN_CARD:
                return SakuraRewardTypes.SIGN_IN_CARD;
            case ADVANCEMENT:
                return SakuraRewardTypes.ADVANCEMENT;
            case MESSAGE:
                return SakuraRewardTypes.MESSAGE;
            case COMMAND:
                return SakuraRewardTypes.COMMAND;
            default:
                throw new IllegalStateException("Unsupported reward type: " + this);
        }
    }
}
