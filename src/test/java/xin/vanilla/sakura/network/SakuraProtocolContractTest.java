package xin.vanilla.sakura.network;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryAnimationStyle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SakuraProtocolContractTest {
    @Test
    public void extendedNullableAnimationOrdinalsRequireProtocolTenAndExcludeEightAndNine() {
        assertEquals("sakura-10", SakuraNetwork.PROTOCOL_VERSION);
        assertFalse(SakuraNetwork.PROTOCOL_VERSION.equals("sakura-8"));
        assertFalse(SakuraNetwork.PROTOCOL_VERSION.equals("sakura-9"));
        assertArrayEquals(new LotteryAnimationStyle[]{LotteryAnimationStyle.STRIP, LotteryAnimationStyle.CARDS,
                LotteryAnimationStyle.ROULETTE, LotteryAnimationStyle.INSTANT, LotteryAnimationStyle.ENVELOPE,
                LotteryAnimationStyle.FAN, LotteryAnimationStyle.BOUNCE, LotteryAnimationStyle.CHEST,
                LotteryAnimationStyle.FLIP}, LotteryAnimationStyle.values());
    }


}
