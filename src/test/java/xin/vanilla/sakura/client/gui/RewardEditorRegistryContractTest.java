package xin.vanilla.sakura.client.gui;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardEditorRegistryContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void rewardRendererDispatchesThroughClientRegistry() {
        String renderer = read(MAIN.resolve("client/gui/RewardRenderer.java"));

        assertTrue(renderer.contains("SakuraRewardClient.find"));
        assertTrue(renderer.contains("drawPlaceholder"));
        assertFalse(renderer.contains("ERewardType"));
        assertFalse(renderer.contains("reward.getType() =="));
    }

    @Test
    public void rewardOptionScreenDispatchesStableIdsThroughTheRegistry() {
        String screen = read(MAIN.resolve("screen/RewardOptionScreen.java"));

        assertTrue(screen.contains("event.id()"));
        assertTrue(screen.contains("SakuraRewardClient.all()"));
        assertTrue(screen.contains("RewardEditorCoordinator.openCreate"));
        assertTrue(screen.contains("RewardEditorCoordinator.openEdit"));
        assertFalse(screen.contains("ERewardType.values()"));
        assertFalse(screen.contains("reward.getType() =="));
        assertFalse(screen.contains("RewardEditTargets"));
        assertFalse(screen.contains("equalsIgnoreCase(selectedString)"));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
