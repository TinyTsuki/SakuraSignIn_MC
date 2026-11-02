package xin.vanilla.sakura.reward.builtin;

import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BuiltInRewardTypesTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        net.minecraft.util.registry.Bootstrap.bootStrap();
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
    public void itemDescriptionPreservesTranslationUntilClientRendering() {
        BuiltInRewardTypes.register();
        Reward reward = new Reward(new ItemStack(Items.APPLE, 2), SakuraRewardTypes.ITEM);
        lombok.var description = reward.getName("zh_cn", true);
        JsonObject wire = description.toJson();
        assertNotNull("Item names must retain the original component on the wire", wire.get("original"));
        assertEquals("item.minecraft.apple", wire.getAsJsonObject("original").get("translate").getAsString());
        assertEquals("x2", description.toVanilla().getSiblings().get(0).getString());
        assertEquals(Items.APPLE.getDescriptionId(), ((net.minecraft.util.text.TranslationTextComponent)
                description.toVanilla()).getKey());
    }

    @Test
    public void itemDescriptionKeepsCustomStyledNameAndOptionalAmount() {
        BuiltInRewardTypes.register();
        ItemStack item = new ItemStack(Items.CAKE, 3);
        lombok.var name = new net.minecraft.util.text.StringTextComponent("Birthday cake")
                .withStyle(net.minecraft.util.text.TextFormatting.GOLD);
        item.setHoverName(name);
        lombok.var description = new Reward(item, SakuraRewardTypes.ITEM).getName("zh_cn", false);
        assertEquals(net.minecraft.util.text.ITextComponent.Serializer.toJsonTree(name),
                description.toJson().get("original"));
        assertEquals("Birthday cake", description.toVanilla().getString());
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

        Reward firstEffect = new Reward(new EffectInstance(Effects.LUCK, 20, 1),
                SakuraRewardTypes.EFFECT);
        Reward secondEffect = new Reward(new EffectInstance(Effects.LUCK, 30, 1),
                SakuraRewardTypes.EFFECT);
        Reward mergedEffect = RewardOperations.merge(firstEffect, secondEffect).get();
        assertEquals(50, ((EffectInstance) RewardOperations.decode(mergedEffect)).getDuration());

        Reward differentProbability = new Reward(new ItemStack(Items.APPLE, 1),
                SakuraRewardTypes.ITEM, BigDecimal.ONE);
        assertTrue(!RewardOperations.merge(firstItem, differentProbability).isPresent());
        Reward disabled = secondItem.clone().setDisabled(true);
        assertTrue(!RewardOperations.merge(firstItem, disabled).isPresent());
    }
}
