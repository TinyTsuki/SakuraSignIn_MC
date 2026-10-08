package xin.vanilla.sakura.config;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ConfigurationAccessorContractTest {
    @Test
    public void declarationsDoNotExposeDetachedBeanAccessors() {
        verifyDeclaration(CommonConfig.class);
        verifyDeclaration(ClientConfig.class);
    }

    @Test
    public void sourcesContainNoDeclarationAccessors() throws Exception {
        for (String name : new String[]{"CommonConfig", "ClientConfig"}) {
            String source = new String(Files.readAllBytes(Paths.get(
                    "src/main/java/xin/vanilla/sakura/config/" + name + ".java")), StandardCharsets.UTF_8);
            assertFalse(name + " still generates detached bean accessors",
                    source.matches("(?s).*(@(?:lombok\\.)?(?:Getter|Setter|Accessors)\\b|import\\s+lombok\\.).*"));
        }
    }

    private static void verifyDeclaration(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertTrue("Configuration declaration exposes a detached accessor: " + method,
                    !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()));
        }
        for (Class<?> nested : type.getDeclaredClasses()) verifyDeclaration(nested);
    }
}
