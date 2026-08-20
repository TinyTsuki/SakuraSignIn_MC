package xin.vanilla.sakura.command;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 防止命令注册、业务执行与消息发送再次堆回单个入口类。
 */
public class CommandArchitectureContractTest {
    private static final Path MAIN = Paths.get("src/main/java/xin/vanilla/sakura");

    @Test
    public void commandEntryOnlyComposesFocusedBuilders() throws Exception {
        String entry = read(MAIN.resolve("command/SignInCommand.java"));
        long lines;
        try (Stream<String> sourceLines = Files.lines(MAIN.resolve("command/SignInCommand.java"))) {
            lines = sourceLines.count();
        }

        assertTrue("Command entry should remain small", lines <= 180);
        assertTrue(entry.contains("HelpCommand.build()"));
        assertTrue(entry.contains("SignActionCommand.buildSign()"));
        assertTrue(entry.contains("CdkCommand.build()"));
        assertTrue(entry.contains("CardCommand.build()"));
        assertTrue(entry.contains("ConfigCommand.build()"));
        assertFalse(entry.contains("RewardConfigManager"));
        assertFalse(entry.contains("SakuraUtils.sendMessage"));
    }

    @Test
    public void allServerMessagesUseTheNotificationBoundary() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith(Paths.get("message/SakuraMessages.java")))
                    .forEach(path -> {
                        String source = read(path);
                        assertFalse(path + " bypasses SakuraMessages",
                                source.contains("SakuraUtils.sendMessage")
                                        || source.contains("SakuraUtils.broadcastMessage"));
                    });
        }

        String messages = read(MAIN.resolve("message/SakuraMessages.java"));
        assertTrue(messages.contains("MessageUtils.sendNotification"));
        assertFalse("Banira owns client capability fallback",
                messages.contains("BaniraModPresence") || messages.contains("MessageUtils.sendMessage"));
        assertTrue(messages.contains("EnumNotificationStyle style"));
        assertTrue(messages.contains("public static void success("));
        assertTrue("Per-player language must not mutate a shared message",
                messages.contains("message.clone().languageCode"));
    }

    @Test
    public void configCommandUsesDescriptorAndFocusedPlayerBuilders() throws Exception {
        Path configCommand = MAIN.resolve("command/impl/ConfigCommand.java");
        String source = read(configCommand);
        long lines;
        try (Stream<String> sourceLines = Files.lines(configCommand)) {
            lines = sourceLines.count();
        }

        assertTrue("Config command entry should remain small", lines <= 80);
        assertTrue(source.contains("CommandUtils.configKeySuggestion"));
        assertTrue(source.contains("CommandUtils.executeModifyConfig"));
        assertTrue(source.contains("PersonalDateConfigCommand.build()"));
        assertTrue(Files.exists(MAIN.resolve("command/impl/PersonalDateConfigCommand.java")));
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
