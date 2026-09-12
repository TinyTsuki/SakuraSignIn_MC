package xin.vanilla.sakura.internal.dev;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigScope;
import xin.vanilla.banira.common.config.ConfigValueStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class SakuraNetworkSmokeConfigsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private enum Mode {
        @SerializedName("not-the-toml-name") FIXED_TIME
    }

    private static final String TOML = "[settings]\n"
            + "expression = \"minecraft:item,limit=8\"\n"
            + "entries = [\"minecraft:item,limit=8\", \"other,a,b\"]\n"
            + "modes = [\"FIXED_TIME\"]\n"
            + "mode = \"FIXED_TIME\"\n"
            + "count = 8\nratio = 1.0\nenabled = true\n";

    @Test
    public void snapshotsEveryHolderPathAndRestartsWithCommaListsEnumsAndNumbers() throws Exception {
        Fixture fixture = fixture();
        byte[] original = Files.readAllBytes(fixture.toml);
        JsonObject snapshot = fixture.verify("phase-one");
        assertEquals(fixture.values.size(), snapshot.size());
        assertEquals("FIXED_TIME", snapshot.get("settings.mode").getAsString());
        assertEquals(2, snapshot.getAsJsonArray("settings.entries").size());
        assertEquals("other,a,b", snapshot.getAsJsonArray("settings.entries").get(1).getAsString());
        assertEquals("FIXED_TIME", snapshot.getAsJsonArray("settings.modes").get(0).getAsString());
        fixture.values.put("settings.count", 8L);
        fixture.values.put("settings.ratio", 1);
        assertEquals(snapshot, fixture.verify("phase-two"));
        assertArrayEquals(original, Files.readAllBytes(fixture.toml));
        assertTrue(Files.isRegularFile(fixture.checkpoint));
    }

    @Test
    public void rejectsMissingTomlFieldBeforeWritingCheckpoint() throws Exception {
        Fixture fixture = fixture();
        fixture.writeToml(TOML.replace("count = 8\n", ""));
        rejects(() -> fixture.verify("phase-one"), "settings.count");
        assertFalse(Files.exists(fixture.checkpoint));
    }

    @Test
    public void rejectsChangedTomlValueWithoutOverwritingEvidence() throws Exception {
        Fixture fixture = fixture();
        fixture.verify("phase-one");
        byte[] checkpoint = Files.readAllBytes(fixture.checkpoint);
        fixture.writeToml(TOML.replace("count = 8", "count = 9"));
        byte[] changed = Files.readAllBytes(fixture.toml);
        rejects(() -> fixture.verify("phase-two"), "settings.count");
        assertArrayEquals(changed, Files.readAllBytes(fixture.toml));
        assertArrayEquals(checkpoint, Files.readAllBytes(fixture.checkpoint));
    }

    @Test
    public void rejectsChangedHolderEvenWhenTomlStillMatchesCheckpoint() throws Exception {
        Fixture fixture = fixture();
        fixture.verify("phase-one");
        fixture.values.put("settings.count", 9);
        rejects(() -> fixture.verify("phase-two"), "settings.count");
    }

    @Test
    public void rejectsMatchingHolderAndTomlThatBothDifferFromCheckpoint() throws Exception {
        Fixture fixture = fixture();
        fixture.verify("phase-one");
        fixture.values.put("settings.count", 9);
        fixture.writeToml(TOML.replace("count = 8", "count = 9"));
        rejects(() -> fixture.verify("phase-two"), "settings.count");
    }

    @Test
    public void rejectsMissingCheckpointField() throws Exception {
        Fixture fixture = fixture();
        JsonObject snapshot = fixture.verify("phase-one");
        snapshot.remove("settings.mode");
        Files.write(fixture.checkpoint, snapshot.toString().getBytes(StandardCharsets.UTF_8));
        rejects(() -> fixture.verify("phase-two"), "paths");
    }

    @Test
    public void rejectsMissingHolderPathOnRestart() throws Exception {
        Fixture fixture = fixture();
        fixture.verify("phase-one");
        fixture.values.remove("settings.mode");
        rejects(() -> fixture.verify("phase-two"), "paths");
    }

    @Test
    public void rejectsMissingTomlFieldOnRestart() throws Exception {
        Fixture fixture = fixture();
        fixture.verify("phase-one");
        fixture.writeToml(TOML.replace("count = 8\n", ""));
        rejects(() -> fixture.verify("phase-two"), "settings.count");
    }

    @Test
    public void rejectsEmptyHolderInsteadOfPassingVacuously() throws Exception {
        Fixture fixture = fixture();
        fixture.values.clear();
        rejects(() -> fixture.verify("phase-one"), "empty");
    }

    @Test
    public void rejectsUnknownPhaseWithoutWritingCheckpoint() throws Exception {
        Fixture fixture = fixture();
        rejects(() -> fixture.verify("unknown"), "phase");
        assertFalse(Files.exists(fixture.checkpoint));
    }

    private Fixture fixture() throws Exception {
        return new Fixture(temporary.newFolder().toPath());
    }

    private interface CheckedAction { void run() throws Exception; }

    private static void rejects(CheckedAction action, String message) throws Exception {
        try {
            action.run();
            fail("Invalid config evidence accepted");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private static final class Fixture {
        final Map<String, Object> values = new LinkedHashMap<>();
        final Path directory;
        final Path toml;
        final Path checkpoint;
        final ConfigHolder holder;

        Fixture(Path directory) throws Exception {
            this.directory = directory;
            toml = directory.resolve("fixture.toml");
            checkpoint = directory.resolve("fixture-network-smoke-config.json");
            values.put("settings.expression", "minecraft:item,limit=8");
            values.put("settings.entries", Arrays.asList("minecraft:item,limit=8", "other,a,b"));
            values.put("settings.modes", Collections.singletonList(Mode.FIXED_TIME));
            values.put("settings.mode", Mode.FIXED_TIME);
            values.put("settings.count", 8);
            values.put("settings.ratio", 1.0F);
            values.put("settings.enabled", true);
            ConfigValueStore store = new ConfigValueStore() {
                public Set<String> paths() { return values.keySet(); }
                public Object get(String path) { return values.get(path); }
                public void set(String path, Object value) { throw new AssertionError("Must not set holder"); }
                public Class<?> valueClass(String path) { return values.get(path).getClass(); }
                public Object defaultValue(String path) { return values.get(path); }
                public boolean validate(String path, Object value) { return true; }
                public void save() { throw new AssertionError("Must not save over config evidence"); }
            };
            holder = ConfigHolder.create("fixture", "fixture", ConfigScope.COMMON, store,
                    Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
            writeToml(TOML);
        }

        void writeToml(String text) throws Exception {
            Files.write(toml, text.getBytes(StandardCharsets.UTF_8));
        }

        JsonObject verify(String phase) throws Exception {
            return SakuraNetworkSmokeConfigs.verify(holder, directory, phase);
        }
    }
}
