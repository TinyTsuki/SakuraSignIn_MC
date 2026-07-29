package xin.vanilla.sakura.network;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class RewardNetworkProtocolTest {
    @Test
    public void changedRewardPacketRejectsOldChannelsButKeepsClientOptional() throws Exception {
        Path source = Paths.get("src/main/java/xin/vanilla/sakura/network/ModNetworkHandler.java");
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);

        assertTrue(text.contains("PROTOCOL_VERSION = \"2\""));
        assertTrue(text.contains("PROTOCOL_VERSION.equals(version)"));
        assertTrue(text.contains("NetworkRegistry.ABSENT.equals(version)"));
        assertTrue(text.contains("NetworkRegistry.ACCEPTVANILLA.equals(version)"));
    }
}
