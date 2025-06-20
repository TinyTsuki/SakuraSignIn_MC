package xin.vanilla.sakura.util;

import com.google.gson.*;
import lombok.NonNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonUtils {
    public static final Gson GSON = new GsonBuilder().enableComplexMapKeySerialization().create();
    public static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();

    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("^\\[(-?\\d+)]");


    private static JsonElement handleArrayAccess(JsonElement current, String key, Matcher matcher) {
        int index = Integer.parseInt(matcher.group(1));
        if (!current.isJsonArray()) {
            throw new ClassCastException("Expected array at path segment '" + key + "'");
        }
        JsonArray array = current.getAsJsonArray();
        if (index < 0) {
            index += array.size();
        }
        if (index < 0 || index >= array.size()) {
            throw new IndexOutOfBoundsException("Array index " + index + " out of bounds [0-"
                    + (array.size() - 1) + "] at path segment '" + key + "'");
        }
        return array.get(index);
    }

    private static JsonElement handleObjectAccess(JsonElement current, String key) {
        if (!current.isJsonObject()) {
            throw new ClassCastException("Expected object at path segment '" + key + "'");
        }
        JsonObject obj = current.getAsJsonObject();
        if (!obj.has(key)) {
            throw new NoSuchElementException("Missing field '" + key + "'");
        }
        return obj.get(key);
    }

    private static String buildErrorContext(String fullPath, List<String> keys, int failIndex) {
        return String.format("Path traversal failed at segment [%d/%d] '%s' in full path: '%s'",
                failIndex + 1, keys.size(), keys.get(failIndex), fullPath);
    }

    private static void updateParent(JsonElement parent, Object parentKey, JsonElement newElement) {
        if (parent == null) return;

        if (parent.isJsonObject()) {
            parent.getAsJsonObject().add((String) parentKey, newElement);
        } else if (parent.isJsonArray()) {
            int index = (Integer) parentKey;
            parent.getAsJsonArray().set(index, newElement);
        }
    }

    private static void ensureArrayCapacity(JsonArray array, int index) {
        int required = index > 0 ? index + 1 : -index;
        if (array.size() < required) {
            for (int i = array.size(); i < required; i++) {
                array.add(new JsonObject());
            }
        }
    }

    private static void ensureElementIsObject(JsonArray array, int index) {
        JsonElement element = array.get(index);
        if (!element.isJsonObject() || (element.isJsonObject() && element.getAsJsonObject().size() == 0)) {
            array.set(index, new JsonObject());
        }
    }

    private static void addItem(JsonArray array, Object item) {
        if (item instanceof JsonElement) {
            array.add((JsonElement) item);
        } else if (item instanceof String) {
            array.add(new JsonPrimitive((String) item));
        } else if (item instanceof Number) {
            array.add(new JsonPrimitive(((Number) item).doubleValue()));
        } else if (item instanceof Boolean) {
            array.add(new JsonPrimitive((Boolean) item));
        } else if (item instanceof Character) {
            array.add(new JsonPrimitive(String.valueOf(item)));
        } else if (item instanceof Collection) {
            JsonArray arr = new JsonArray();
            for (Object it : (Collection<?>) item) {
                addItem(arr, it);
            }
        } else if (item.getClass().isArray()) {
            JsonArray arr = new JsonArray();
            int length = Array.getLength(item);
            for (int i = 0; i < length; i++) {
                Object it = Array.get(item, i);
                addItem(arr, it);
            }
        }
    }

    private static List<String> parsePath(String path) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;

        for (char c : path.toCharArray()) {
            if (!escaped && c == '\\') {
                escaped = true;
                continue;
            }

            if (!escaped && c == '.') {
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
                escaped = false;
            }
        }

        if (sb.length() > 0) {
            parts.add(sb.toString());
        }

        return parts;
    }

    /**
     * 获取父路径
     */
    public static String getParentPath(String path) {
        List<String> keys = parsePath(path);
        if (keys.isEmpty()) {
            return "";
        }

        StringBuilder parentPath = new StringBuilder();
        for (int i = 0; i < keys.size() - 1; i++) {
            parentPath.append(keys.get(i));
            if (i < keys.size() - 2) {
                parentPath.append(".");
            }
        }
        return parentPath.toString();
    }

    /**
     * 获取最后一个键
     */
    public static String getLastKey(String path) {
        List<String> keys = parsePath(path);
        if (keys.isEmpty()) {
            return "";
        }
        return keys.get(keys.size() - 1);
    }

    /**
     * 获取第一个键
     */
    public static String getFirstKey(String path) {
        List<String> keys = parsePath(path);
        if (keys.isEmpty()) {
            return "";
        }
        return keys.get(0);
    }

    /**
     * 获取 JSON 元素
     */
    @NonNull
    public static JsonElement getJsonElement(@NonNull JsonElement json, @NonNull String path) {
        List<String> keys = parsePath(path);
        if (keys.isEmpty()) {
            return json;
        }

        JsonElement current = json;

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            boolean isLastKey = i == keys.size() - 1;
            Matcher matcher = ARRAY_INDEX_PATTERN.matcher(key);

            try {
                if (matcher.find()) {
                    current = handleArrayAccess(current, key, matcher);
                } else {
                    current = handleObjectAccess(current, key);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(buildErrorContext(path, keys, i), e);
            }

            if (!isLastKey && current.isJsonNull()) {
                throw new IllegalArgumentException(buildErrorContext(path, keys, i)
                        + " cannot access children of null value");
            }
        }

        return current;
    }

    /**
     * 设置 JSON 元素
     */
    public static JsonElement setJsonElement(@NonNull JsonElement json, @NonNull String path, @NonNull JsonElement value) {
        List<String> keys = parsePath(path);
        if (keys.isEmpty()) {
            return value;
        }

        JsonElement root = json;
        JsonElement current = root;
        JsonElement parent = null;
        Object parentKey = null;

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Matcher matcher = ARRAY_INDEX_PATTERN.matcher(key);

            if (matcher.find()) {
                // 处理数组索引
                int index = Integer.parseInt(matcher.group(1));
                if (!current.isJsonArray()) {
                    // 当前节点不是数组，检查是否可以替换
                    if (current.isJsonObject() && current.getAsJsonObject().size() == 0) {
                        JsonArray newArray = new JsonArray();
                        updateParent(parent, parentKey, newArray);
                        // 更新当前节点和根节点（如果是顶级节点）
                        current = newArray;
                        if (parent == null) {
                            root = current;
                        }
                    } else {
                        throw new IllegalArgumentException("Path '" + key + "' requires array but found " + current.getClass().getSimpleName());
                    }
                }

                JsonArray array = current.getAsJsonArray();
                ensureArrayCapacity(array, index);

                if (index < 0) {
                    index += array.size();
                }
                if (i == keys.size() - 1) {
                    array.set(index, value);
                } else {
                    ensureElementIsObject(array, index);
                    parent = array;
                    parentKey = index;
                    current = array.get(index);
                }
            } else {
                // 处理对象字段
                if (!current.isJsonObject()) {
                    throw new IllegalArgumentException("Path '" + key + "' requires object but found " + current.getClass().getSimpleName());
                }

                JsonObject obj = current.getAsJsonObject();
                if (i == keys.size() - 1) {
                    obj.add(key, value);
                } else {
                    if (!obj.has(key)) {
                        obj.add(key, new JsonObject());
                    }
                    parent = obj;
                    parentKey = key;
                    current = obj.get(key);
                }
            }
        }

        return root;
    }

    /**
     * 获取 JsonObject
     */
    public static JsonObject getJsonObject(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        } else {
            throw new IllegalArgumentException("Expected JsonObject at path: " + path);
        }
    }

    /**
     * 获取 JsonObject
     *
     * @param defaultValue 默认值
     */
    public static JsonObject getJsonObject(@NonNull JsonElement json, @NonNull String path, JsonObject defaultValue) {
        try {
            return getJsonObject(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置 JsonObject
     */
    public static JsonElement setJsonObject(@NonNull JsonElement json, @NonNull String path, @NonNull JsonObject value) {
        return setJsonElement(json, path, value);
    }

    /**
     * 获取 JsonArray
     */
    public static JsonArray getJsonArray(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        } else {
            throw new IllegalArgumentException("Expected JsonArray at path: " + path);
        }
    }

    /**
     * 获取 JsonArray
     *
     * @param defaultValue 默认值
     */
    public static JsonArray getJsonArray(@NonNull JsonElement json, @NonNull String path, JsonArray defaultValue) {
        try {
            return getJsonArray(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置 JsonArray
     */
    public static JsonElement setJsonArray(@NonNull JsonElement json, @NonNull String path, @NonNull JsonArray value) {
        return setJsonElement(json, path, value);
    }

    /**
     * 获取 字符串
     */
    public static String getString(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        } else {
            return element.toString();
        }
    }

    /**
     * 获取 字符串
     *
     * @param defaultValue 默认值
     */
    public static String getString(@NonNull JsonElement json, @NonNull String path, String defaultValue) {
        try {
            return getString(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置 字符串
     */
    public static JsonElement setString(@NonNull JsonElement json, @NonNull String path, @NonNull String value) {
        JsonElement newValue = new JsonPrimitive(value);
        return setJsonElement(json, path, newValue);
    }

    /**
     * 获取整数
     */
    public static int getInt(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive number at path: " + path);
        }
    }

    /**
     * 获取整数
     *
     * @param defaultValue 默认值
     */
    public static int getInt(@NonNull JsonElement json, @NonNull String path, int defaultValue) {
        try {
            return getInt(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置整数
     */
    public static JsonElement setInt(@NonNull JsonElement json, @NonNull String path, int value) {
        return setLong(json, path, value);
    }

    /**
     * 获取布尔值
     */
    public static boolean getBoolean(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive boolean at path: " + path);
        }
    }

    /**
     * 获取布尔值
     *
     * @param defaultValue 默认值
     */
    public static boolean getBoolean(@NonNull JsonElement json, @NonNull String path, boolean defaultValue) {
        try {
            return getBoolean(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置布尔值
     */
    public static JsonElement setBoolean(@NonNull JsonElement json, @NonNull String path, boolean value) {
        JsonElement newValue = new JsonPrimitive(value);
        return setJsonElement(json, path, newValue);
    }

    /**
     * 获取双精度浮点数
     */
    public static double getDouble(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive number at path: " + path);
        }
    }

    /**
     * 获取双精度浮点数
     *
     * @param defaultValue 默认值
     */
    public static double getDouble(@NonNull JsonElement json, @NonNull String path, double defaultValue) {
        try {
            return getDouble(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置双精度浮点数
     */
    public static JsonElement setDouble(@NonNull JsonElement json, @NonNull String path, double value) {
        JsonElement newValue = new JsonPrimitive(value);
        return setJsonElement(json, path, newValue);
    }

    /**
     * 获取长整型
     */
    public static long getLong(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive number at path: " + path);
        }
    }

    /**
     * 获取长整型
     *
     * @param defaultValue 默认值
     */
    public static long getLong(@NonNull JsonElement json, @NonNull String path, long defaultValue) {
        try {
            return getLong(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置长整型
     */
    public static JsonElement setLong(@NonNull JsonElement json, @NonNull String path, long value) {
        return setFloat(json, path, value);
    }

    /**
     * 获取单精度浮点数
     */
    public static float getFloat(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsFloat();
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive number at path: " + path);
        }
    }

    /**
     * 获取单精度浮点数
     *
     * @param defaultValue 默认值
     */
    public static float getFloat(@NonNull JsonElement json, @NonNull String path, float defaultValue) {
        try {
            return getFloat(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置单精度浮点数
     */
    public static JsonElement setFloat(@NonNull JsonElement json, @NonNull String path, float value) {
        return setDouble(json, path, value);
    }

    /**
     * 获取字节
     */
    public static byte getByte(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsByte();
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive number at path: " + path);
        }
    }

    /**
     * 获取字节
     *
     * @param defaultValue 默认值
     */
    public static byte getByte(@NonNull JsonElement json, @NonNull String path, byte defaultValue) {
        try {
            return getByte(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置字节
     */
    public static JsonElement setByte(@NonNull JsonElement json, @NonNull String path, byte value) {
        return setInt(json, path, value);
    }

    /**
     * 获取短整型
     */
    public static short getShort(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsShort();
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive number at path: " + path);
        }
    }

    /**
     * 获取短整型
     *
     * @param defaultValue 默认值
     */
    public static short getShort(@NonNull JsonElement json, @NonNull String path, short defaultValue) {
        try {
            return getShort(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置短整型
     */
    public static JsonElement setShort(@NonNull JsonElement json, @NonNull String path, short value) {
        return setInt(json, path, value);
    }

    /**
     * 获取字符
     */
    public static char getChar(@NonNull JsonElement json, @NonNull String path) {
        JsonElement element = getJsonElement(json, path);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String str = element.getAsString();
            if (str.length() == 1) {
                return str.charAt(0);
            } else {
                throw new IllegalArgumentException("Expected single character at path: " + path);
            }
        } else {
            throw new IllegalArgumentException("Expected JsonPrimitive string at path: " + path);
        }
    }

    /**
     * 获取字符
     *
     * @param defaultValue 默认值
     */
    public static char getChar(@NonNull JsonElement json, @NonNull String path, char defaultValue) {
        try {
            return getChar(json, path);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 设置字符
     */
    public static JsonElement setChar(@NonNull JsonElement json, @NonNull String path, char value) {
        JsonElement newValue = new JsonPrimitive(String.valueOf(value));
        return setJsonElement(json, path, newValue);
    }

    /**
     * 设置值
     */
    public static JsonElement set(@NonNull JsonElement json, @NonNull String path, @NonNull Object value) {
        if (value instanceof JsonElement) {
            return setJsonElement(json, path, (JsonElement) value);
        } else if (value instanceof String) {
            return setString(json, path, (String) value);
        } else if (value instanceof Number) {
            return setDouble(json, path, ((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            return setBoolean(json, path, (Boolean) value);
        } else if (value instanceof Character) {
            return setChar(json, path, (Character) value);
        } else if (value instanceof Collection) {
            JsonArray array = new JsonArray();
            for (Object item : (Collection<?>) value) {
                addItem(array, item);
            }
            return setJsonArray(json, path, array);
        } else if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                addItem(array, item);
            }
            return setJsonArray(json, path, array);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + value.getClass());
        }
    }
}
