package xin.vanilla.sakura.client.gui;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 锁定 Banira 选择结果作为领域值原样交给注册表编辑器。
 */
public class EffectRewardSelectionFlowTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    public void preservesEffectDurationAmplifierAndProbability() throws Exception {
        ResourceLocation effectId = new ResourceLocation("minecraft", "luck");
        MobEffect effect = MobEffects.LUCK;
        BaniraTestPlatform.install();
        BaniraTestPlatform.register(effectId.toString(), effect);
        MobEffectInstance selected = new MobEffectInstance(effect, 7200, 3);

        MobEffectInstance reward = EffectRewardSelectionFlow.copyValue(selected);

        assertSame(effect, reward.getEffect());
        assertEquals(7200, reward.getDuration());
        assertEquals(3, reward.getAmplifier());
    }

    @Test
    public void suppliesPositiveDurationForIncompleteSelectorDefaults() throws Exception {
        MobEffect effect = MobEffects.LUCK;
        MobEffectInstance reward = EffectRewardSelectionFlow.copyValue(
                new MobEffectInstance(effect));

        assertTrue(reward.getDuration() > 0);
        assertEquals(0, reward.getAmplifier());
    }

}
