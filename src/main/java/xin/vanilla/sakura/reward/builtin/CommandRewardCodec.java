package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;

public final class CommandRewardCodec implements RewardCodec<String> {
    @Override
    public String decode(JsonObject content) throws RewardDataException {
        try {
            String command = content.get("command").getAsString().trim();
            if (command.isEmpty()) {
                throw new RewardDataException("Command cannot be empty");
            }
            return command;
        } catch (RewardDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RewardDataException("Invalid command reward", exception);
        }
    }

    @Override
    public JsonObject encode(String value) throws RewardDataException {
        String command = value == null ? "" : value.trim();
        if (command.isEmpty()) {
            throw new RewardDataException("Command cannot be empty");
        }
        JsonObject result = new JsonObject();
        result.addProperty("command", command);
        return result;
    }
}
