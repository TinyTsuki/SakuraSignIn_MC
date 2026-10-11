package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.network.lottery.LotteryPacketFields;
import static org.junit.Assert.*;

public class LotteryRevealBudgetTest {
    @Test public void everyUTFPrefixIsChargedAgainstWireBudget() {
        LotteryPacketFields.Budget budget = new LotteryPacketFields.Budget(1024);
        char[] text = new char[765]; java.util.Arrays.fill(text, 'x'); budget.add(new String(text));
        assertThrows(IllegalArgumentException.class, () -> {
            for (int i = 0; i < 256; i++) budget.add("");
        });
    }
}
