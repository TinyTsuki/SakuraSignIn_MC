package xin.vanilla.sakura.api.reward.script;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.api.reward.RewardDataException;

import java.util.*;
import java.util.function.Function;

/**
 * A detached draft; fields absent from older schemas survive editing unchanged.
 */
public final class ScriptRewardForm {
    private final JsonObject initial;
    private final List<ScriptRewardField> fields;

    public ScriptRewardForm(ScriptRewardDescriptor descriptor, JsonObject value) {
        initial = value == null ? descriptor.defaults() : JsonRewardCodec.copy(value);
        fields = descriptor.fields();
        JsonObject defaults = descriptor.defaults();
        for (ScriptRewardField field : fields) {
            if (!initial.has(field.key())) initial.add(field.key(), defaults.get(field.key()));
        }
    }

    public List<ScriptRewardField> fields() {
        return fields;
    }

    public String value(ScriptRewardField field) {
        return field.format(initial.get(field.key()));
    }

    public JsonObject submit(Function<String, String> values) throws RewardDataException {
        JsonObject result = JsonRewardCodec.copy(initial);
        for (ScriptRewardField field : fields) result.add(field.key(), field.parse(values.apply(field.key())));
        return new JsonRewardCodec().decode(result);
    }
}
