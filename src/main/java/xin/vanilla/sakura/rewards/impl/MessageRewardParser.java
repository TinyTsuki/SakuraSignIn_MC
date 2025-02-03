package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Color;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.I18nUtils;

public class MessageRewardParser implements RewardParser<IFormattableTextComponent> {

    @Override
    public @NonNull IFormattableTextComponent deserialize(JsonObject json) {
        IFormattableTextComponent message;
        try {
            message = new StringTextComponent(json.get("contents").getAsString());
            JsonObject styleJson = json.getAsJsonObject("style");
            Style style = Style.EMPTY;
            if (styleJson.has("color"))
                style.withColor(Color.fromRgb(styleJson.get("color").getAsInt()));
            style.withBold(styleJson.get("bold").getAsBoolean());
            style.withItalic(styleJson.get("italic").getAsBoolean());
            style.setUnderlined(styleJson.get("underlined").getAsBoolean());
            style.setStrikethrough(styleJson.get("strikethrough").getAsBoolean());
            style.setObfuscated(styleJson.get("obfuscated").getAsBoolean());
            style.withFont(new ResourceLocation(styleJson.get("font").getAsString()));
            message.setStyle(style);
        } catch (Exception e) {
            LOGGER.error("Failed to parse message reward", e);
            message = AbstractGuiUtils.textToComponent(Text.literal("Failed to parse message reward").setColor(0xFFFF0000));
        }
        return message;
    }

    @Override
    public JsonObject serialize(IFormattableTextComponent reward) {
        JsonObject result = new JsonObject();
        JsonObject styleJson = new JsonObject();
        Style style = reward.getStyle();
        if (style.getColor() != null) {
            styleJson.addProperty("color", style.getColor().getValue());
        }
        styleJson.addProperty("bold", style.isBold());
        styleJson.addProperty("italic", style.isItalic());
        styleJson.addProperty("underlined", style.isUnderlined());
        styleJson.addProperty("strikethrough", style.isStrikethrough());
        styleJson.addProperty("obfuscated", style.isObfuscated());
        styleJson.addProperty("font", style.getFont().toString());
        result.addProperty("contents", reward.getContents());
        result.add("style", styleJson);
        return result;
    }

    @Override
    public @NonNull String getDisplayName(JsonObject json) {
        return getDisplayName(json, false);
    }

    @Override
    public @NonNull String getDisplayName(JsonObject json, boolean withNum) {
        return I18nUtils.get(String.format("reward.sakura_sign_in.reward_type_%s", ERewardType.MESSAGE.getCode()));
    }
}
