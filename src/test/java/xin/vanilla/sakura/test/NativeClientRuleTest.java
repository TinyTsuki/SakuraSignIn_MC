package xin.vanilla.sakura.test;

import net.minecraft.client.Minecraft;
import org.junit.Test;

import static org.junit.Assert.*;

public class NativeClientRuleTest {
    @Test
    public void suppliesLanguageAndRestoresThePreviousClientWithoutStartingIt() throws Exception {
        Minecraft previous = Minecraft.getInstance();
        NativeClientRule fixture = new NativeClientRule();
        try {
            fixture.before();
            Minecraft client = Minecraft.getInstance();
            assertNotSame(previous, client);
            assertNotNull(client.font);
            assertNotNull(client.options);
            assertEquals("en_us", client.options.languageCode);
        } finally {
            fixture.after();
        }
        assertSame(previous, Minecraft.getInstance());
    }
}
