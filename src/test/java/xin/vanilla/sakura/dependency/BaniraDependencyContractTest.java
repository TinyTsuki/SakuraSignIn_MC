package xin.vanilla.sakura.dependency;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定开发期 Banira 依赖与同版本本地发布刷新契约。
 */
public class BaniraDependencyContractTest {

    @Test
    public void buildUsesCurrentFabricBaniraPublication() throws Exception {
        String properties = read("gradle.properties");
        String build = read("build.gradle");
        String fingerprint = read("gradle/banira-local-fingerprint.gradle");

        assertTrue(properties.contains("loader_type=fabric"));
        assertTrue(properties.contains("banira_version="));
        assertTrue(build.contains("mavenLocal()"));
        assertTrue(build.contains("xin.vanilla.banira:banira_codex:${loader_type}-${minecraft_version}-${banira_version}"));
        assertTrue(build.contains("changing = true"));
        assertTrue(build.contains("cacheChangingModulesFor 0, 'seconds'"));
        assertTrue(build.contains("apply from: 'gradle/banira-local-fingerprint.gradle'"));
        assertTrue(build.contains("tasks.register('jarAll')"));
        assertFalse(build.contains("afterEvaluate"));

        assertTrue(fingerprint.contains("local-build.json"));
        assertTrue(fingerprint.contains("findByName(\"MavenLocal\")"));
        assertFalse(fingerprint.contains("System.getProperty(\"user.home\")"));
        assertFalse(fingerprint.contains("worktree"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
