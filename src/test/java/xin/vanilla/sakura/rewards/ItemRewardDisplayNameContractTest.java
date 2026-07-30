package xin.vanilla.sakura.rewards;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 奖励悬浮名称不能修改 ItemStack 缓存的原版组件，否则每帧都会重复追加数量。
 */
public class ItemRewardDisplayNameContractTest {
    @Test
    public void displayNameCopiesTheVanillaTextBeforeAppendingCount() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/rewards/impl/ItemRewardParser.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("literal(itemStack.getHoverName().getString())"));
        assertFalse(source.contains("object(itemStack.getHoverName())"));
    }
}
