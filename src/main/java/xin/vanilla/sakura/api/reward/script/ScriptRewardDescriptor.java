package xin.vanilla.sakura.api.reward.script;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.api.reward.RewardTypeId;

import java.util.*;

import com.google.gson.JsonArray;

/**
 * Data-only client presentation; never contains source code or class names.
 */
public final class ScriptRewardDescriptor {
    private final RewardTypeId id;
    private final String name;
    private final String icon;
    private final JsonObject defaults;
    private final List<ScriptRewardField> fields;
    private String amountField;

    public ScriptRewardDescriptor(RewardTypeId id, String name, String icon, JsonObject defaults) {
        this(id, name, icon, defaults, Collections.emptyList());
    }

    public ScriptRewardDescriptor(RewardTypeId id, String name, String icon, JsonObject defaults,
                                  List<ScriptRewardField> fields) {
        if (id == null || id.toString().length() > 256 || name == null || name.trim().isEmpty()
                || name.length() > 128 || icon == null || icon.length() > 256
                || !icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || defaults == null || defaults.toString().length() > 4096) {
            throw new IllegalArgumentException("Invalid script reward descriptor");
        }
        this.id = id;
        this.name = name.trim();
        this.icon = icon;
        this.defaults = JsonRewardCodec.copy(defaults);
        if (fields == null || fields.size() > 32) throw new IllegalArgumentException("Too many script fields");
        Map<String, ScriptRewardField> resolved = new LinkedHashMap<>();
        for (ScriptRewardField field : fields) {
            if (field == null || !defaults.has(field.key()) || resolved.put(field.key(), field) != null)
                throw new IllegalArgumentException("Duplicate or missing script field default");
            if (!field.parse(field.format(defaults.get(field.key()))).equals(defaults.get(field.key())))
                throw new IllegalArgumentException("Script field default has the wrong type");
        }
        // Older scripts can contain larger or irregular objects; keep their JSON fallback available.
        boolean infer = defaults.entrySet().size() <= 32 && defaults.entrySet().stream()
                .allMatch(entry -> !entry.getKey().isEmpty() && entry.getKey().length() <= 128);
        if (infer) defaults.entrySet().forEach(entry -> {
            if (!resolved.containsKey(entry.getKey()))
                resolved.put(entry.getKey(), ScriptRewardField.infer(entry.getKey(), entry.getValue()));
        });
        JsonArray metadata = new JsonArray();
        resolved.values().forEach(field -> metadata.add(field.toJson()));
        if (metadata.toString().length() > 8192) {
            if (!fields.isEmpty()) throw new IllegalArgumentException("Script fields too large");
            resolved.clear();
        }
        this.fields = Collections.unmodifiableList(new ArrayList<>(resolved.values()));
        this.amountField = defaults.has("amount") && defaults.get("amount").isJsonPrimitive()
                && defaults.getAsJsonPrimitive("amount").isNumber() ? "amount" : "";
    }

    public RewardTypeId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String icon() {
        return icon;
    }

    public JsonObject defaults() {
        return JsonRewardCodec.copy(defaults);
    }

    public List<ScriptRewardField> fields() {
        return fields;
    }

    public String amountField() {
        return amountField;
    }

    public ScriptRewardDescriptor withAmountField(String field) {
        if (field == null || field.length() > 128 || !field.isEmpty() && (!defaults.has(field)
                || !defaults.get(field).isJsonPrimitive() || !defaults.getAsJsonPrimitive(field).isNumber()))
            throw new IllegalArgumentException("Amount field must have a numeric default");
        ScriptRewardDescriptor copy = new ScriptRewardDescriptor(id, name, icon, defaults, fields);
        copy.amountField = field;
        return copy;
    }

    public ScriptRewardDescriptor withName(String value) {
        return new ScriptRewardDescriptor(id, value, icon, defaults, fields).withAmountField(amountField);
    }

    public String amount(JsonObject value) {
        if (amountField.isEmpty() || value == null || !value.has(amountField)
                || !value.get(amountField).isJsonPrimitive() || !value.getAsJsonPrimitive(amountField).isNumber())
            return "";
        try {
            java.math.BigDecimal number = value.get(amountField).getAsBigDecimal().stripTrailingZeros();
            if (number.precision() > 128 || number.scale() < -32 || number.scale() > 32) return "";
            return number.toPlainString();
        } catch (NumberFormatException invalid) {
            return "";
        }
    }

    public JsonArray fieldsJson() {
        JsonArray result = new JsonArray();
        fields.forEach(field -> result.add(field.toJson()));
        return result;
    }
}
