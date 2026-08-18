package xin.vanilla.sakura.metadata;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.assertTrue;

/**
 * 校验展开后的 Fabric 元数据与 Banira 必需依赖。
 */
public class ModMetadataDependencyContractTest {
    @Test
    public void metadataRequiresBaniraWithoutSelfDependency() throws Exception {
        String metadata = new String(
                Files.readAllBytes(Paths.get("build/resources/main/fabric.mod.json")),
                StandardCharsets.UTF_8
        );
        assertTrue(metadata.contains("\"id\": \"sakura_sign_in\""));
        assertTrue(metadata.contains("\"banira_codex\": \">=1.0.3 <1.1.0\""));
    }
}
