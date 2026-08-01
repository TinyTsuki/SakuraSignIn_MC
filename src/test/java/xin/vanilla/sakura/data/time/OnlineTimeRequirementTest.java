package xin.vanilla.sakura.data.time;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OnlineTimeRequirementTest {
    @Test
    public void checksLifetimeAndTodayFromVanillaTicks() {
        OnlineTimeRequirementResult result = OnlineTimeRequirement.evaluate(
                "2026-08-02", 12_000, LocalDate.of(2026, 8, 2),
                18_000, 800, 301);

        assertEquals(900L, result.getTotalSeconds());
        assertEquals(300L, result.getTodaySeconds());
        assertEquals(1L, result.getMissingTodaySeconds());
        assertFalse(result.isAllowed());
    }

    @Test
    public void resetsPersistedBaselineWhenDateChanges() {
        OnlineTimeRequirementResult result = OnlineTimeRequirement.evaluate(
                "2026-08-01", 1_000, LocalDate.of(2026, 8, 2),
                20_000, 0, 0);

        assertTrue(result.isBaselineChanged());
        assertEquals("2026-08-02", result.getBaselineDate());
        assertEquals(20_000, result.getBaselineTicks());
        assertEquals(0L, result.getTodaySeconds());
        assertTrue(result.isAllowed());
    }
}
