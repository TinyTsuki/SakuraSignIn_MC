package xin.vanilla.sakura.rewards;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.sakura.rewards.impl.MessageRewardParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 锁定旧消息奖励向 Banira 文本模型的无损迁移。
 */
public class MessageRewardParserMigrationTest {

    @Test
    public void legacyCategorizedComponentIsConvertedRecursively() {
        JsonObject legacy = legacyComponent("legacy_notice", "MESSAGE");
        legacy.addProperty("languageCode", "zh_cn");
        legacy.addProperty("color", 0xFF336699);
        legacy.addProperty("bold", true);

        JsonArray args = new JsonArray();
        args.add(legacyComponent("enabled", "WORD"));
        legacy.add("args", args);

        JsonArray children = new JsonArray();
        children.add(legacyComponent(" tail", "PLAIN"));
        legacy.add("children", children);

        Object decoded = new MessageRewardParser().deserialize(legacy);

        assertTrue(decoded instanceof Component);
        Component component = (Component) decoded;
        assertEquals(EnumI18nType.NONE, component.i18nType());
        assertEquals("message.sakura_sign_in.legacy_notice", component.text());
        assertEquals("sakura_sign_in", component.modId());
        assertEquals("zh_cn", component.languageCodeOrDefault());
        assertEquals(0xFF336699, component.color().argb());
        assertTrue(component.bold());
        assertEquals("word.sakura_sign_in.enabled", component.getArgs().get(0).text());
        assertEquals(" tail", component.getChildren().get(0).text());
        assertEquals(EnumI18nType.PLAIN, component.getChildren().get(0).i18nType());
    }

    private static JsonObject legacyComponent(String text, String i18nType) {
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        json.addProperty("i18nType", i18nType);
        json.addProperty("languageCode", "en_us");
        json.addProperty("color", 0xFFFFFFFF);
        json.addProperty("bgColor", 0x00000000);
        json.addProperty("shadow", false);
        json.addProperty("bold", false);
        json.addProperty("italic", false);
        json.addProperty("underlined", false);
        json.addProperty("strikethrough", false);
        json.addProperty("obfuscated", false);
        json.add("children", new JsonArray());
        json.add("args", new JsonArray());
        return json;
    }
}
