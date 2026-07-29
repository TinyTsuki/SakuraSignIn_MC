package xin.vanilla.sakura.client.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.Test;
import xin.vanilla.sakura.enums.ERewardType;
import xin.vanilla.sakura.rewards.Reward;
import xin.vanilla.sakura.rewards.RewardManager;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * 锁定 Banira 选择结果转换为 Sakura 奖励时的内容与概率。
 */
public class ItemRewardSelectionFlowTest {
    @Test
    public void preservesStackAndProbability() {
        ItemStack selected = new ItemStack(Items.DIAMOND, 17);
        selected.getOrCreateTag().putString("sakura_test", "kept");

        Reward reward = ItemRewardSelectionFlow.toReward(selected, new BigDecimal("0.375"));
        ItemStack decoded = RewardManager.deserializeReward(reward);

        assertEquals(ERewardType.ITEM, reward.getType());
        assertEquals(new BigDecimal("0.375"), reward.getProbability());
        assertEquals(Items.DIAMOND, decoded.getItem());
        assertEquals(17, decoded.getCount());
        assertFalse(decoded.isEmpty());
        assertEquals("kept", decoded.getTag().getString("sakura_test"));
    }

    @Test(expected = ClassNotFoundException.class)
    public void legacySakuraItemSelectorIsRemoved() throws Exception {
        Class.forName("xin.vanilla.sakura.screen.ItemSelectScreen");
    }
}
