package xin.vanilla.sakura.client.reward.builtin;

import net.minecraft.resources.Identifier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AdvancementPresentationResolverTest {
    @Test
    public void missingDisplayUsesAdvancementFallbackInsteadOfBarrier() {
        assertEquals(Identifier.fromNamespaceAndPath("minecraft", "knowledge_book"),
                AdvancementPresentationResolver.fallbackIconId());
    }
}
