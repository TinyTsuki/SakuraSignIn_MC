package xin.vanilla.sakura.network.packet;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadRewardOptionNoticeTest {
    @Test
    public void limitsRepeatedRequestsPerConnection() {
        Object first = new Object();
        Object second = new Object();
        assertFalse(DownloadRewardOptionNotice.allow(null, 0));
        assertTrue(DownloadRewardOptionNotice.allow(first, 0));
        assertFalse(DownloadRewardOptionNotice.allow(first, 2_999_999_999L));
        assertTrue(DownloadRewardOptionNotice.allow(second, 2_999_999_999L));
        assertTrue(DownloadRewardOptionNotice.allow(first, 3_000_000_000L));
    }
}
