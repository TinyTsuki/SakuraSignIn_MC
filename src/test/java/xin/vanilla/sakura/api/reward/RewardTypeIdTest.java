package xin.vanilla.sakura.api.reward;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class RewardTypeIdTest {

    @Test
    public void parsesStableNamespacedIds() {
        RewardTypeId id = RewardTypeId.of("example_currency", "account/balance");

        assertEquals("example_currency", id.getNamespace());
        assertEquals("account/balance", id.getPath());
        assertEquals("example_currency:account/balance", id.toString());
        assertEquals(id, RewardTypeId.parse(id.toString()));
        assertNotEquals(id, RewardTypeId.of("example_currency", "account/points"));
    }

    @Test
    public void rejectsLegacyOrMalformedIds() {
        assertThrows(IllegalArgumentException.class, () -> RewardTypeId.parse("ITEM"));
        assertThrows(IllegalArgumentException.class, () -> RewardTypeId.parse("Example:item"));
        assertThrows(IllegalArgumentException.class, () -> RewardTypeId.of("example", ""));
        assertThrows(IllegalArgumentException.class, () -> RewardTypeId.of("", "item"));
    }
}
