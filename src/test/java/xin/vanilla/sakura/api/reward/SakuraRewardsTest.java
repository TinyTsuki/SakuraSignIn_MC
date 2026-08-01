package xin.vanilla.sakura.api.reward;

import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SakuraRewardsTest {

    @Before
    @After
    public void resetRegistry() {
        SakuraRewards.clearForTests();
    }

    @Test
    public void registersDefinitionsInStableIdOrder() {
        RewardTypeDefinition<Integer> zeta = definition("zeta:last", "zeta:reward.add.last");
        RewardTypeDefinition<Integer> alpha = definition("alpha:first", "alpha:reward.add.first");

        assertSame(zeta, SakuraRewards.register(zeta));
        SakuraRewards.register(alpha);

        assertSame(alpha, SakuraRewards.require(alpha.getId()));
        assertEquals(Arrays.asList("alpha:first", "zeta:last"), SakuraRewards.all().stream()
                .map(value -> value.getId().toString())
                .collect(Collectors.toList()));
    }

    @Test
    public void rejectsDuplicatesAndRegistrationAfterFreeze() {
        RewardTypeDefinition<Integer> definition = definition(
                "example:coin", "example:reward.add.coin");
        SakuraRewards.register(definition);

        assertThrows(IllegalStateException.class, () -> SakuraRewards.register(definition));
        SakuraRewards.freeze();
        assertTrue(SakuraRewards.isFrozen());
        assertThrows(IllegalStateException.class, () -> SakuraRewards.register(
                definition("example:point", "example:reward.add.point")));
    }

    @Test
    public void validatesAddPermissionMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> RewardAddPermission.of(-1, "example:reward.add.coin"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardAddPermission.of(5, "example:reward.add.coin"));
        assertThrows(IllegalArgumentException.class,
                () -> RewardAddPermission.of(2, "missing_namespace"));
    }

    private static RewardTypeDefinition<Integer> definition(String id, String permissionKey) {
        return RewardTypeDefinition.builder(RewardTypeId.parse(id), new IntegerCodec(),
                        (context, value) -> RewardGrantResult.success())
                .validator(value -> value > 0
                        ? Collections.emptyList()
                        : Collections.singletonList(new RewardViolation("value", "positive")))
                .describer((languageCode, value, withAmount) ->
                        xin.vanilla.sakura.SakuraComponent.get().literal(String.valueOf(value)))
                .addPermission(RewardAddPermission.of(2, permissionKey))
                .build();
    }

    private static final class IntegerCodec implements RewardCodec<Integer> {
        @Override
        public Integer decode(JsonObject content) throws RewardDataException {
            if (content == null || !content.has("value")) {
                throw new RewardDataException("Missing value");
            }
            return content.get("value").getAsInt();
        }

        @Override
        public JsonObject encode(Integer value) {
            JsonObject result = new JsonObject();
            result.addProperty("value", value);
            return result;
        }
    }
}
