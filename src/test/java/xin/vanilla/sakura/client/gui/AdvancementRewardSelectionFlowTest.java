package xin.vanilla.sakura.client.gui;

import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.Reward;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/**
 * 锁定 Banira 选择结果转换为 Sakura 进度奖励时的标识与概率。
 */
public class AdvancementRewardSelectionFlowTest {
    @Test
    public void preservesAdvancementAndProbability() {
        ResourceLocation selected = new ResourceLocation("minecraft", "story/mine_stone");

        Reward reward = AdvancementRewardSelectionFlow.toReward(selected, new BigDecimal("0.45"));

        assertEquals(ERewardType.ADVANCEMENT, reward.getType());
        assertEquals(new BigDecimal("0.45"), reward.getProbability());
        assertEquals("minecraft:story/mine_stone", reward.getContent().get("advancement").getAsString());
    }

    @Test(expected = ClassNotFoundException.class)
    public void legacySakuraAdvancementSelectorIsRemoved() throws Exception {
        Class.forName("xin.vanilla.sakura.screen.AdvancementSelectScreen");
    }
}
