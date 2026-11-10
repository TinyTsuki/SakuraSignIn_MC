package xin.vanilla.sakura;

import org.junit.Test;
import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumI18nType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static org.junit.Assert.*;

public class NativeMessageBoundaryTest {


    @Test
    public void composingNamesPreservesVanillaTranslationsAndTheOriginal() {
        net.minecraft.network.chat.Component entity = net.minecraft.network.chat.Component.translatable("entity.minecraft.pig");
        net.minecraft.network.chat.Component item = net.minecraft.network.chat.Component.translatable("item.minecraft.diamond");
        String before = BaniraComponent.get().object(entity).toJson().toString();
        net.minecraft.network.chat.Component message = BaniraComponent.get().literal("%s %s")
                .appendArg(entity).appendArg(item.copy()).toChat("zh_cn");
        JsonElement json = BaniraComponent.get().object(message).toJson().get("original");
        assertEquals(Arrays.asList("entity.minecraft.pig", "item.minecraft.diamond"), keys(json));
        assertEquals(before, BaniraComponent.get().object(entity).toJson().toString());
    }

    @Test
    public void serverModTemplateDoesNotRequireTheReceivingMod() {
        Component message = BaniraComponent.get().trans(EnumI18nType.FORMAT, "missing_boundary_template",
                        net.minecraft.network.chat.Component.translatable("item.minecraft.diamond"))
                .translationFallback("Name: %s").languageCode("zh_cn");
        net.minecraft.network.chat.Component prepared = message.toChat("zh_cn");
        assertTrue(prepared.getString().startsWith("Name: "));
        JsonElement json = BaniraComponent.get().object(prepared).toJson().get("original");
        assertEquals(Arrays.asList("item.minecraft.diamond"), keys(json));
        assertFalse(json.toString().contains("missing_boundary_template"));
    }


    @Test
    public void reviewedNativeTemplateFormattingComesFromTheFrozenCommonInput() throws Exception {
        Class<?> translations = Class.forName("xin.vanilla.banira.common.data.VanillaComponentTranslations",
                false, Component.class.getClassLoader());
        java.lang.reflect.Method format = translations.getDeclaredMethod("noArgumentTemplate", String.class);
        format.setAccessible(true);
        assertEquals("100%", format.invoke(null, "100%%"));
        assertEquals("%% %s", format.invoke(null, "%% %s"));
    }

    private static List<String> keys(JsonElement value) {
        List<String> keys = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) keys.addAll(keys(child));
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("translate")) keys.add(object.get("translate").getAsString());
            for (String field : Arrays.asList("extra", "with", "separator"))
                if (object.has(field)) keys.addAll(keys(object.get(field)));
        }
        return keys;
    }
}
