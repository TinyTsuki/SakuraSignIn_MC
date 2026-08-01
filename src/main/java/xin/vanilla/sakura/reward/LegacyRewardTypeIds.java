package xin.vanilla.sakura.reward;

import com.google.gson.JsonParseException;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;

import java.util.Locale;

/** 旧奖励枚举名称到稳定类型 ID 的唯一迁移入口。 */
public final class LegacyRewardTypeIds {
    private LegacyRewardTypeIds() {
    }

    public static RewardTypeId resolve(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.indexOf(':') > 0) {
            return RewardTypeId.parse(normalized);
        }
        switch (normalized.toUpperCase(Locale.ROOT)) {
            case "ITEM":
                return SakuraRewardTypes.ITEM;
            case "EFFECT":
                return SakuraRewardTypes.EFFECT;
            case "EXP_POINT":
                return SakuraRewardTypes.EXPERIENCE_POINT;
            case "EXP_LEVEL":
                return SakuraRewardTypes.EXPERIENCE_LEVEL;
            case "SIGN_IN_CARD":
                return SakuraRewardTypes.SIGN_IN_CARD;
            case "ADVANCEMENT":
                return SakuraRewardTypes.ADVANCEMENT;
            case "MESSAGE":
                return SakuraRewardTypes.MESSAGE;
            case "COMMAND":
                return SakuraRewardTypes.COMMAND;
            default:
                throw new JsonParseException("Unknown legacy reward type: " + value);
        }
    }
}
