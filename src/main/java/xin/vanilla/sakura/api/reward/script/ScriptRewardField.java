package xin.vanilla.sakura.api.reward.script;

import com.google.gson.*;

import java.math.BigDecimal;

/**
 * Data-only form metadata. Script execution and authoritative validation stay on the server.
 */
public final class ScriptRewardField {
    public enum Type {TEXT, INTEGER, DECIMAL, BOOLEAN, JSON}

    private final String key;
    private final String label;
    private final Type type;
    private final BigDecimal min;
    private final BigDecimal max;

    private ScriptRewardField(String key, String label, Type type, BigDecimal min, BigDecimal max) {
        if (key == null || key.isEmpty() || key.length() > 128 || label == null || label.trim().isEmpty()
                || label.length() > 128 || type == null || min != null && max != null && min.compareTo(max) > 0)
            throw new IllegalArgumentException("Invalid script field");
        this.key = key;
        this.label = label;
        this.type = type;
        this.min = min;
        this.max = max;
    }

    public static ScriptRewardField integer(String key, String label, long min, long max) {
        return new ScriptRewardField(key, label, Type.INTEGER, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    public static ScriptRewardField decimal(String key, String label, String min, String max) {
        return new ScriptRewardField(key, label, Type.DECIMAL, number(min), number(max));
    }

    public static ScriptRewardField text(String key, String label) {
        return new ScriptRewardField(key, label, Type.TEXT, null, null);
    }

    public static ScriptRewardField bool(String key, String label) {
        return new ScriptRewardField(key, label, Type.BOOLEAN, null, null);
    }

    public static ScriptRewardField infer(String key, JsonElement value) {
        Type type = Type.JSON;
        if (value != null && value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) type = Type.BOOLEAN;
            else if (primitive.isString()) type = Type.TEXT;
            else if (primitive.isNumber()) {
                BigDecimal number = editableNumber(primitive);
                if (number != null)
                    type = number.stripTrailingZeros().scale() <= 0 ? Type.INTEGER : Type.DECIMAL;
            }
        }
        return new ScriptRewardField(key, key, type, null, null);
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public Type type() {
        return type;
    }

    public String minimum() {
        return min == null ? "" : min.toPlainString();
    }

    public String maximum() {
        return max == null ? "" : max.toPlainString();
    }

    public String format(JsonElement value) {
        if (value == null) return "";
        if ((type == Type.INTEGER || type == Type.DECIMAL) && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            BigDecimal number = editableNumber(value);
            if (number == null) return value.toString();
            return (type == Type.INTEGER ? number.stripTrailingZeros() : number).toPlainString();
        }
        return type != Type.JSON && value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    public JsonElement parse(String input) {
        if (input == null || input.length() > 16384) throw new IllegalArgumentException("Invalid field length");
        switch (type) {
            case TEXT:
                return new JsonPrimitive(input);
            case BOOLEAN:
                if (!input.equals("true") && !input.equals("false"))
                    throw new IllegalArgumentException("Invalid boolean");
                return new JsonPrimitive(Boolean.parseBoolean(input));
            case INTEGER:
            case DECIMAL:
                String trimmed = input.trim();
                if (type == Type.INTEGER && !trimmed.matches("-?[0-9]+"))
                    throw new IllegalArgumentException("Expected integer");
                BigDecimal value = number(trimmed);
                if (min != null && value.compareTo(min) < 0 || max != null && value.compareTo(max) > 0)
                    throw new IllegalArgumentException("Number out of range");
                return new JsonPrimitive(value);
            default:
                if (input.trim().isEmpty()) throw new IllegalArgumentException("Expected JSON");
                return new JsonParser().parse(input);
        }
    }

    private static BigDecimal number(String input) {
        if (input == null || input.length() > 128 || !input.matches("-?[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalArgumentException("Expected decimal number");
        return new BigDecimal(input);
    }

    private static BigDecimal editableNumber(JsonElement value) {
        try {
            BigDecimal number = value.getAsBigDecimal();
            return plainNumberFits(number) ? number : null;
        } catch (NumberFormatException unsupported) {
            return null;
        }
    }

    private static boolean plainNumberFits(BigDecimal value) {
        // Bound expansion before converting server-supplied exponent notation to text.
        return value.precision() <= 64 && value.scale() >= -32 && value.scale() <= 32;
    }

    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.addProperty("key", key);
        result.addProperty("label", label);
        result.addProperty("type", type.name());
        if (min != null) result.addProperty("min", minimum());
        if (max != null) result.addProperty("max", maximum());
        return result;
    }

    public static ScriptRewardField fromJson(JsonObject value) {
        return new ScriptRewardField(value.get("key").getAsString(), value.get("label").getAsString(),
                Type.valueOf(value.get("type").getAsString()), value.has("min") ? number(value.get("min").getAsString()) : null,
                value.has("max") ? number(value.get("max").getAsString()) : null);
    }
}
