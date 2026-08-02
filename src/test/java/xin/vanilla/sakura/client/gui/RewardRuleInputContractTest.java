package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 随机奖励组的新建、编辑和粘贴必须共用同一套百分比边界。 */
public class RewardRuleInputContractTest {
    @Test
    public void randomRewardGroupInputsUsePercentConversionEverywhere() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/screen/RewardOptionScreen.java")),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("RewardProbabilityInput.PERCENT_REGEX"));
        assertTrue(source.contains("RewardProbabilityInput.display"));
        assertTrue(source.contains("RewardProbabilityInput.parse"));
        assertFalse(source.contains("0.1#1"));
    }
}
