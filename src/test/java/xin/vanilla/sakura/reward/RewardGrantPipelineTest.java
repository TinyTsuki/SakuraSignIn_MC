package xin.vanilla.sakura.reward;

import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.RewardAddPermission;
import xin.vanilla.sakura.api.reward.RewardCodec;
import xin.vanilla.sakura.api.reward.RewardDataException;
import xin.vanilla.sakura.api.reward.RewardGrantResult;
import xin.vanilla.sakura.api.reward.RewardGrantStatus;
import xin.vanilla.sakura.api.reward.RewardRegistryTestSupport;
import xin.vanilla.sakura.api.reward.RewardTypeDefinition;
import xin.vanilla.sakura.api.reward.RewardTypeId;
import xin.vanilla.sakura.api.reward.RewardValidator;
import xin.vanilla.sakura.api.reward.SakuraRewards;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class RewardGrantPipelineTest {

    @Before
    @After
    public void resetRegistry() {
        RewardRegistryTestSupport.reset();
    }

    @Test
    public void isolatesUnavailableInvalidAndFailedRewards() {
        AtomicInteger successfulExecutions = new AtomicInteger();
        RewardTypeId failed = RewardTypeId.parse("test:failed");
        RewardTypeId success = RewardTypeId.parse("test:success");
        SakuraRewards.register(definition(failed, value -> {
            throw new IllegalStateException("expected test failure");
        }));
        SakuraRewards.register(definition(success, value -> {
            successfulExecutions.incrementAndGet();
            return RewardGrantResult.success();
        }));

        Reward unknown = new Reward(value(1), RewardTypeId.parse("missing:coin"));
        Reward invalid = new Reward(new JsonObject(), success);
        Reward failing = new Reward(value(1), failed);
        Reward succeeding = new Reward(value(2), success);

        List<RewardGrantResult> results = RewardOperations.grantAll(null,
                Arrays.asList(unknown, invalid, failing, succeeding));

        assertEquals(Arrays.asList(
                        RewardGrantStatus.TYPE_UNAVAILABLE,
                        RewardGrantStatus.INVALID_CONTENT,
                        RewardGrantStatus.FAILED,
                        RewardGrantStatus.SUCCESS),
                Arrays.asList(results.get(0).getStatus(), results.get(1).getStatus(),
                        results.get(2).getStatus(), results.get(3).getStatus()));
        assertEquals(1, successfulExecutions.get());
    }

    private static RewardTypeDefinition<Integer> definition(
            RewardTypeId id, TestExecutor executor) {
        return RewardTypeDefinition.builder(id, new PositiveIntegerCodec(),
                        (context, value) -> executor.grant(value))
                .validator(RewardValidator.acceptAll())
                .describer((languageCode, value, withAmount) ->
                        xin.vanilla.sakura.SakuraComponent.get().literal(String.valueOf(value)))
                .addPermission(RewardAddPermission.of(0,
                        id.getNamespace() + ":reward.add." + id.getPath()))
                .build();
    }

    private static JsonObject value(int value) {
        JsonObject result = new JsonObject();
        result.addProperty("value", value);
        return result;
    }

    private interface TestExecutor {
        RewardGrantResult grant(int value);
    }

    private static final class PositiveIntegerCodec implements RewardCodec<Integer> {
        @Override
        public Integer decode(JsonObject content) throws RewardDataException {
            if (content == null || !content.has("value")) {
                throw new RewardDataException("Missing value");
            }
            return content.get("value").getAsInt();
        }

        @Override
        public JsonObject encode(Integer value) {
            return RewardGrantPipelineTest.value(value);
        }
    }
}
