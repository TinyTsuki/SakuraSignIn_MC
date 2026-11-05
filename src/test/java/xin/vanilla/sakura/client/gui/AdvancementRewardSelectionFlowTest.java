package xin.vanilla.sakura.client.gui;

import net.minecraft.resources.Identifier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 锁定 Banira 进度选择结果作为领域值交给注册表编辑器。
 */
public class AdvancementRewardSelectionFlowTest {
    @Test
    public void preservesAdvancementAndProbability() {
        Identifier selected = Identifier.fromNamespaceAndPath("minecraft", "story/mine_stone");

        Identifier reward = AdvancementRewardSelectionFlow.copyValue(selected);

        assertEquals(selected, reward);
    }
}
