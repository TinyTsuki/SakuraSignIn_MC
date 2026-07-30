package xin.vanilla.sakura.client.gui;

import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.EffectType;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.test.BaniraTestPlatform;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/**
 * 锁定 Banira 选择结果转换为 Sakura 效果奖励时的内容与概率。
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

        Reward reward = EffectRewardSelectionFlow.toReward(selected, new BigDecimal("0.625"));

        assertEquals(ERewardType.EFFECT, reward.getType());
        assertEquals(new BigDecimal("0.625"), reward.getProbability());
        assertEquals("sakura_sign_in:test_effect", reward.getContent().get("effect").getAsString());
        assertEquals(7200, reward.getContent().get("duration").getAsInt());
        assertEquals(3, reward.getContent().get("amplifier").getAsInt());
    }

    @Test(expected = ClassNotFoundException.class)
    public void legacySakuraEffectSelectorIsRemoved() throws Exception {
        Class.forName("xin.vanilla.sakura.screen.EffecrSelectScreen");
    }
}
