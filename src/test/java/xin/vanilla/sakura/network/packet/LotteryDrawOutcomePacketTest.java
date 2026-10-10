package xin.vanilla.sakura.network.packet;

import org.junit.Test;
import xin.vanilla.sakura.network.TestBaniraPacketBuffer;
import java.util.UUID;
import static org.junit.Assert.*;

public class LotteryDrawOutcomePacketTest {
    @Test public void outcomeCarriesOnlyCanonicalOperationId() {
        String id = UUID.randomUUID() + "-19";
        TestBaniraPacketBuffer buffer = new TestBaniraPacketBuffer();
        new LotteryDrawOutcomePacket(id).toBytes(buffer);
        assertEquals(id, new LotteryDrawOutcomePacket(buffer).getOperationId());
    }
    @Test(expected = IllegalArgumentException.class)
    public void outcomeRejectsInvalidOperationId() { new LotteryDrawOutcomePacket("bad"); }
}
