package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

public final class CommandRewardCodec implements RewardCodec<CommandRewardData> {
    @Override
    public CommandRewardData decode(JsonObject content) throws RewardDataException {
        try {
            String command = content.get("command").getAsString().trim();
            if (command.isEmpty()) {
                throw new RewardDataException("Command cannot be empty");
            }
            CommandRewardData value = new CommandRewardData(command, optional(content, "displayName"), optional(content, "displayIcon"));
            validate(value);
            return value;
        } catch (RewardDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RewardDataException("Invalid command reward", exception);
        }
    }

    @Override
    public JsonObject encode(CommandRewardData value) throws RewardDataException {
        validate(value);
        String command = value.command();
        if (command.isEmpty()) {
            throw new RewardDataException("Command cannot be empty");
        }
        JsonObject result = new JsonObject();
        result.addProperty("command", command);
        if (!value.displayName().isEmpty()) result.addProperty("displayName", value.displayName());
        if (!value.displayIcon().isEmpty()) result.addProperty("displayIcon", value.displayIcon());
        return result;
    }

    private static String optional(JsonObject content, String key) throws RewardDataException {
        if (!content.has(key)) return "";
        if (!content.get(key).isJsonPrimitive() || !content.getAsJsonPrimitive(key).isString())
            throw new RewardDataException("Invalid command reward " + key);
        return content.get(key).getAsString();
    }

    private static void validate(CommandRewardData value) throws RewardDataException {
        if (value == null || value.command().isEmpty() || value.command().length() > 32767
                || value.displayName().length() > 128 || value.displayIcon().length() > 256
                || (!value.displayIcon().isEmpty() && !value.displayIcon().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))) {
            throw new RewardDataException("Invalid command reward content");
        }
    }
}
