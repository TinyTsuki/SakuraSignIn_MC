package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import xin.vanilla.banira.common.data.Color;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

import java.util.Locale;

public final class MessageRewardCodec implements RewardCodec<Component> {
    @Override
    public Component decode(JsonObject content) throws RewardDataException {
        if (content == null || content.size() == 0) {
            throw new RewardDataException("Message component cannot be empty");
        }
        try {
            return content.has("modId") ? SakuraComponent.get().deserialize(content) : decodeLegacy(content);
        } catch (Exception exception) {
            throw new RewardDataException("Invalid message reward", exception);
        }
    }

    @Override
    public JsonObject encode(Component value) throws RewardDataException {
        if (value == null) {
            throw new RewardDataException("Message component cannot be null");
        }
        return value.toJson();
    }

    private static Component decodeLegacy(JsonObject json) {
        String type = string(json, "i18nType", "PLAIN").toUpperCase(Locale.ROOT);
        String text = string(json, "text", "");
        Component result = "PLAIN".equals(type) || "ORIGINAL".equals(type)
                ? SakuraComponent.get().literal(text)
                : SakuraComponent.get().trans("NONE", SakuraComponent.key(type, text));
        if (json.has("languageCode")) result.languageCode(string(json, "languageCode", null));
        if (json.has("color")) result.color(Color.argb(json.get("color").getAsInt()));
        if (json.has("bgColor")) result.bgColor(Color.argb(json.get("bgColor").getAsInt()));
        result.shadow(bool(json, "shadow")).bold(bool(json, "bold")).italic(bool(json, "italic"))
                .underlined(bool(json, "underlined")).strikethrough(bool(json, "strikethrough"))
                .obfuscated(bool(json, "obfuscated"));
        if (json.has("clickEvent.action") && json.has("clickEvent.value")) {
            result.clickEvent(new ClickEvent(clickAction(json.get("clickEvent.action").getAsString()),
                    json.get("clickEvent.value").getAsString()));
        }
        if (json.has("hoverEvent")) result.hoverEvent(HoverEvent.deserialize(json.getAsJsonObject("hoverEvent")));
        for (JsonElement child : array(json, "children")) result.getChildren().add(decodeLegacy(child.getAsJsonObject()));
        for (JsonElement arg : array(json, "args")) result.getArgs().add(decodeLegacy(arg.getAsJsonObject()));
        return result;
    }

    private static ClickEvent.Action clickAction(String name) {
        for (ClickEvent.Action action : ClickEvent.Action.values()) {
            if (action.name().equalsIgnoreCase(name) || action.getName().equalsIgnoreCase(name)) return action;
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
