package xin.vanilla.sakura.reward.script;

import com.google.gson.*;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.script.*;

import java.util.*;

import static org.junit.Assert.*;

public class ScriptRewardFormTest {
    @Test
    public void scriptBadgeReadsRewardAmountInsteadOfIconStackSize() {
        ScriptRewardDescriptor type = descriptor(json("{\"amount\":100}"));
        assertEquals("1250000", type.amount(json("{\"amount\":1250000}")));
        assertEquals("9223372036854775807", type.amount(json("{\"amount\":9223372036854775807}")));
        assertEquals("", type.amount(json("{\"amount\":\"wrong\"}")));
        assertEquals("", type.withAmountField("").amount(json("{\"amount\":100}")));
        ScriptRewardDescriptor custom = descriptor(json("{\"coins\":5}")).withAmountField("coins");
        assertEquals("17", custom.withName("Gold").amount(json("{\"coins\":17}")));
        assertEquals("coins", custom.withName("Gold").amountField());
        assertThrows(IllegalArgumentException.class, () -> custom.withAmountField("missing"));
    }

    private ScriptRewardDescriptor descriptor(JsonObject defaults, ScriptRewardField... fields) {
        return new ScriptRewardDescriptor(RewardTypeId.parse("test:coins"), "Coins", "minecraft:gold_nugget",
                defaults, Arrays.asList(fields));
    }

    private JsonObject json(String value) {
        return new JsonParser().parse(value).getAsJsonObject();
    }

    @Test
    public void legacyDefaultsBecomeTypedFieldsWithoutRequiringJson() throws Exception {
        ScriptRewardForm form = new ScriptRewardForm(descriptor(json("{\"amount\":100,\"name\":\"Coins\",\"enabled\":true,\"ratio\":0.5}")), null);
        assertEquals(Arrays.asList(ScriptRewardField.Type.INTEGER, ScriptRewardField.Type.TEXT,
                        ScriptRewardField.Type.BOOLEAN, ScriptRewardField.Type.DECIMAL),
                Arrays.asList(form.fields().get(0).type(), form.fields().get(1).type(), form.fields().get(2).type(), form.fields().get(3).type()));
        Map<String, String> values = new HashMap<>();
        values.put("amount", "250");
        values.put("name", "Gold");
        values.put("enabled", "false");
        values.put("ratio", "0.75");
        JsonObject saved = form.submit(values::get);
        assertEquals(json("{\"amount\":250,\"name\":\"Gold\",\"enabled\":false,\"ratio\":0.75}"), saved);
        assertEquals("100", form.value(form.fields().get(0)));
    }

    @Test
    public void editsPreserveUnknownDataAndReopenSavedAmount() throws Exception {
        ScriptRewardDescriptor descriptor = descriptor(json("{\"amount\":100}"),
                ScriptRewardField.integer("amount", "Coins", 1, Long.MAX_VALUE));
        JsonObject existing = json("{\"amount\":27,\"legacy\":{\"tag\":\"keep\"}}");
        ScriptRewardForm form = new ScriptRewardForm(descriptor, existing);
        existing.addProperty("amount", 999);
        assertEquals("27", form.value(form.fields().get(0)));
        JsonObject saved = form.submit(key -> "41");
        assertEquals(json("{\"amount\":41,\"legacy\":{\"tag\":\"keep\"}}"), saved);
        ScriptRewardForm reopened = new ScriptRewardForm(descriptor, saved);
        assertEquals("41", reopened.value(reopened.fields().get(0)));
    }

    @Test
    public void integerLimitsAreExactAndInvalidEditsDoNotMutateDraft() throws Exception {
        ScriptRewardField field = ScriptRewardField.integer("amount", "Coins", 1, Long.MAX_VALUE);
        ScriptRewardForm form = new ScriptRewardForm(descriptor(json("{\"amount\":100}"), field), null);
        assertEquals(Long.MAX_VALUE, form.submit(key -> "9223372036854775807").get("amount").getAsLong());
        for (String value : Arrays.asList("0", "-1", "0.5", "9223372036854775808", "", "NaN", "1e1000000"))
            assertThrows(IllegalArgumentException.class, () -> form.submit(key -> value));
        assertEquals("100", form.value(field));
    }

    @Test
    public void defaultsAreAddedWithoutDroppingNestedValues() throws Exception {
        ScriptRewardForm form = new ScriptRewardForm(descriptor(json("{\"amount\":100,\"extra\":{\"enabled\":true}}")), json("{\"amount\":5}"));
        assertEquals(ScriptRewardField.Type.JSON, form.fields().get(1).type());
        assertEquals(json("{\"amount\":6,\"extra\":{\"enabled\":false}}"),
                form.submit(key -> key.equals("amount") ? "6" : "{\"enabled\":false}"));
    }

    @Test
    public void fieldMetadataRoundTripsAndRejectsInvalidDeclarations() {
        ScriptRewardField field = ScriptRewardField.decimal("amount", "Balance", "0.01", "1000");
        ScriptRewardField copy = ScriptRewardField.fromJson(field.toJson());
        assertEquals(field.toJson(), copy.toJson());
        assertEquals(new JsonPrimitive(new java.math.BigDecimal("0.25")), copy.parse("0.25"));
        assertThrows(IllegalArgumentException.class, () -> copy.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> descriptor(json("{\"amount\":2}"), field, field));
        assertThrows(IllegalArgumentException.class, () -> descriptor(new JsonObject(), field));
        assertThrows(IllegalArgumentException.class, () -> descriptor(json("{\"amount\":\"2\"}"), field));
        assertThrows(IllegalArgumentException.class, () -> ScriptRewardField.integer("x", "X", 10, 1));
        assertThrows(IllegalArgumentException.class, () -> ScriptRewardField.bool("x", "X").parse("yes"));
    }

    @Test
    public void largeLegacyObjectsKeepJsonFallbackAndTextRemainsLiteral() {
        JsonObject defaults = new JsonObject();
        for (int i = 0; i < 33; i++) defaults.addProperty("key" + i, "text");
        assertTrue(descriptor(defaults).fields().isEmpty());
        List<ScriptRewardField> fields = new ArrayList<>();
        for (int i = 0; i < 33; i++) fields.add(ScriptRewardField.text("key" + i, "Label"));
        assertThrows(IllegalArgumentException.class, () -> descriptor(defaults, fields.toArray(new ScriptRewardField[0])));
        ScriptRewardField field = ScriptRewardField.text("label", "Label");
        assertEquals("{not JSON}", field.parse("{not JSON}").getAsString());
        assertEquals("", field.parse("").getAsString());
    }

    @Test
    public void numericNotationIsNormalizedWithoutUnboundedExpansion() throws Exception {
        ScriptRewardForm form = new ScriptRewardForm(descriptor(json("{\"amount\":100.0}")), null);
        assertEquals("100", form.value(form.fields().get(0)));
        assertEquals(100, form.submit(key -> form.value(form.fields().get(0))).get("amount").getAsInt());
        ScriptRewardDescriptor huge = descriptor(json("{\"amount\":1e999999999}"));
        assertEquals(ScriptRewardField.Type.JSON, huge.fields().get(0).type());
        assertEquals("1e999999999", ScriptRewardField.integer("amount", "Amount", 1, Long.MAX_VALUE)
                .format(json("{\"amount\":1e999999999}").get("amount")));
        assertTrue(new ScriptRewardForm(huge, null).value(huge.fields().get(0)).length() < 32);
    }
}
