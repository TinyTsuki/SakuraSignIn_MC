package xin.vanilla.sakura.config.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.sakura.config.reward.RewardConfig;
import xin.vanilla.sakura.enums.ERewardRule;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.network.data.RewardOptionSyncData;
import xin.vanilla.sakura.network.packet.RewardOptionSyncPacket;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;
import xin.vanilla.sakura.config.reward.RewardConfigManager;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RewardConfigMigrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void versionedCodecPreservesPersonalDatePresetMetadata() throws Exception {
        RewardConfig source = new RewardConfig();
        PersonalDatePreset preset = new PersonalDatePreset(
                "server_day", "Server Day", PersonalDateRecurrence.YEARLY,
                java.util.Arrays.asList(CalendarIds.GREGORIAN, CalendarIds.CHINESE_LUNAR), 2,
                PersonalDateDeliveryMode.ONLINE, 3, 7,
                new RewardList(Collections.singletonList(messageReward("personal")))
        );
        source.setPersonalDatePresets(Collections.singletonList(preset));

        RewardConfig restored = new RewardConfigCodec().decode(new RewardConfigCodec().encode(source));

        assertEquals(source.getPersonalDatePresets(), restored.getPersonalDatePresets());
    }

    @Test(expected = IOException.class)
    public void versionedCodecRejectsDuplicatePersonalDatePresetIds() throws Exception {
        RewardConfig source = new RewardConfig();
        PersonalDatePreset preset = new PersonalDatePreset(
                "server_day", "Server Day", PersonalDateRecurrence.MONTHLY,
                Collections.singletonList(CalendarIds.GREGORIAN), 1,
                PersonalDateDeliveryMode.SIGN_IN, 0, 0, new RewardList()
        );
        source.setPersonalDatePresets(Arrays.asList(preset, preset));

        new RewardConfigCodec().encode(source);
    }

    @Test
    public void legacyReaderPreservesDuplicateProbabilityGroups() throws Exception {
        RewardConfigDocument document = new LegacyRewardConfigReader().read(loadFixture());

        List<RewardGroup> groups = document.getGroups().stream()
                .filter(group -> group.getRule() == ERewardRule.RANDOM_REWARD)
                .collect(Collectors.toList());

        assertEquals(2, groups.size());
        assertEquals("0.5", groups.get(0).getKey());
        assertEquals("first", groups.get(0).getRewards().get(0).getContent().get("text").getAsString());
        assertEquals("0.5", groups.get(1).getKey());
        assertEquals("second", groups.get(1).getRewards().get(0).getContent().get("text").getAsString());
        assertEquals(SakuraRewardTypes.MESSAGE,
                groups.get(0).getRewards().get(0).getTypeId());
    }

    @Test
    public void legacyEnumNamesBecomeNamespacedTypeIds() throws Exception {
        RewardConfigDocument document = new LegacyRewardConfigReader().read("{"
                + "\"baseRewards\":[{\"type\":\"ITEM\",\"probability\":1,"
                + "\"content\":{\"item\":\"minecraft:apple\",\"count\":1}}],"
                + "\"continuousRewards\":{},\"cycleRewards\":{},\"yearRewards\":{},"
                + "\"monthRewards\":{},\"weekRewards\":{},\"dateTimeRewards\":{},"
                + "\"cumulativeRewards\":{},\"randomRewards\":{},\"cdkRewards\":[]}" );

        Reward reward = document.getGroups().get(0).getRewards().get(0);
        assertEquals(SakuraRewardTypes.ITEM, reward.getTypeId());
        String encoded = new RewardConfigCodec().encodeDocument(document);
        assertTrue(encoded.contains("\"type\": \"sakura_sign_in:item\""));
    }

    @Test
    public void versionedCodecKeepsRuleAndDuplicateGroupOrder() throws Exception {
        RewardConfigDocument source = new RewardConfigDocument(Arrays.asList(
                group(ERewardRule.CONTINUOUS_REWARD, "8", "late"),
                group(ERewardRule.CONTINUOUS_REWARD, "1", "early"),
                group(ERewardRule.RANDOM_REWARD, "0.5", "first"),
                group(ERewardRule.RANDOM_REWARD, "0.5", "second")
        ));
        RewardConfigCodec codec = new RewardConfigCodec();

        RewardConfig runtime = codec.toRuntimeConfig(source);
        RewardConfig decoded = codec.decode(codec.encode(runtime));
        RewardConfigDocument result = codec.toDocument(decoded);

        assertEquals(Arrays.asList("8", "1"),
                keys(result, ERewardRule.CONTINUOUS_REWARD));
        assertEquals(Arrays.asList("0.5", "0.5"),
                keys(result, ERewardRule.RANDOM_REWARD));
        assertEquals("first", randomText(result, 0));
        assertEquals("second", randomText(result, 1));
    }

    @Test
    public void repositoryMigratesOnceAndKeepsAnExactBackup() throws Exception {
        Path configDirectory = temporaryFolder.newFolder("config").toPath();
        Path rewardFile = configDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        byte[] legacy = loadFixture().getBytes(StandardCharsets.UTF_8);
        Files.write(rewardFile, legacy);
        RewardConfigRepository repository = new RewardConfigRepository(configDirectory);

        RewardConfig firstLoad = repository.loadOrCreate(new RewardConfig());

        assertEquals(2, firstLoad.getRandomRewardGroups().size());
        JsonObject migrated = new JsonParser()
                .parse(new String(Files.readAllBytes(rewardFile), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(RewardConfigDocument.CURRENT_SCHEMA_VERSION,
                migrated.get("schemaVersion").getAsInt());
        List<Path> backupsAfterMigration = backupFiles(configDirectory);
        assertEquals(1, backupsAfterMigration.size());
        assertArrayEquals(legacy, Files.readAllBytes(backupsAfterMigration.get(0)));

        repository.loadOrCreate(new RewardConfig());

        assertEquals(backupsAfterMigration, backupFiles(configDirectory));
    }

    @Test
    public void repositoryMigratesVersionedEnumTypesToNamespacedIds() throws Exception {
        Path configDirectory = temporaryFolder.newFolder("versioned-enum").toPath();
        Path rewardFile = configDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        byte[] legacy = ("{\"schemaVersion\":2,\"groups\":[{"
                + "\"rule\":\"BASE_REWARD\",\"key\":\"base\",\"rewards\":[{"
                + "\"type\":\"ITEM\",\"probability\":1,"
                + "\"content\":{\"item\":\"minecraft:apple\",\"count\":1}}]}]}")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(rewardFile, legacy);

        RewardConfig migrated = new RewardConfigRepository(configDirectory)
                .loadOrCreate(new RewardConfig());

        assertEquals(SakuraRewardTypes.ITEM,
                migrated.getBaseRewards().get(0).getTypeId());
        JsonObject rewritten = new JsonParser().parse(new String(
                Files.readAllBytes(rewardFile), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(RewardConfigDocument.CURRENT_SCHEMA_VERSION,
                rewritten.get("schemaVersion").getAsInt());
        assertEquals("sakura_sign_in:item", rewritten.getAsJsonArray("groups")
                .get(0).getAsJsonObject().getAsJsonArray("rewards")
                .get(0).getAsJsonObject().get("type").getAsString());
        List<Path> backups = backupFiles(configDirectory);
        assertEquals(1, backups.size());
        assertArrayEquals(legacy, Files.readAllBytes(backups.get(0)));
    }

    @Test
    public void malformedLegacyFileIsNeverOverwritten() throws Exception {
        Path configDirectory = temporaryFolder.newFolder("malformed").toPath();
        Path rewardFile = configDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        byte[] malformed = "{\"randomRewards\": [".getBytes(StandardCharsets.UTF_8);
        Files.write(rewardFile, malformed);
        RewardConfigRepository repository = new RewardConfigRepository(configDirectory);

        try {
            repository.loadOrCreate(new RewardConfig());
            fail("Malformed legacy JSON must fail migration");
        } catch (IOException expected) {
            assertArrayEquals(malformed, Files.readAllBytes(rewardFile));
            assertTrue(backupFiles(configDirectory).isEmpty());
        }
    }

    @Test
    public void migrationDoesNotDependOnLegacyTopLevelPropertyOrder() throws Exception {
        Path configDirectory = temporaryFolder.newFolder("reordered").toPath();
        Path rewardFile = configDirectory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        String reordered = "{"
                + "\"randomRewards\":{\"0.5\":[]},"
                + "\"baseRewards\":[],"
                + "\"continuousRewards\":{},"
                + "\"cycleRewards\":{},"
                + "\"yearRewards\":{},"
                + "\"monthRewards\":{},"
                + "\"weekRewards\":{},"
                + "\"dateTimeRewards\":{},"
                + "\"cumulativeRewards\":{},"
                + "\"cdkRewards\":[]"
                + "}";
        Files.write(rewardFile, reordered.getBytes(StandardCharsets.UTF_8));

        RewardConfig migrated = new RewardConfigRepository(configDirectory)
                .loadOrCreate(new RewardConfig());

        assertEquals(1, migrated.getRandomRewardGroups().size());
        assertEquals("0.5", migrated.getRandomRewardGroups().get(0).getKey());
    }

    @Test
    public void legacyReaderNormalizesNumericKeysWithoutMergingRandomGroups() throws Exception {
        String legacy = "{"
                + "\"baseRewards\":[],"
                + "\"continuousRewards\":{\"01\":[]},"
                + "\"cycleRewards\":{},"
                + "\"yearRewards\":{\"-01\":[]},"
                + "\"monthRewards\":{},"
                + "\"weekRewards\":{},"
                + "\"dateTimeRewards\":{},"
                + "\"cumulativeRewards\":{\"0002\":[]},"
                + "\"randomRewards\":{\"0.500\":[],\"0.50\":[]},"
                + "\"cdkRewards\":[]"
                + "}";

        RewardConfigDocument document = new LegacyRewardConfigReader().read(legacy);

        assertEquals(Arrays.asList("1"), keys(document, ERewardRule.CONTINUOUS_REWARD));
        assertEquals(Arrays.asList("-1"), keys(document, ERewardRule.YEAR_REWARD));
        assertEquals(Arrays.asList("2"), keys(document, ERewardRule.CUMULATIVE_REWARD));
        assertEquals(Arrays.asList("0.5", "0.5"), keys(document, ERewardRule.RANDOM_REWARD));
    }

    @Test
    public void migrationMergesNumericKeysThatWereEquivalentInTheLegacyModel() throws Exception {
        String legacy = "{"
                + "\"baseRewards\":[],"
                + "\"continuousRewards\":{"
                + "\"01\":[{\"type\":\"MESSAGE\",\"probability\":1,\"content\":{\"text\":\"first\"}}],"
                + "\"1\":[{\"type\":\"MESSAGE\",\"probability\":1,\"content\":{\"text\":\"second\"}}]"
                + "},"
                + "\"cycleRewards\":{},"
                + "\"yearRewards\":{},"
                + "\"monthRewards\":{},"
                + "\"weekRewards\":{},"
                + "\"dateTimeRewards\":{},"
                + "\"cumulativeRewards\":{},"
                + "\"randomRewards\":{},"
                + "\"cdkRewards\":[]"
                + "}";
        RewardConfigDocument document = new LegacyRewardConfigReader().read(legacy);

        RewardConfig migrated = new RewardConfigCodec().toRuntimeConfig(document);

        assertEquals(1, migrated.getContinuousRewards().size());
        assertEquals(2, migrated.getContinuousRewards().get("1").size());
        assertEquals("first", migrated.getContinuousRewards().get("1")
                .get(0).getContent().get("text").getAsString());
        assertEquals("second", migrated.getContinuousRewards().get("1")
                .get(1).getContent().get("text").getAsString());
    }

    @Test
    public void rewardSyncPreservesEmptyDuplicateRandomGroups() {
        RewardConfig source = new RewardConfig();
        source.addRandomRewardGroup("0.5", new RewardList());
        source.addRandomRewardGroup("0.5",
                new RewardList(Arrays.asList(messageReward("filled"))));
        List<RewardOptionSyncData> data =
                RewardConfigManager.toSyncData(source, ERewardRule.RANDOM_REWARD);

        RewardConfig decoded = RewardConfigManager.fromSyncPacketList(
                Arrays.asList(new RewardOptionSyncPacket(data)));

        assertEquals(2, decoded.getRandomRewardGroups().size());
        assertTrue(decoded.getRandomRewardGroups().get(0).getRewards().isEmpty());
        assertEquals("filled", decoded.getRandomRewardGroups().get(1)
                .getRewards().get(0).getContent().get("text").getAsString());
    }

    private static RewardGroup group(ERewardRule rule, String key, String text) {
        return new RewardGroup(rule, key,
                new RewardList(Arrays.asList(messageReward(text))));
    }

    private static Reward messageReward(String text) {
        JsonObject content = new JsonObject();
        content.addProperty("text", text);
        return new Reward(content, SakuraRewardTypes.MESSAGE, BigDecimal.ONE);
    }

    private static List<String> keys(RewardConfigDocument document, ERewardRule rule) {
        return document.getGroups().stream()
                .filter(group -> group.getRule() == rule)
                .map(RewardGroup::getKey)
                .collect(Collectors.toList());
    }

    private static String randomText(RewardConfigDocument document, int index) {
        return document.getGroups().stream()
                .filter(group -> group.getRule() == ERewardRule.RANDOM_REWARD)
                .collect(Collectors.toList())
                .get(index)
                .getRewards()
                .get(0)
                .getContent()
                .get("text")
                .getAsString();
    }

    private static List<Path> backupFiles(Path configDirectory) throws IOException {
        Path backupDirectory = configDirectory.resolve("backups").resolve("reward-config");
        if (!Files.isDirectory(backupDirectory)) {
            return java.util.Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(backupDirectory)) {
            return stream.filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static String loadFixture() throws IOException {
        try (InputStream input = RewardConfigMigrationTest.class.getResourceAsStream(
                "/fixtures/legacy-reward-option-data.json")) {
            if (input == null) {
                throw new IOException("Missing legacy reward fixture");
            }
            byte[] bytes = new byte[8192];
            int length;
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            while ((length = input.read(bytes)) >= 0) {
                output.write(bytes, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
