package xin.vanilla.sakura.client.gui;

import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.EffectType;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

/**
 * 锁定 Banira 选择结果作为领域值原样交给注册表编辑器。
 */
public class EffectRewardSelectionFlowTest {
    @Test
    public void preservesEffectDurationAmplifierAndProbability() throws Exception {
        ResourceLocation effectId = new ResourceLocation("sakura_sign_in", "test_effect");
        Effect effect = new Effect(EffectType.BENEFICIAL, 0x7FB8FF) {
        };
        Field registryName = effect.getClass().getSuperclass().getSuperclass()
                .getDeclaredField("registryName");
        registryName.setAccessible(true);
        registryName.set(effect, effectId);
        BaniraTestPlatform.install();
        BaniraTestPlatform.register(effectId.toString(), effect);
        EffectInstance selected = new EffectInstance(effect, 7200, 3);

        EffectInstance reward = EffectRewardSelectionFlow.copyValue(selected);

        assertEquals(effectId, reward.getEffect().getRegistryName());
        assertEquals(7200, reward.getDuration());
        assertEquals(3, reward.getAmplifier());
    }

    @Test(expected = ClassNotFoundException.class)
    public void legacySakuraEffectSelectorIsRemoved() throws Exception {
        Class.forName("xin.vanilla.sakura.screen.EffecrSelectScreen");
    }
}
