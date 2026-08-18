package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.RewardDataException;
import xin.vanilla.sakura.api.reward.RewardRegistryTestSupport;
import xin.vanilla.sakura.api.reward.RewardTypeDefinition;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.api.reward.SakuraRewards;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardOperations;
import xin.vanilla.sakura.test.NeoForgeUnitTestBootstrap;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BuiltInRewardTypesTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        NeoForgeUnitTestBootstrap.bootstrap();
    }

    @Before
    @After
    public void resetRegistry() {
        RewardRegistryTestSupport.reset();
    }

    @Test
    public void registersAllBuiltInsWithStableIdsAndPermissions() {
        BuiltInRewardTypes.register();

        List<String> ids = SakuraRewards.all().stream()
                .map(value -> value.getId().toString())
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(
                "sakura_sign_in:advancement",
                "sakura_sign_in:command",
                "sakura_sign_in:effect",
                "sakura_sign_in:experience_level",
                "sakura_sign_in:experience_point",
                "sakura_sign_in:item",
                "sakura_sign_in:message",
                "sakura_sign_in:sign_in_card"), ids);

        RewardTypeDefinition<?> command = SakuraRewards.require(SakuraRewardTypes.COMMAND);
        assertEquals(2, command.getAddPermission().getPermissionLevel());
        assertEquals("sakura_sign_in:reward.add.command",
                command.getAddPermission().getVirtualPermissionKey());
        for (RewardTypeDefinition<?> definition : SakuraRewards.all()) {
            assertTrue(definition.getAddPermission().getVirtualPermissionKey()
                    .startsWith("sakura_sign_in:reward.add."));
        }

        RewardRegistryTestSupport.reset();
        BuiltInRewardTypes.register();
        assertEquals(8, SakuraRewards.all().size());
    }

    @Test
    public void rejectsMalformedPayloadsInsteadOfCreatingFallbackRewards() {
        assertThrows(RewardDataException.class,
                () -> new ItemRewardCodec().decode(new JsonObject()));
        assertThrows(RewardDataException.class,
                () -> new EffectRewardCodec().decode(new JsonObject()));
        assertThrows(RewardDataException.class,
                () -> new IntegerRewardCodec("expPoint").decode(new JsonObject()));
        assertThrows(RewardDataException.class,
                () -> new AdvancementRewardCodec().decode(new JsonObject()));
        assertThrows(RewardDataException.class,
                () -> new CommandRewardCodec().decode(new JsonObject()));
        assertThrows(RewardDataException.class,
                () -> new MessageRewardCodec().decode(new JsonObject()));
    }

    @Test
    public void registeredMergeStrategiesCombineOnlyMatchingRewards() throws RewardDataException {
        BuiltInRewardTypes.register();

        Reward firstItem = new Reward(new ItemStack(Items.APPLE, 2),
                SakuraRewardTypes.ITEM, new BigDecimal("0.5"));
        Reward secondItem = new Reward(new ItemStack(Items.APPLE, 3),
                SakuraRewardTypes.ITEM, new BigDecimal("0.5"));
        assertTrue(RewardOperations.semanticallyMatches(firstItem, secondItem));
        assertEquals(0, firstItem.getProbability().compareTo(secondItem.getProbability()));
        Reward mergedItem = RewardOperations.merge(firstItem, secondItem).get();
        assertEquals(5, ((ItemStack) RewardOperations.decode(mergedItem)).getCount());

        Reward firstEffect = new Reward(new MobEffectInstance(MobEffects.LUCK, 20, 1),
                SakuraRewardTypes.EFFECT);
        Reward secondEffect = new Reward(new MobEffectInstance(MobEffects.LUCK, 30, 1),
                SakuraRewardTypes.EFFECT);
        Reward mergedEffect = RewardOperations.merge(firstEffect, secondEffect).get();
        assertEquals(50, ((MobEffectInstance) RewardOperations.decode(mergedEffect)).getDuration());

        Reward differentProbability = new Reward(new ItemStack(Items.APPLE, 1),
                SakuraRewardTypes.ITEM, BigDecimal.ONE);
        assertTrue(!RewardOperations.merge(firstItem, differentProbability).isPresent());
        Reward disabled = secondItem.clone().setDisabled(true);
        assertTrue(!RewardOperations.merge(firstItem, disabled).isPresent());
    }
}
