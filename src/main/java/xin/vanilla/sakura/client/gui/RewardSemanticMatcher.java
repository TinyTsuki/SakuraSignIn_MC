package xin.vanilla.sakura.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.reward.Reward;

import java.util.Objects;

/**
 * 比较奖励的语义身份，忽略数量、概率等非身份字段。
 */
public final class RewardSemanticMatcher {
    private RewardSemanticMatcher() {
    }

    public static boolean matches(Reward first, Reward second) {
        if (first == null || second == null || first.getType() == null
                || first.getType() != second.getType()) {
            return false;
        }
        ERewardType type = first.getType();
        JsonObject firstContent = first.getContent();
        JsonObject secondContent = second.getContent();
        if (firstContent == null || secondContent == null) {
            return firstContent == secondContent;
        }
        switch (type) {
            case ITEM:
                return sameRequiredString(firstContent, secondContent, "item", "id")
                        && sameString(firstContent, secondContent, "nbt");
            case EFFECT:
                return sameRequiredString(firstContent, secondContent, "effect");
            case ADVANCEMENT:
                return sameRequiredString(firstContent, secondContent, "advancement");
            case COMMAND:
                return sameRequiredString(firstContent, secondContent, "command");
            case MESSAGE:
                return firstContent.equals(secondContent);
            case EXP_POINT:
            case EXP_LEVEL:
            case SIGN_IN_CARD:
                return true;
            default:
                return firstContent.equals(secondContent);
        }
    }

    private static boolean sameString(JsonObject first, JsonObject second, String... keys) {
        return Objects.equals(string(first, keys), string(second, keys));
    }

    private static boolean sameRequiredString(JsonObject first, JsonObject second,
                                              String... keys) {
        String firstValue = requiredString(first, keys);
        String secondValue = requiredString(second, keys);
        return firstValue == null || secondValue == null
                ? first.equals(second)
                : Objects.equals(firstValue, secondValue);
    }

    private static String requiredString(JsonObject content, String... keys) {
        for (String key : keys) {
            JsonElement value = content.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                try {
                    return value.getAsString();
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String string(JsonObject content, String... keys) {
        for (String key : keys) {
            JsonElement value = content.get(key);
            if (value != null && !value.isJsonNull()) {
                try {
                    return value.getAsString();
                } catch (RuntimeException ignored) {
                    return value.toString();
                }
            }
        }
        return "";
    }
}
