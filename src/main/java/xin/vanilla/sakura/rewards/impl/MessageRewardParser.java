package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.NonNull;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import xin.vanilla.banira.common.data.Color;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.text.SakuraComponent;

import java.util.Locale;

public class MessageRewardParser implements RewardParser<Component> {

    @Override
    public @NonNull Component deserialize(JsonObject json) {
        if (json.has("modId")) {
            return SakuraComponent.get().deserialize(json);
        }
        return deserializeLegacy(json);
    }

    @Override
    public JsonObject serialize(Component reward) {
        return reward.toJson();
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json) {
        return getDisplayName(languageCode, json, false);
    }

    @Override
    public @NonNull Component getDisplayName(String languageCode, JsonObject json, boolean withNum) {
        return SakuraComponent.get().transLang(
                languageCode, "word", "reward_type_" + ERewardType.MESSAGE.getCode());
    }

    /**
     * 旧奖励文件中的分类枚举不属于 Banira 模型，读取时转换为完整翻译键。
     */
    private static Component deserializeLegacy(JsonObject json) {
        String type = string(json, "i18nType", "PLAIN").toUpperCase(Locale.ROOT);
        String text = string(json, "text", "");
        Component result;
        if ("PLAIN".equals(type) || "ORIGINAL".equals(type)) {
            result = SakuraComponent.get().literal(text);
        } else {
            result = SakuraComponent.get().trans("NONE", SakuraComponent.key(type, text));
        }

        if (json.has("languageCode")) {
            result.languageCode(string(json, "languageCode", null));
        }
        if (json.has("color")) {
            result.color(Color.argb(json.get("color").getAsInt()));
        }
        if (json.has("bgColor")) {
            result.bgColor(Color.argb(json.get("bgColor").getAsInt()));
        }
        result.shadow(bool(json, "shadow"))
                .bold(bool(json, "bold"))
                .italic(bool(json, "italic"))
                .underlined(bool(json, "underlined"))
                .strikethrough(bool(json, "strikethrough"))
                .obfuscated(bool(json, "obfuscated"));

        if (json.has("clickEvent.action") && json.has("clickEvent.value")) {
            result.clickEvent(new ClickEvent(
                    clickAction(json.get("clickEvent.action").getAsString()),
                    json.get("clickEvent.value").getAsString()));
        }
        if (json.has("hoverEvent")) {
            result.hoverEvent(HoverEvent.deserialize(json.getAsJsonObject("hoverEvent")));
        }
        for (JsonElement child : array(json, "children")) {
            result.getChildren().add(deserializeLegacy(child.getAsJsonObject()));
        }
        for (JsonElement arg : array(json, "args")) {
            result.getArgs().add(deserializeLegacy(arg.getAsJsonObject()));
        }
        return result;
    }

    private static ClickEvent.Action clickAction(String name) {
        for (ClickEvent.Action action : ClickEvent.Action.values()) {
            if (action.name().equalsIgnoreCase(name) || action.getName().equalsIgnoreCase(name)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown click action: " + name);
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject json, String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }

    private static JsonArray array(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
    }
}
