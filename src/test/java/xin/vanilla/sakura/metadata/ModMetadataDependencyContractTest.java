package xin.vanilla.sakura.metadata;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 校验展开后的 Forge 元数据，避免再次声明对自身的依赖。
 */
public class ModMetadataDependencyContractTest {
    private static final Pattern DEPENDENCY_BLOCK = Pattern.compile(
            "\\[\\[dependencies\\.\"([^\"]+)\"\\]\\](.*?)(?=\\[\\[dependencies\\.|\\z)",
            Pattern.DOTALL
    );
    private static final Pattern MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"([^\"]+)\"");

    @Test
    public void metadataRequiresBaniraWithoutSelfDependency() throws Exception {
        String metadata = new String(
                Files.readAllBytes(Paths.get("build/sourcesSets/main/META-INF/mods.toml")),
                StandardCharsets.UTF_8
        );

        boolean baniraRequired = false;
        Matcher blocks = DEPENDENCY_BLOCK.matcher(metadata);
        while (blocks.find()) {
            String owner = blocks.group(1);
            Matcher modId = MOD_ID.matcher(blocks.group(2));
            if (!modId.find()) {
                continue;
            }
            String dependency = modId.group(1);
            assertFalse("Sakura must not depend on itself", owner.equals(dependency));
            if ("sakura_sign_in".equals(owner) && "banira_codex".equals(dependency)) {
                baniraRequired = blocks.group(2).contains("mandatory = true");
            }
        }

        assertTrue("Banira must be a required Sakura dependency", baniraRequired);
    }
}
