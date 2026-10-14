package xin.vanilla.sakura.config.reward;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPathService;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.sakura.test.UnboundConfigPlatform;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class RewardConfigClientBackupTest {
    @Rule public final UnboundConfigPlatform platform = new UnboundConfigPlatform();
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final AtomicBoolean client = new AtomicBoolean();
    private final List<LogEvent> errors = new ArrayList<>();
    private BaniraPlatform previousPlatform;
    private RewardConfig previousServer, previousClient;
    private Logger logger;
    private AbstractAppender appender;
    private Path serverDirectory, clientDirectory;

    @Before public void setup() throws Exception {
        previousPlatform = BaniraPlatforms.get();
        Path root = temporary.newFolder("config").toPath();
        BaniraPathService paths = (BaniraPathService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPathService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("gameConfigPath")) return root;
                    throw new UnsupportedOperationException(method.toString());
                });
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pathService")) return paths;
                    return method.invoke(previousPlatform, args);
                }));
        RewardConfigManager.configureClientScope(client::get);
        previousServer = RewardConfigManager.getRewardConfig();
        serverDirectory = RewardConfigManager.getConfigDirectory();
        client.set(true);
        previousClient = RewardConfigManager.getRewardConfig();
        RewardConfigManager.setRewardConfig(new RewardConfig());
        clientDirectory = RewardConfigManager.getConfigDirectory();
        assertTrue(clientDirectory.startsWith(root));
        logger = (Logger) LogManager.getLogger(RewardConfigManager.class);
        appender = new AbstractAppender("client-backup-test", null, null, false) {
            @Override public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.ERROR)) errors.add(event.toImmutable());
            }
        };
        appender.start(); logger.addAppender(appender);
    }

    @After public void cleanup() {
        if (logger != null && appender != null) { logger.removeAppender(appender); appender.stop(); }
        client.set(true); RewardConfigManager.setRewardConfig(previousClient);
        client.set(false); RewardConfigManager.setRewardConfig(previousServer);
        RewardConfigManager.configureClientScope(() -> false);
        BaniraPlatforms.install(previousPlatform);
    }

    @Test public void firstClientSyncSkipsAbsentCacheAndCanSaveWithoutError() throws Exception {
        byte[] authority = "server-original".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(serverDirectory);
        Files.write(serverDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME), authority);
        long generation = RewardConfigManager.getLotteryGeneration();
        RewardConfigManager.backupRewardOption();
        assertTrue("First client backup logged an error", errors.isEmpty());
        assertFalse(Files.exists(clientDirectory.resolve("backups")));
        RewardConfigManager.saveRewardOption();
        assertTrue(errors.isEmpty());
        assertTrue(Files.isRegularFile(clientDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME)));
        assertEquals(generation, RewardConfigManager.getLotteryGeneration());
        assertArrayEquals(authority, Files.readAllBytes(serverDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME)));
    }

    @Test public void existingClientCacheIsBackedUpByteForByteBeforeReplacement() throws Exception {
        byte[] original = "{\"before\":\"sync\"}\r\n".getBytes(StandardCharsets.UTF_8);
        Path file = writeCache(original);
        RewardConfigManager.backupRewardOption();
        assertTrue(errors.isEmpty());
        Path backups = clientDirectory.resolve("backups/reward-config");
        List<Path> saved;
        try (Stream<Path> stream = Files.list(backups)) { saved = stream.collect(Collectors.toList()); }
        assertEquals(1, saved.size());
        assertArrayEquals(original, Files.readAllBytes(saved.get(0)));
        RewardConfigManager.saveRewardOption();
        assertTrue(errors.isEmpty());
        assertFalse(java.util.Arrays.equals(original, Files.readAllBytes(file)));
        assertArrayEquals(original, Files.readAllBytes(saved.get(0)));
    }

    @Test public void invalidCacheSourceStillLogsAnIoError() throws Exception {
        Path file = clientDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        Files.createDirectories(file);
        RewardConfigManager.backupRewardOption();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getThrown() instanceof java.io.IOException);
        assertTrue(Files.isDirectory(file));
    }

    @Test public void backupWriteFailureStillLogsAndPreservesSource() throws Exception {
        byte[] original = "preserved-cache".getBytes(StandardCharsets.UTF_8);
        Path file = writeCache(original);
        Files.write(clientDirectory.resolve("backups"), new byte[]{1});
        RewardConfigManager.backupRewardOption();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getThrown() instanceof java.io.IOException);
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test public void missingServerConfigurationStillLogsAnError() {
        client.set(false);
        RewardConfigManager.backupRewardOption(false);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getThrown() instanceof java.io.IOException);
    }

    @Test public void defaultBackupRemainsStrictInServerScope() {
        client.set(false);
        RewardConfigManager.backupRewardOption();
        assertEquals(1, errors.size());
    }

    private Path writeCache(byte[] content) throws Exception {
        Files.createDirectories(clientDirectory);
        Path file = clientDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        Files.write(file, content);
        return file;
    }
}
