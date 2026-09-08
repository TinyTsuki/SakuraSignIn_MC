package xin.vanilla.sakura.internal.server.dev;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SakuraNetworkSmokeTimingsTest {
    @Test
    public void reportsMeasuredDistributionWithoutMutatingEarlierSamples() {
        SakuraNetworkSmokeTimings samples = new SakuraNetworkSmokeTimings(4);
        samples.record(90);
        samples.record(10);
        samples.record(40);
        assertEquals(3, samples.count());
        assertEquals(46, samples.average());
        assertEquals(40, samples.percentile(50));
        assertEquals(90, samples.percentile(95));
        samples.record(20);
        assertEquals(40, samples.average());
        assertEquals(20, samples.percentile(50));
        assertEquals(90, samples.maximum());
        assertEquals("count=4 average-ns=40 p50-ns=20 p95-ns=90 max-ns=90", samples.summary());
    }

    @Test
    public void boundsStorageAndRejectsNegativeMeasurementsWithoutChangingTheData() {
        SakuraNetworkSmokeTimings samples = new SakuraNetworkSmokeTimings(1);
        try { samples.record(-1); fail("Negative time accepted"); }
        catch (IllegalArgumentException expected) { }
        assertEquals(0, samples.count());
        samples.record(7);
        try { samples.record(8); fail("Capacity exceeded silently"); }
        catch (IllegalStateException expected) { }
        assertEquals(1, samples.count());
        assertEquals(7, samples.maximum());
    }

    @Test
    public void handlesEmptyAndInvalidPercentilesExplicitly() {
        SakuraNetworkSmokeTimings samples = new SakuraNetworkSmokeTimings(2);
        assertEquals(0, samples.average());
        assertEquals(0, samples.percentile(95));
        for (int value : new int[] {0, 101}) {
            try { samples.percentile(value); fail("Invalid percentile accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { new SakuraNetworkSmokeTimings(0); fail("Invalid capacity accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
