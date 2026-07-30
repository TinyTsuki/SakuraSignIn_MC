package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * 锁定 Banira 选择结果到 Sakura 奖励的复制与概率语义。
 */
public class ItemRewardSelectionFlowTest {
    @Test
    public void copiesSelectedStackAndPreservesProbability() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/client/gui/ItemRewardSelectionFlow.java")),
                StandardCharsets.UTF_8);
        assertTrue(source.contains(
                "new Reward(itemStack.copy(), ERewardType.ITEM, probability)"));
    }

    @Test(expected = ClassNotFoundException.class)
    public void legacySakuraItemSelectorIsRemoved() throws Exception {
        Class.forName("xin.vanilla.sakura.screen.ItemSelectScreen");
    }
}
