package xin.vanilla.sakura.data.lottery;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class LotteryProtocolLimits {
    public static final int MAX_DOCUMENT_BYTES = 1_048_576;
    public static final int MAX_UPLOAD_BYTES = 8_388_608;
    public static final int MAX_UPLOAD_PARTS = 4096;
    public static final int MAX_FRAGMENT_CHARS = 2048;
    public static final int MAX_FRAGMENT_BYTES = 8192;
    public static final int MAX_PARTS = 512;
    public static final int MAX_REVEAL_BYTES = 524_288;
    public static final int MAX_REWARD_CHARS = 32_767;
    public static final int MAX_REWARD_BYTES = 65_536;

    private LotteryProtocolLimits() { }

    public static String text(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("Invalid lottery text length");
        return value;
    }

    public static String token(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value))
                throw new IllegalArgumentException("Invalid lottery token");
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid lottery token", failure);
        }
        return value;
    }

    public static String operationId(String value) {
        if (value == null || value.length() < 38 || value.length() > 56 || value.charAt(36) != '-')
            throw new IllegalArgumentException("Invalid lottery operation id");
        token(value.substring(0, 36));
        String number = value.substring(37);
        if (!number.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid lottery operation id");
        try {
            if (Long.parseLong(number) <= 0) throw new IllegalArgumentException("Invalid lottery operation id");
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid lottery operation id", failure);
        }
        return value;
    }

    public static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    public static void json(String value, int maxBytes) {
        if (value == null || value.length() > maxBytes || bytes(value) > maxBytes)
            throw new IllegalArgumentException("Lottery JSON exceeds byte budget");
        try (JsonReader reader = new JsonReader(new StringReader(value))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IllegalArgumentException("Lottery JSON object required");
            int depth = 0, tokens = 0;
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                if (++tokens > 131_072) throw new IllegalArgumentException("Too many lottery JSON tokens");
                switch (reader.peek()) {
                    case BEGIN_OBJECT:
                        if (++depth > 64) throw new IllegalArgumentException("Lottery JSON nesting too deep");
                        reader.beginObject(); break;
                    case BEGIN_ARRAY:
                        if (++depth > 64) throw new IllegalArgumentException("Lottery JSON nesting too deep");
                        reader.beginArray(); break;
                    case END_OBJECT: reader.endObject(); depth--; break;
                    case END_ARRAY: reader.endArray(); depth--; break;
                    case NAME: reader.nextName(); break;
                    case STRING: case NUMBER: reader.nextString(); break;
                    case BOOLEAN: reader.nextBoolean(); break;
                    case NULL: reader.nextNull(); break;
                    default: throw new IllegalArgumentException("Invalid lottery JSON");
                }
            }
            if (depth != 0) throw new IllegalArgumentException("Incomplete lottery JSON");
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid lottery JSON", failure);
        }
    }
}
