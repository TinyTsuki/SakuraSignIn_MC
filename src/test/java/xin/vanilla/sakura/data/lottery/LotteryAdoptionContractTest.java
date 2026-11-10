package xin.vanilla.sakura.data.lottery;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class LotteryAdoptionContractTest {
    @Test
    public void poolExposesDescriptionAndVoucherMetadata() {
        try {
            LotteryPool.class.getMethod("getDescription");
            LotteryPool.class.getMethod("getVoucherExpression");
        } catch (NoSuchMethodException missing) {
            fail("Shared-cost pool metadata is missing");
        }
    }


    @Test
    public void extendedAnimationOrdinalsKeepOptionalChannelAndRequireTheNewProtocol() throws Exception {
        assertEquals(0, LotteryAnimationStyle.STRIP.ordinal());
        assertEquals(1, LotteryAnimationStyle.CARDS.ordinal());
        assertEquals(2, LotteryAnimationStyle.ROULETTE.ordinal());
        assertEquals(3, LotteryAnimationStyle.INSTANT.ordinal());
        assertEquals(7, LotteryAnimationStyle.CHEST.ordinal());
        assertEquals(8, LotteryAnimationStyle.FLIP.ordinal());
    }
}
