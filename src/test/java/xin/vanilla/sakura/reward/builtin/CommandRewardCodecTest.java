package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.RewardDataException;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.reward.*;

import static org.junit.Assert.*;

public class CommandRewardCodecTest {
    private final CommandRewardCodec codec = new CommandRewardCodec();

    @Test
    public void oldCommandsRetainTheirPayload() throws Exception {
        JsonObject old = new JsonParser().parse("{\"command\":\"give @s minecraft:paper 1\"}").getAsJsonObject();
        CommandRewardData value = codec.decode(old);
        assertEquals("", value.displayName());
        assertEquals("", value.displayIcon());
        assertEquals(old, codec.encode(value));
        assertEquals(old, new Reward("give @s minecraft:paper 1", SakuraRewardTypes.COMMAND).getContent());
    }

    @Test
    public void optionalPresentationSurvivesPersistenceAndEditing() throws Exception {
        CommandRewardData value = new CommandRewardData("/give @s minecraft:paper 1", "Paper voucher", "minecraft:paper");
        Reward reward = new Reward(codec.encode(value), SakuraRewardTypes.COMMAND);
        Reward restored = RewardJsonCodec.decode(RewardJsonCodec.encode(reward));
        CommandRewardData decoded = codec.decode(restored.getContent());
        assertEquals(value.command(), decoded.command());
        assertEquals(value.displayName(), decoded.displayName());
        assertEquals(value.displayIcon(), decoded.displayIcon());
        assertEquals(restored.getContent(), codec.encode(decoded));
    }

    @Test
    public void clearingPresentationRemovesOptionalFields() throws Exception {
        JsonObject value = codec.encode(new CommandRewardData("say hi", " ", " "));
        assertFalse(value.has("displayName"));
        assertFalse(value.has("displayIcon"));
    }

    @Test
    public void invalidFieldsFailBeforeCommandExecution() {
        assertThrows(RewardDataException.class, () -> codec.encode(new CommandRewardData("", "name", "")));
        assertThrows(RewardDataException.class, () -> codec.encode(new CommandRewardData("say hi", "name", "bad icon")));
        assertThrows(RewardDataException.class, () -> codec.decode(new JsonParser().parse(
                "{\"command\":\"say hi\",\"displayName\":{}}").getAsJsonObject()));
    }
}
