package xin.vanilla.sakura.config.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.reward.Reward;

import static org.junit.Assert.assertEquals;

public class UnknownRewardRoundTripTest {

    @Test
    public void keepsUnknownTypeAndPayloadWithoutResolvingItsExtension() throws Exception {
        String source = "{\"schemaVersion\":2,\"groups\":[{"
                + "\"rule\":\"BASE_REWARD\",\"key\":\"base\",\"rewards\":[{"
                + "\"type\":\"example_currency:coin\",\"probability\":\"0.75\","
                + "\"content\":{\"amount\":12,\"account\":{\"kind\":\"wallet\"},"
                + "\"extensionField\":[1,2,3]}}]}]}";
        RewardConfigCodec codec = new RewardConfigCodec();

        RewardConfigDocument decoded = codec.decodeDocument(source);
        Reward reward = decoded.getGroups().get(0).getRewards().get(0);
        String encoded = codec.encodeDocument(decoded);
        RewardConfigDocument roundTrip = codec.decodeDocument(encoded);

        assertEquals(RewardTypeId.parse("example_currency:coin"), reward.getTypeId());
        JsonObject expectedContent = new JsonParser().parse(source).getAsJsonObject()
                .getAsJsonArray("groups").get(0).getAsJsonObject()
                .getAsJsonArray("rewards").get(0).getAsJsonObject()
                .getAsJsonObject("content");
        assertEquals(expectedContent, roundTrip.getGroups().get(0).getRewards()
                .get(0).getContent());
    }

    @Test
    public void keepsMalformedPayloadForARegisteredType() throws Exception {
        String source = "{\"schemaVersion\":2,\"groups\":[{"
                + "\"rule\":\"BASE_REWARD\",\"key\":\"base\",\"rewards\":[{"
                + "\"type\":\"sakura_sign_in:item\",\"probability\":1,"
                + "\"content\":{\"broken\":true}}]}]}";
        RewardConfigCodec codec = new RewardConfigCodec();

        RewardConfigDocument decoded = codec.decodeDocument(source);
        Reward reward = decoded.getGroups().get(0).getRewards().get(0);
        RewardConfigDocument roundTrip = codec.decodeDocument(codec.encodeDocument(decoded));

        assertEquals(RewardTypeId.parse("sakura_sign_in:item"), reward.getTypeId());
        assertEquals(reward.getContent(), roundTrip.getGroups().get(0).getRewards()
                .get(0).getContent());
    }
}
