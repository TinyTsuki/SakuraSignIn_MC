package xin.vanilla.sakura.rewards.impl;

import com.google.gson.JsonObject;
import lombok.NonNull;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.RewardParser;
import xin.vanilla.sakura.screen.component.Text;
import xin.vanilla.sakura.util.AbstractGuiUtils;
import xin.vanilla.sakura.util.I18nUtils;

public class MessageRewardParser implements RewardParser<ITextComponent> {

    @Override
    public @NonNull ITextComponent deserialize(JsonObject json) {
        ITextComponent message;
        try {
            message = new StringTextComponent(json.get("contents").getAsString());
            JsonObject styleJson = json.getAsJsonObject("style");
            Style style = new Style();
            if (styleJson.has("color")) {
                TextFormatting color = TextFormatting.getById(styleJson.get("color").getAsInt());
                if (color == null) {
                    LOGGER.warn("Invalid color code: {}", styleJson.get("color").getAsInt());
                } else {
                    style.setColor(color);
                }
            }
            style.setUnderlined(styleJson.get("underlined").getAsBoolean());
            style.setStrikethrough(styleJson.get("strikethrough").getAsBoolean());
            style.setObfuscated(styleJson.get("obfuscated").getAsBoolean());
            // style.withFont(new ResourceLocation(styleJson.get("font").getAsString()));
            message.setStyle(style);
        } catch (Exception e) {
            LOGGER.error("Failed to parse message reward", e);
            message = AbstractGuiUtils.textToComponent(Text.literal("Failed to parse message reward").setColor(0xFFFF0000));
        }
        return message;
    }

    @Override
    public JsonObject serialize(ITextComponent reward) {
        JsonObject result = new JsonObject();
        JsonObject styleJson = new JsonObject();
        Style style = reward.getStyle();
        if (style.getColor() != null) {
            styleJson.addProperty("color", style.getColor().getColor());
        }
        styleJson.addProperty("bold", style.isBold());
        styleJson.addProperty("italic", style.isItalic());
        styleJson.addProperty("underlined", style.isUnderlined());
        styleJson.addProperty("strikethrough", style.isStrikethrough());
        styleJson.addProperty("obfuscated", style.isObfuscated());
        // styleJson.addProperty("font", style.getFont().toString());
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
