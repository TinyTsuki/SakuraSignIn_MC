package xin.vanilla.sakura.client.reward.builtin;

import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AdvancementPresentationResolverTest {
    @Test
    public void missingDisplayUsesAdvancementFallbackInsteadOfBarrier() {
        assertEquals(new ResourceLocation("minecraft", "knowledge_book"),
                AdvancementPresentationResolver.fallbackIconId());
    }
}
