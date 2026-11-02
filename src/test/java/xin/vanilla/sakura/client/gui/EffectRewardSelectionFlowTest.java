package xin.vanilla.sakura.client.gui;

import net.minecraft.util.ResourceLocation;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import static org.junit.Assert.*;

/**
 * 锁定 Banira 选择结果作为领域值原样交给注册表编辑器。
 */
public class EffectRewardSelectionFlowTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        net.minecraft.util.registry.Bootstrap.bootStrap();
    }

    @Test
    public void preservesEffectDurationAmplifierAndProbability() throws Exception {
        ResourceLocation effectId = new ResourceLocation("minecraft", "luck");
        Effect effect = Effects.LUCK;
        BaniraTestPlatform.install();
        BaniraTestPlatform.register(effectId.toString(), effect);
        EffectInstance selected = new EffectInstance(effect, 7200, 3);

        EffectInstance reward = EffectRewardSelectionFlow.copyValue(selected);

        assertSame(effect, reward.getEffect());
        assertEquals(7200, reward.getDuration());
        assertEquals(3, reward.getAmplifier());
    }

    @Test
    public void suppliesPositiveDurationForIncompleteSelectorDefaults() throws Exception {
        Effect effect = Effects.LUCK;
        EffectInstance reward = EffectRewardSelectionFlow.copyValue(
                new EffectInstance(effect));

        assertTrue(reward.getDuration() > 0);
        assertEquals(0, reward.getAmplifier());
    }

}
