package xin.vanilla.sakura.config.reward;

import org.junit.Test;
import xin.vanilla.sakura.data.lottery.LotteryLimitPolicy;
import xin.vanilla.sakura.data.lottery.LotteryPool;
import xin.vanilla.sakura.data.lottery.LotteryPreviewMode;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LotteryRewardConfigCodecTest {
    @Test
    public void roundTripKeepsPoolMetadataAndRewards() throws Exception {
        RewardConfig source = new RewardConfig();
        source.getLotteryPools().add(new LotteryPool("daily", "Daily Draw",
                LotteryLimitPolicy.WEEKLY, 2, 30, LotteryPreviewMode.NONE,
                new RewardList(Collections.singletonList(Reward.getDefault()))));

        RewardConfig restored = new RewardConfigCodec().decode(
                new RewardConfigCodec().encode(source));

        assertEquals(source.getLotteryPools(), restored.getLotteryPools());
    }

    @Test
    public void rejectsLegacyLotteryPreviewFlag() throws Exception {
        RewardConfigCodec codec = new RewardConfigCodec();
        RewardConfig source = new RewardConfig();
        source.getLotteryPools().add(new LotteryPool("daily", "Daily Draw",
                LotteryLimitPolicy.DAILY, 1, 0, LotteryPreviewMode.ALL,
                new RewardList(Collections.singletonList(Reward.getDefault()))));
        String encoded = codec.encode(source);
        String visible = encoded.replace("\"previewMode\": \"ALL\"", "\"showRewards\": true");
        String hidden = encoded.replace("\"previewMode\": \"ALL\"", "\"showRewards\": false");

        for (String json : new String[]{visible, hidden}) {
            try {
                codec.decode(json);
                fail("Legacy pool accepted");
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("previewMode"));
            }
        }
    }

    @Test
    public void writesLotterySubformatWithoutChangingOverallSchema() throws Exception {
        com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(
                new RewardConfigCodec().encode(new RewardConfig())).getAsJsonObject();
        assertEquals(3, json.get("schemaVersion").getAsInt());
        assertEquals(1, json.get("lotteryFormat").getAsInt());
    }

    @Test
    public void oldOrdinaryDocumentWithoutPoolsStillReads() throws Exception {
        RewardConfigCodec codec = new RewardConfigCodec();
        com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(
                codec.encode(ordinaryConfig())).getAsJsonObject();
        json.remove("lotteryFormat");
        json.remove("lotteryPools");
        RewardConfig restored = codec.decode(json.toString());
        assertFalse(restored.getBaseRewards().isEmpty());
        assertFalse(restored.getPersonalDatePresets().isEmpty());
        assertTrue(restored.getLotteryPools().isEmpty());
    }

    private static RewardConfig ordinaryConfig() {
        RewardConfig config = new RewardConfig();
        config.getBaseRewards().add(Reward.getDefault());
        config.getMonthRewards().put("9", new RewardList(Collections.singletonList(Reward.getDefault())));
        config.addCdkReward(new xin.vanilla.banira.common.data.KeyValue<>(
                new xin.vanilla.banira.common.data.KeyValue<>("unchanged", ""),
                new xin.vanilla.banira.common.data.KeyValue<>(new RewardList(),
                        new java.util.concurrent.atomic.AtomicInteger(3))));
        config.getPersonalDatePresets().add(new xin.vanilla.sakura.data.personaldate.PersonalDatePreset(
                "birthday", "Birthday", xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence.YEARLY,
                java.util.Arrays.asList("minecraft:gregorian", "sakura_sign_in:chinese_lunar"), 1,
                xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode.SIGN_IN,
                0, 0, new RewardList()));
        return config;
    }

    @Test
    public void newLotteryMetadataDoesNotChangeOrdinaryGroupsOrDatePresets() throws Exception {
        RewardConfigCodec codec = new RewardConfigCodec();
        RewardConfig config = ordinaryConfig();
        com.google.gson.JsonObject before = new com.google.gson.JsonParser().parse(
                codec.encode(config)).getAsJsonObject();
        LotteryPool pool = new LotteryPool("daily", "Daily", LotteryLimitPolicy.DAILY,
                1, 0, new RewardList());
        pool.setDescription("Description\nSecond line");
        pool.setVoucherExpression("xpPoints(2)");
        config.getLotteryPools().add(pool);
        com.google.gson.JsonObject after = new com.google.gson.JsonParser().parse(
                codec.encode(codec.decode(codec.encode(config)))).getAsJsonObject();
        assertEquals(before.get("groups"), after.get("groups"));
        assertEquals(before.get("personalDatePresets"), after.get("personalDatePresets"));
        assertEquals(config.getLotteryPools(), codec.decode(after.toString()).getLotteryPools());
    }

    @Test
    public void rejectsUnversionedPoolsAndPreservesOriginalFile() throws Exception {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("lottery-format");
        java.nio.file.Path file = directory.resolve(RewardConfigRepository.REWARD_FILE_NAME);
        String json = "{\"schemaVersion\":3,\"groups\":[],\"lotteryPools\":[{"
                + "\"id\":\"daily\",\"displayName\":\"Daily\",\"limitPolicy\":\"DAILY\","
                + "\"previewMode\":\"ALL\",\"rewards\":[]}]}";
        byte[] original = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(file, original);
        try {
            new RewardConfigRepository(directory).loadOrCreate(new RewardConfig());
            fail("Unversioned pools accepted");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("lottery"));
            org.junit.Assert.assertArrayEquals(original, java.nio.file.Files.readAllBytes(file));
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    @Test
    public void rejectsUnknownLotterySubformat() throws Exception {
        try {
            new RewardConfigCodec().decode("{\"schemaVersion\":3,\"groups\":[],"
                    + "\"lotteryFormat\":2,\"lotteryPools\":[]}");
            fail("Unknown lottery format accepted");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("lottery"));
        }
    }
}
