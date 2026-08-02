package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardProbabilityInputTest {
    @Test
    public void displaysAndParsesPercentInsteadOfInternalRatio() {
        assertEquals("1", RewardProbabilityInput.display(new BigDecimal("0.01")));
        assertEquals(0, new BigDecimal("0.01").compareTo(RewardProbabilityInput.parse("1")));
        assertEquals("10", RewardProbabilityInput.display(new BigDecimal("0.1")));
    }

    @Test
    public void validatesHumanPercentRange() {
        assertTrue(RewardProbabilityInput.isValidPercent("0.00001"));
        assertTrue(RewardProbabilityInput.isValidPercent("100"));
        assertFalse(RewardProbabilityInput.isValidPercent("0"));
        assertFalse(RewardProbabilityInput.isValidPercent("100.1"));
    }
}
