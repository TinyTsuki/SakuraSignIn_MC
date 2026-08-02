package xin.vanilla.sakura.screen;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LotteryScreenTest {
    @Test
    public void finitePoolOffersPresetCountsExactMaximumAndAll() {
        LotteryPool pool = new LotteryPool("pool", "Pool", LotteryLimitPolicy.WEEKLY,
                12, 0, new RewardList());

        assertEquals(Arrays.asList("1", "5", "10", "12", "all"),
                LotteryScreen.drawCounts(pool));
    }

    @Test
    public void unlimitedPoolDoesNotPretendToHaveACompleteAllDraw() {
        LotteryPool pool = new LotteryPool("pool", "Pool", LotteryLimitPolicy.UNLIMITED,
                1, 0, new RewardList());

        assertFalse(LotteryScreen.drawCounts(pool).contains("all"));
        assertEquals("100", LotteryScreen.drawCounts(pool)
                .get(LotteryScreen.drawCounts(pool).size() - 1));
    }

    @Test
    public void closeButtonUsesBaniraWindowPresetAndInset() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/sakura/screen/LotteryScreen.java")),
                StandardCharsets.UTF_8);
        org.junit.Assert.assertTrue(source.contains("presetStyleClose()"));
        org.junit.Assert.assertTrue(source.contains("PANEL_TOP + CLOSE_PAD"));
        org.junit.Assert.assertTrue(source.contains("CLOSE_SIZE / 3f"));
    }
}
