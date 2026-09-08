package xin.vanilla.sakura.internal.client.dev;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SakuraNetworkSmokeScreensTest {
    @Test
    public void rejectsFramesThatFinishAfterSamplingEnds() {
        AtomicBoolean sampling = new AtomicBoolean(true);
        SakuraNetworkSmokeScreens.Metrics metrics = new SakuraNetworkSmokeScreens.Metrics("test", sampling::get);
        String before = metrics.summary();
        sampling.set(false);
        metrics.render(100, true, true);
        assertEquals(before, metrics.summary());
    }

    @Test
    public void rejectsFramesThatStartedBeforeSampling() {
        SakuraNetworkSmokeScreens.Metrics metrics = new SakuraNetworkSmokeScreens.Metrics("test", () -> true);
        String before = metrics.summary();
        metrics.render(100, true, false);
        assertEquals(before, metrics.summary());
    }

    @Test
    public void keepsCompletedSampledFramesWhenLaterFramesAreOutsideTheWindow() {
        AtomicBoolean sampling = new AtomicBoolean(true);
        SakuraNetworkSmokeScreens.Metrics metrics = new SakuraNetworkSmokeScreens.Metrics("test", sampling::get);
        metrics.render(100, true, true);
        assertTrue(metrics.summary().contains("test-render-frames=1 test-content-render-frames=1"));
        String sampled = metrics.summary();
        sampling.set(false);
        metrics.render(500, true, false);
        assertEquals(sampled, metrics.summary());
    }
}
