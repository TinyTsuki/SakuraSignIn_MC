package xin.vanilla.sakura.reward.script;

import org.junit.Assume;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class ExampleScriptFiles {
    private ExampleScriptFiles() {
    }

    static Path resolve(String name) {
        String directory = System.getenv("SAKURA_REWARD_EXAMPLES");
        Assume.assumeTrue("Set SAKURA_REWARD_EXAMPLES to the docs branch's examples/reward-scripts directory",
                directory != null && !directory.isEmpty());
        Path path = Paths.get(directory).resolve(name);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Missing docs example: " + path);
        return path;
    }
}
