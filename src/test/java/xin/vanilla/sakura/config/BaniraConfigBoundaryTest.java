package xin.vanilla.sakura.config;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 稳定配置模型不得向业务代码暴露 Forge 配置类型。
 */
public class BaniraConfigBoundaryTest {
    private static final Path MAIN = Paths.get("src/main/java");

    @Test
    public void configModelsUseOnlyBaniraConfigurationApi() throws Exception {
        String common = source("xin/vanilla/sakura/config/CommonConfig.java");
        String client = source("xin/vanilla/sakura/config/ClientConfig.java");
        assertTrue(common.contains("@Config("));
        assertTrue(client.contains("@Config("));
        assertFalse(common.contains("ForgeConfigSpec"));
        assertFalse(client.contains("ForgeConfigSpec"));
        assertFalse(Files.exists(MAIN.resolve("xin/vanilla/sakura/config/ServerConfig.java")));
    }

    @Test
    public void bootstrapRegistersBaniraConfigModelsOnly() throws Exception {
        String bootstrap = source("xin/vanilla/sakura/SakuraSignIn.java");
        assertTrue(bootstrap.contains("BaniraConfig.register(CommonConfig.class, MODID)"));
        assertTrue(bootstrap.contains("BaniraConfig.register(ClientConfig.class, MODID)"));
        assertFalse(bootstrap.contains("import net.minecraftforge.fml.ModLoadingContext;"));
        assertFalse(bootstrap.contains("registerConfig(ModConfig.Type"));
    }

    @Test
    public void loginSendsTheServerCommonConfigSnapshot() throws Exception {
        String common = source("xin/vanilla/sakura/config/CommonConfig.java");
        String network = source("xin/vanilla/sakura/network/SakuraNetwork.java");
        assertTrue(common.contains("ConfigSnapshotToClient"));
        assertTrue(common.contains("syncToPlayer(Object player)"));
        assertTrue(network.contains("BaniraModPresence.register"));
        assertTrue(network.contains("CommonConfig.syncToPlayer(player)"));
    }

    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(MAIN.resolve(relative)), StandardCharsets.UTF_8);
    }
}
