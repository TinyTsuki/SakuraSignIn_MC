package xin.vanilla.sakura.client.gui;

import net.minecraft.resources.ResourceLocation;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * 锁定 Banira 进度选择结果作为领域值交给注册表编辑器。
 */
public class AdvancementRewardSelectionFlowTest {
    @Test
    public void preservesAdvancementAndProbability() {
        ResourceLocation selected = new ResourceLocation("minecraft", "story/mine_stone");

        ResourceLocation reward = AdvancementRewardSelectionFlow.copyValue(selected);

        assertEquals(selected, reward);
    }

    @Test(expected = ClassNotFoundException.class)
    public void legacySakuraAdvancementSelectorIsRemoved() throws Exception {
        Class.forName("xin.vanilla.sakura.screen.AdvancementSelectScreen");
    }
}
