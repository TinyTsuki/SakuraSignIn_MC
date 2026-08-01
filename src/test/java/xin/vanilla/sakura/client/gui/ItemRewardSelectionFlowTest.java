package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * 锁定 Banira 选择结果在交给注册表编辑器前会复制物品。
 */
public class ItemRewardSelectionFlowTest {
    @Test
    public void copiesSelectedStackBeforeSubmittingDomainValue() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/client/gui/ItemRewardSelectionFlow.java")),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("onSelected.accept(itemStack.copy())"));
        assertTrue(source.contains("closeAfterSubmit(true)"));
    }

    @Test(expected = ClassNotFoundException.class)
    public void legacySakuraItemSelectorIsRemoved() throws Exception {
        Class.forName("xin.vanilla.sakura.screen.ItemSelectScreen");
    }
}
