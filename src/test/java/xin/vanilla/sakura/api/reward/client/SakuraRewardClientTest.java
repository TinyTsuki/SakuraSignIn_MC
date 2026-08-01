package xin.vanilla.sakura.api.reward.client;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import xin.vanilla.sakura.SakuraComponent;
import xin.vanilla.sakura.api.reward.RewardTypeId;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SakuraRewardClientTest {

    @Before
    @After
    public void resetRegistry() {
        SakuraRewardClient.clearForTests();
    }

    @Test
    public void ordersExtensionsBySortThenTypeId() {
        RewardClientExtension<Integer> zeta = extension(20);
        RewardClientExtension<Integer> beta = extension(10);
        RewardClientExtension<Integer> alpha = extension(10);

        SakuraRewardClient.register(RewardTypeId.parse("example:zeta"), zeta);
        SakuraRewardClient.register(RewardTypeId.parse("example:beta"), beta);
        SakuraRewardClient.register(RewardTypeId.parse("example:alpha"), alpha);

        assertEquals(Arrays.asList("example:alpha", "example:beta", "example:zeta"),
                SakuraRewardClient.all().stream()
                        .map(value -> value.getTypeId().toString())
                        .collect(Collectors.toList()));
        assertSame(alpha, SakuraRewardClient.require(RewardTypeId.parse("example:alpha"))
                .getExtension());
    }

    @Test
    public void rejectsDuplicatesAndRegistrationAfterFreeze() {
        RewardTypeId id = RewardTypeId.parse("example:coin");
        SakuraRewardClient.register(id, extension(0));

        assertThrows(IllegalStateException.class,
                () -> SakuraRewardClient.register(id, extension(1)));
        SakuraRewardClient.freeze();
        assertTrue(SakuraRewardClient.isFrozen());
        assertThrows(IllegalStateException.class, () -> SakuraRewardClient.register(
                RewardTypeId.parse("example:point"), extension(2)));
    }

    @Test
    public void missingClientExtensionStaysExplicitlyUnresolved() {
        assertFalse(SakuraRewardClient.find(RewardTypeId.parse("missing:coin")).isPresent());
    }

    private static RewardClientExtension<Integer> extension(int sortOrder) {
        return RewardClientExtension.builder(new RewardPresentation<Integer>() {
                    @Override
                    public xin.vanilla.banira.common.data.Component displayName(
                            RewardDisplayContext context, Integer value) {
                        return SakuraComponent.get().literal(String.valueOf(value));
                    }

                    @Override
                    public void renderIcon(RewardRenderContext context, Integer value) {
                        context.drawAmount(String.valueOf(value));
                    }
                })
                .sortOrder(sortOrder)
                .build();
    }
}
